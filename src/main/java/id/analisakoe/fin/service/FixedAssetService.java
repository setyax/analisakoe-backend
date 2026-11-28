package id.analisakoe.fin.service;

import id.analisakoe.context.ContextService;
import org.springframework.beans.factory.annotation.Autowired;

import id.analisakoe.common.BusinessException;
import id.analisakoe.fin.entity.AssetMaster;
import id.analisakoe.fin.repository.AssetMasterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
public class FixedAssetService {

    @Autowired
    private ContextService context;
    private final AssetMasterRepository assetRepo;
    private final JournalService journalService;

    public FixedAssetService(AssetMasterRepository assetRepo, JournalService journalService) {
        this.assetRepo = assetRepo; this.journalService = journalService;
    }

    // 1. KAPITALISASI ASET (Dipanggil saat PI di-Posting)
    @Transactional
    public void capitalizeAsset(String name, String category, BigDecimal cost, int usefulLifeYears, LocalDate acqDate) {
        AssetMaster asset = new AssetMaster();
        asset.setTenantId(context.tenantId()); asset.setCompanyId(context.companyId());
        asset.setAssetCode("AST-" + System.currentTimeMillis());
        asset.setAssetName(name); asset.setCategory(category);
        asset.setAcquisitionDate(acqDate); asset.setAcquisitionCost(cost);
        asset.setUsefulLifeYears(usefulLifeYears);
        asset.setSalvageValue(BigDecimal.ZERO); // Asumsi tanpa nilai residu
        asset.setDepreciationMethod("STRAIGHT_LINE");
        asset.setAccumulatedDepreciation(BigDecimal.ZERO);
        asset.setBookValue(cost);
        asset.setStatus("ACTIVE");
        assetRepo.save(asset);

        // Jurnal Kapitalisasi (Debit Aset, Kredit Hutang/Clearing)
        // (Di sistem nyata, ini menggantikan jurnal pembelian biasa)
        journalService.createJournal(asset.getTenantId(), asset.getCompanyId(), acqDate, "ASSET_CAP", "Kapitalisasi Aset: " + name, List.of(
            new JournalService.JournalLine("1404", cost, BigDecimal.ZERO, "Peralatan & Mesin"), // Sesuaikan dengan tipe aset
            new JournalService.JournalLine("2101", BigDecimal.ZERO, cost, "Hutang Usaha")
        ));
    }

    // 2. DEPRESIASI BULANAN (Garis Lurus)
    @Transactional
    public void runDepreciation(LocalDate periodDate) {
        List<AssetMaster> assets = assetRepo.findAll();
        String periodStr = String.format("%04d-%02d", periodDate.getYear(), periodDate.getMonthValue());

        for (AssetMaster asset : assets) {
            if (!"ACTIVE".equals(asset.getStatus())) continue;

            // Rumus Garis Lurus: (Harga Perolehan - Nilai Residu) / Umur Ekonomis (Tahun) / 12 Bulan
            BigDecimal cost = asset.getAcquisitionCost().subtract(asset.getSalvageValue());
            BigDecimal annualDepr = cost.divide(new BigDecimal(asset.getUsefulLifeYears()), 2, RoundingMode.HALF_UP);
            BigDecimal monthlyDepr = annualDepr.divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP);

            // Jangan depresiasi jika sudah mencapai nilai residu
            if (asset.getBookValue().subtract(monthlyDepr).compareTo(asset.getSalvageValue()) < 0) {
                monthlyDepr = asset.getBookValue().subtract(asset.getSalvageValue());
            }

            if (monthlyDepr.compareTo(BigDecimal.ZERO) <= 0) continue;

            // Update Nilai Buku
            asset.setAccumulatedDepreciation(asset.getAccumulatedDepreciation().add(monthlyDepr));
            asset.setBookValue(asset.getBookValue().subtract(monthlyDepr));
            assetRepo.save(asset);

            // Jurnal: Debit Beban Depresiasi (6199), Kredit Akumulasi Depresiasi (1491)
            journalService.createJournal(asset.getTenantId(), asset.getCompanyId(), periodDate, "DEPR", "Depresiasi " + periodName(periodStr) + ": " + asset.getAssetName(), List.of(
                new JournalService.JournalLine("6199", monthlyDepr, BigDecimal.ZERO, "Beban Depresiasi"),
                new JournalService.JournalLine("1491", BigDecimal.ZERO, monthlyDepr, "Akumulasi Depresiasi")
            ));
        }
    }

    private String periodName(String period) { return period; }
}