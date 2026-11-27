package id.analisakoe.pur.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Determinasi harga pembelian ala SAP condition technique.
 *
 * Urutan sumber harga:
 *   CONTRACT -> INFO_RECORD (price list) -> RFQ -> LAST_PO -> MANUAL
 *
 * Condition turunan (DISCOUNT / SURCHARGE / TAX) diambil dari tabel yang sama
 *dan dihitung terpisah setelah harga dasar, sesuai calculation_order.
 */
@Service
public class PriceDeterminationService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final EntityManager em;

    public PriceDeterminationService(EntityManager em) {
        this.em = em;
    }

    public record AppliedCondition(String source, String conditionClass, String conditionType,
                                   BigDecimal rate, BigDecimal amount, int calculationOrder) {}

    public record Determination(
            boolean found,
            BigDecimal basePrice,
            BigDecimal price,
            BigDecimal priceUnit,
            String priceSource,
            String conditionType,
            String currency,
            BigDecimal minQty,
            BigDecimal taxRate,
            List<AppliedCondition> conditions) {}

    public record LineResult(BigDecimal price, BigDecimal dpp, BigDecimal taxAmount,
                             BigDecimal lineTotal) {}

    /** Urutan prioritas sumber harga (kecil = lebih dulu). */
    private static int sourceRank(String source) {
        if (source == null) return 90;
        return switch (source.toUpperCase()) {
            case "CONTRACT" -> 10;
            case "INFO_RECORD" -> 20;
            case "RFQ" -> 30;
            case "LAST_PO" -> 40;
            default -> 90;
        };
    }

    @Transactional(readOnly = true)
    public Determination determine(Long tenantId, Long companyId, Long supplierId,
                                   Long itemId, Long uomId, LocalDate date,
                                   BigDecimal qty, BigDecimal manualPrice) {
        BigDecimal safeQty = qty == null || qty.compareTo(BigDecimal.ZERO) <= 0
                ? BigDecimal.ONE : qty;

        Object[] base = findContractOrInfo(tenantId, companyId, supplierId, itemId, uomId, date, safeQty);
        String source = base[0] != null ? base[0].toString() : null;
        BigDecimal rawPrice = base[1] != null ? new BigDecimal(base[1].toString()) : BigDecimal.ZERO;
        BigDecimal priceUnit = base[2] != null ? new BigDecimal(base[2].toString()) : BigDecimal.ONE;
        String conditionType = base[3] != null ? base[3].toString() : "PB00";
        String currency = base[4] != null ? base[4].toString() : "IDR";
        BigDecimal minQty = base[5] != null ? new BigDecimal(base[5].toString()) : BigDecimal.ONE;

        if (source == null) {
            Object[] rfq = findRfq(tenantId, companyId, supplierId, itemId, uomId, date);
            if (rfq != null) {
                source = "RFQ";
                rawPrice = new BigDecimal(rfq[0].toString());
                priceUnit = BigDecimal.ONE;
                conditionType = "RFQ";
                currency = rfq[1] != null ? rfq[1].toString() : currency;
                minQty = safeQty;
            }
        }

        if (source == null) {
            Object[] lastPo = findLastPo(tenantId, companyId, supplierId, itemId, uomId, date);
            if (lastPo != null) {
                source = "LAST_PO";
                rawPrice = new BigDecimal(lastPo[0].toString());
                priceUnit = lastPo[1] != null && new BigDecimal(lastPo[1].toString()).compareTo(BigDecimal.ZERO) > 0
                        ? new BigDecimal(lastPo[1].toString()) : BigDecimal.ONE;
                conditionType = "PB00";
                currency = lastPo[2] != null ? lastPo[2].toString() : currency;
                minQty = safeQty;
            }
        }

        if (source == null && manualPrice != null && manualPrice.compareTo(BigDecimal.ZERO) > 0) {
            source = "MANUAL";
            rawPrice = manualPrice;
            priceUnit = BigDecimal.ONE;
            conditionType = "MANUAL";
        }

        if (priceUnit.compareTo(BigDecimal.ZERO) <= 0) priceUnit = BigDecimal.ONE;
        BigDecimal basePrice = rawPrice.divide(priceUnit, 4, RoundingMode.HALF_UP);

        List<AppliedCondition> rawConditions = findExtras(tenantId, companyId, supplierId, itemId, uomId, date, safeQty);
        List<AppliedCondition> sorted = new ArrayList<>(rawConditions);
        sorted.sort(Comparator.comparingInt((AppliedCondition c) -> c.calculationOrder())
                .thenComparingInt(c -> sourceRank(c.source())));

        BigDecimal net = basePrice;
        BigDecimal taxRate = BigDecimal.ZERO;
        boolean hasTax = false;
        List<AppliedCondition> applied = new ArrayList<>();

        if (source != null) {
            applied.add(new AppliedCondition(source, "PRICE", conditionType, null, basePrice, 10));
        }

        for (AppliedCondition c : sorted) {
            String cls = c.conditionClass();
            BigDecimal amount;
            if ("TAX".equals(cls)) {
                BigDecimal rate = c.rate() == null ? BigDecimal.ZERO : c.rate();
                amount = net.multiply(rate).divide(HUNDRED, 4, RoundingMode.HALF_UP);
                taxRate = taxRate.add(rate);
                hasTax = true;
            } else if ("DISCOUNT".equals(cls)) {
                amount = c.rate() != null
                        ? net.multiply(c.rate()).divide(HUNDRED, 4, RoundingMode.HALF_UP)
                        : c.amount();
                amount = amount.min(net).max(BigDecimal.ZERO);
                net = net.subtract(amount);
            } else if ("SURCHARGE".equals(cls)) {
                amount = c.rate() != null
                        ? net.multiply(c.rate()).divide(HUNDRED, 4, RoundingMode.HALF_UP)
                        : c.amount();
                net = net.add(amount);
            } else {
                continue;
            }
            applied.add(new AppliedCondition(c.source(), cls, c.conditionType(), c.rate(), amount, c.calculationOrder()));
        }

        return new Determination(
                source != null && basePrice.compareTo(BigDecimal.ZERO) > 0,
                basePrice,
                net,
                priceUnit,
                source == null ? "MANUAL" : source,
                conditionType,
                currency,
                minQty,
                hasTax ? taxRate : null,
                applied);
    }

    /** Hitung dpp/pajak per baris. Jika tidak ada condition TAX, pakai taxType lama. */
    public LineResult calculate(Determination d, BigDecimal qty, String taxType) {
        BigDecimal safeQty = qty == null ? BigDecimal.ZERO : qty;
        BigDecimal dpp = d.price().multiply(safeQty).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tax = BigDecimal.ZERO;

        if (d.taxRate() != null) {
            tax = dpp.multiply(d.taxRate()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        } else if ("EXCLUDE".equals(taxType)) {
            tax = dpp.multiply(new BigDecimal("0.11")).setScale(2, RoundingMode.HALF_UP);
        } else if ("INCLUDE".equals(taxType)) {
            tax = dpp.multiply(new BigDecimal("11"))
                    .divide(new BigDecimal("111"), 2, RoundingMode.HALF_UP);
        }

        BigDecimal lineTotal = "INCLUDE".equals(taxType) && d.taxRate() == null
                ? dpp : dpp.add(tax);
        return new LineResult(d.price(), dpp, tax, lineTotal);
    }

    /** Simpan condition yang benar-benar dipakai (SAP condition tab pada baris PO). */
    @Transactional
    public void storeConditions(Long tenantId, Long companyId, Long headerId, Long detailId,
                                Determination d) {
        if (d == null || detailId == null) return;
        String sql =
                "INSERT INTO pur.po_condition " +
                "(tenant_id, company_id, header_id, dtl_id, condition_source, condition_class, " +
                " condition_type, rate, amount, calculation_order) " +
                "VALUES (:tenant, :company, :hdr, :dtl, :src, :cls, :type, :rate, :amount, :seq)";
        for (AppliedCondition c : d.conditions()) {
            em.createNativeQuery(sql)
                    .setParameter("tenant", tenantId)
                    .setParameter("company", companyId)
                    .setParameter("hdr", headerId)
                    .setParameter("dtl", detailId)
                    .setParameter("src", c.source())
                    .setParameter("cls", c.conditionClass())
                    .setParameter("type", c.conditionType())
                    .setParameter("rate", c.rate())
                    .setParameter("amount", c.amount())
                    .setParameter("seq", c.calculationOrder())
                    .executeUpdate();
        }
    }

    private Object[] findContractOrInfo(Long tenantId, Long companyId, Long supplierId,
                                            Long itemId, Long uomId, LocalDate date, BigDecimal qty) {
        String sql =
                "SELECT condition_source, price, price_unit, condition_type, currency, min_qty " +
                "FROM pur.price_list " +
                "WHERE tenant_id = :tenant AND company_id = :company " +
                "AND supplier_id = :sup AND item_id = :item AND uom_id = :uom " +
                "AND condition_class = 'PRICE' " +
                "AND condition_source IN ('CONTRACT', 'INFO_RECORD') " +
                "AND valid_from <= :date AND (valid_to IS NULL OR valid_to >= :date) " +
                "AND min_qty <= :qty " +
                "ORDER BY CASE condition_source WHEN 'CONTRACT' THEN 0 ELSE 1 END, min_qty DESC " +
                "LIMIT 1";
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("tenant", tenantId)
                .setParameter("company", companyId)
                .setParameter("sup", supplierId)
                .setParameter("item", itemId)
                .setParameter("uom", uomId)
                .setParameter("date", date)
                .setParameter("qty", qty)
                .getResultList();
        return rows.isEmpty() ? new Object[6] : rows.get(0);
    }

    private Object[] findRfq(Long tenantId, Long companyId, Long supplierId,
                             Long itemId, Long uomId, LocalDate date) {
        String sql =
                "SELECT rr.unit_price, 'IDR' " +
                "FROM pur.rfq_response rr " +
                "JOIN pur.rfq_dtl rd ON rd.id = rr.rfq_dtl_id " +
                "JOIN pur.rfq_supplier rs ON rs.id = rr.rfq_supplier_id " +
                "JOIN pur.rfq_hdr rh ON rh.id = rd.header_id " +
                "WHERE rh.tenant_id = :tenant AND rh.company_id = :company " +
                "AND rs.supplier_id = :sup AND rd.item_id = :item AND rd.uom_id = :uom " +
                "AND UPPER(rs.status) = 'WON' " +
                "AND UPPER(rh.status) IN ('APPROVED', 'CLOSED') " +
                "AND rh.doc_date <= :date AND rr.unit_price > 0 " +
                "ORDER BY rh.doc_date DESC, rr.id DESC LIMIT 1";
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("tenant", tenantId)
                .setParameter("company", companyId)
                .setParameter("sup", supplierId)
                .setParameter("item", itemId)
                .setParameter("uom", uomId)
                .setParameter("date", date)
                .getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Object[] findLastPo(Long tenantId, Long companyId, Long supplierId,
                                Long itemId, Long uomId, LocalDate date) {
        String sql =
                "SELECT d.price, d.price_unit, h.currency " +
                "FROM pur.purchase_order_dtl d " +
                "JOIN pur.purchase_order_hdr h ON h.id = d.header_id " +
                "WHERE h.tenant_id = :tenant AND h.company_id = :company " +
                "AND h.supplier_id = :sup AND d.item_id = :item AND d.uom_id = :uom " +
                "AND UPPER(COALESCE(h.status, '')) NOT IN ('DRAFT', 'CANCELLED', 'VOID') " +
                "AND h.doc_date <= :date AND COALESCE(d.price, 0) > 0 " +
                "ORDER BY h.doc_date DESC, d.id DESC LIMIT 1";
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("tenant", tenantId)
                .setParameter("company", companyId)
                .setParameter("sup", supplierId)
                .setParameter("item", itemId)
                .setParameter("uom", uomId)
                .setParameter("date", date)
                .getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<AppliedCondition> findExtras(Long tenantId, Long companyId, Long supplierId,
                                              Long itemId, Long uomId, LocalDate date, BigDecimal qty) {
        String sql =
                "SELECT condition_source, condition_class, condition_type, rate, price, price_unit, " +
                "       calculation_order, min_qty " +
                "FROM pur.price_list " +
                "WHERE tenant_id = :tenant AND company_id = :company " +
                "AND supplier_id = :sup AND item_id = :item AND uom_id = :uom " +
                "AND condition_class IN ('DISCOUNT', 'SURCHARGE', 'TAX') " +
                "AND valid_from <= :date AND (valid_to IS NULL OR valid_to >= :date) " +
                "AND min_qty <= :qty " +
                "ORDER BY min_qty DESC";
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("tenant", tenantId)
                .setParameter("company", companyId)
                .setParameter("sup", supplierId)
                .setParameter("item", itemId)
                .setParameter("uom", uomId)
                .setParameter("date", date)
                .setParameter("qty", qty)
                .getResultList();

        Map<String, AppliedCondition> best = new LinkedHashMap<>();
        for (Object[] r : rows) {
            String cls = r[1] != null ? r[1].toString() : "";
            String type = r[2] != null ? r[2].toString() : cls;
            String key = cls + "|" + type;
            String src = r[0] != null ? r[0].toString() : "INFO_RECORD";
            BigDecimal rate = r[3] != null ? new BigDecimal(r[3].toString()) : null;
            BigDecimal amount = r[4] != null ? new BigDecimal(r[4].toString()) : BigDecimal.ZERO;
            if (r[5] != null && new BigDecimal(r[5].toString()).compareTo(BigDecimal.ZERO) > 0) {
                amount = amount.divide(new BigDecimal(r[5].toString()), 4, RoundingMode.HALF_UP);
            }
            int seq = r[6] != null ? ((Number) r[6]).intValue() : 80;
            AppliedCondition candidate = new AppliedCondition(src, cls, type, rate, amount, seq);
            AppliedCondition prev = best.get(key);
            if (prev == null || seq < prev.calculationOrder() || sourceRank(src) < sourceRank(prev.source())) {
                best.put(key, candidate);
            }
        }
        return new ArrayList<>(best.values());
    }
}
