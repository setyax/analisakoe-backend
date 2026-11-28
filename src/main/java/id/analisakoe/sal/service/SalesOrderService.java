package id.analisakoe.sal.service;

import id.analisakoe.context.ContextService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Autowired;
import id.analisakoe.core.service.NumberRangeService;

import id.analisakoe.common.BusinessException;
import id.analisakoe.inv.entity.InvStockBalance;
import id.analisakoe.inv.entity.InvTransactionDetail;
import id.analisakoe.inv.entity.InvTransactionHeader;
import id.analisakoe.inv.repository.InvStockBalanceRepository;
import id.analisakoe.inv.repository.InvTransactionRepository;
import id.analisakoe.sal.entity.SalesOrderDtl;
import id.analisakoe.sal.entity.SalesOrderHdr;
import id.analisakoe.sal.repository.SalesOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
public class SalesOrderService {

    @Autowired
    private ContextService context;

    @Autowired
    private NumberRangeService numberRangeService;
    private final SalesOrderRepository repo;
    private final InvStockBalanceRepository balanceRepo;
    private final InvTransactionRepository trxRepo;

    public SalesOrderService(SalesOrderRepository repo, InvStockBalanceRepository balanceRepo, InvTransactionRepository trxRepo)  {
        this.repo = repo;
        this.balanceRepo = balanceRepo;
        this.trxRepo = trxRepo;
    }

    @Transactional
    public void create(LocalDate date, Long customerId, String remarks, String currency, String taxType, List<LineRequest> lines, Long salespersonId) {
        if(lines == null || lines.isEmpty()) throw new BusinessException("Minimal 1 item");

        SalesOrderHdr hdr = new SalesOrderHdr();
        hdr.setTenantId(context.tenantId()); hdr.setCompanyId(context.companyId());
        hdr.setDocNo(numberRangeService.generate("SO", "SO"));
        hdr.setDocDate(date); hdr.setCustomerId(customerId);
        hdr.setRemarks(remarks); hdr.setStatus("DRAFT"); 
        hdr.setCurrency(currency != null ? currency : "IDR");
        hdr.setTaxType(taxType != null ? taxType : "NO_TAX");
        hdr.setSalespersonId(salespersonId);

        processLines(hdr, lines, taxType);
        repo.save(hdr);
    }

    @Transactional
    public void update(Long id, LocalDate date, Long customerId, String remarks, String currency, String taxType, List<LineRequest> lines, Long salespersonId) {
        SalesOrderHdr hdr = repo.findByIdAndTenantIdAndCompanyId(id, context.tenantId(), context.companyId())
            .orElseThrow(() -> new BusinessException("Sales Order tidak ditemukan"));
        
        if (!"DRAFT".equals(hdr.getStatus())) {
            throw new BusinessException("Hanya SO berstatus DRAFT yang bisa diedit.");
        }

        hdr.setDocDate(date); hdr.setCustomerId(customerId); hdr.setRemarks(remarks); 
        hdr.setCurrency(currency != null ? currency : "IDR");
        hdr.setTaxType(taxType != null ? taxType : "NO_TAX");
        hdr.setSalespersonId(salespersonId);
        
        hdr.getDetails().clear();
        processLines(hdr, lines, taxType);
        repo.save(hdr);
    }

    private void processLines(SalesOrderHdr hdr, List<LineRequest> lines, String taxType) {
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal grandTotal = BigDecimal.ZERO;

        for(var l : lines) {
            SalesOrderDtl d = new SalesOrderDtl();
            d.setItemId(l.itemId()); d.setUomId(l.uomId());
            d.setQty(l.qty()); 
            BigDecimal price = l.price() == null ? BigDecimal.ZERO : l.price();
            d.setPrice(price);
            
            BigDecimal subtotal = l.qty().multiply(price);
            BigDecimal dpp = BigDecimal.ZERO;
            BigDecimal tax = BigDecimal.ZERO;
            BigDecimal lineTotal = subtotal;

            if ("EXCLUDE".equals(taxType)) {
                dpp = subtotal;
                tax = dpp.multiply(new BigDecimal("0.11")).setScale(2, RoundingMode.HALF_UP);
                lineTotal = dpp.add(tax);
            } else if ("INCLUDE".equals(taxType)) {
                lineTotal = subtotal;
                dpp = lineTotal.divide(new BigDecimal("1.11"), 2, RoundingMode.HALF_UP);
                tax = lineTotal.subtract(dpp);
            } else { // NO_TAX
                dpp = subtotal;
            }

            d.setSubtotal(subtotal);
            d.setDpp(dpp);
            d.setTaxAmount(tax);
            d.setLineTotal(lineTotal);
            hdr.addDetail(d);

            totalTax = totalTax.add(tax);
            grandTotal = grandTotal.add(lineTotal);
        }
        
        hdr.setTaxAmount(totalTax);
        hdr.setTotalAmount(grandTotal);
    }

    @Transactional
    public void confirmOrder(Long orderId) {
        SalesOrderHdr hdr = repo.findByIdAndTenantIdAndCompanyId(orderId, context.tenantId(), context.companyId())
            .orElseThrow(() -> new BusinessException("Sales Order tidak ditemukan"));

        if (!"DRAFT".equals(hdr.getStatus())) {
            throw new BusinessException("Hanya Sales Order berstatus DRAFT yang bisa dikonfirmasi.");
        }

        // BEST PRACTICE SAP: Konfirmasi SO tidak memotong stok.
        // Stok akan dipotong saat Post Goods Issue (PGI) di Delivery Order.
        hdr.setStatus("CONFIRMED");
        repo.save(hdr);
    }

    public record LineRequest(Long itemId, Long uomId, BigDecimal qty, BigDecimal price) {}
    @Transactional
    public void unconfirmOrder(Long orderId) {
        SalesOrderHdr hdr = repo.findByIdAndTenantIdAndCompanyId(orderId, context.tenantId(), context.companyId()).orElseThrow(() -> new BusinessException("SO tidak ditemukan"));
        if (!"CONFIRMED".equals(hdr.getStatus())) {
            throw new BusinessException("Hanya SO berstatus CONFIRMED yang bisa dibatalkan konfirmasinya.");
        }
        hdr.setStatus("DRAFT");
        repo.save(hdr);
    }

    @Transactional
    public void voidOrder(Long orderId) {
        SalesOrderHdr hdr = repo.findByIdAndTenantIdAndCompanyId(orderId, context.tenantId(), context.companyId()).orElseThrow(() -> new BusinessException("SO tidak ditemukan"));
        if ("DELIVERED".equals(hdr.getStatus()) || "INVOICED".equals(hdr.getStatus())) {
            throw new BusinessException("SO sudah diproses ke pengiriman/tagihan. Tidak bisa di-Void.");
        }
        hdr.setStatus("VOID");
        repo.save(hdr);
    }
}
