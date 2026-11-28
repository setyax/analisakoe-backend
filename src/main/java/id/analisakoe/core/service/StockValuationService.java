package id.analisakoe.core.service;

import id.analisakoe.context.ContextService;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service("stockValuationService")
public class StockValuationService {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private ContextService context;

    private Long tenant() { return context.tenantId(); }

    /**
     * Konversi qty & unit cost dari satuan dokumen (uomId) ke satuan dasar item.
     * Return: [0] = qty dalam satuan dasar, [1] = unit cost per satuan dasar.
     * Nilai total tetap konsisten: qtyBase * costBase == qtyLine * costLine.
     */
    public BigDecimal[] convertLineToBase(Long itemId, Long uomId, BigDecimal qty, BigDecimal unitCost) {
        BigDecimal zero = BigDecimal.ZERO;
        BigDecimal q = qty == null ? zero : qty;
        BigDecimal c = unitCost == null ? zero : unitCost;
        if (uomId == null) return new BigDecimal[]{ q, c };

        List<?> baseRows = em.createNativeQuery("SELECT base_uom_id, uom_set_id FROM md.item WHERE id = :id")
            .setParameter("id", itemId).getResultList();
        if (baseRows.isEmpty() || baseRows.get(0) == null) return new BigDecimal[]{ q, c };
        Object[] itemRow = (Object[]) baseRows.get(0);
        Long baseUom = itemRow[0] == null ? null : ((Number) itemRow[0]).longValue();
        Long itemSetId = itemRow[1] == null ? null : ((Number) itemRow[1]).longValue();
        if (baseUom == null || baseUom.equals(uomId)) return new BigDecimal[]{ q, c };

        BigDecimal lineF = null;
        BigDecimal baseF = null;

        // 1) Konversi langsung di md.uom_conversion
        List<?> f = em.createNativeQuery("SELECT factor FROM md.uom_conversion WHERE from_uom_id = :f AND to_uom_id = :t")
            .setParameter("f", uomId).setParameter("t", baseUom).getResultList();
        if (!f.isEmpty()) {
            lineF = BigDecimal.ONE;
            baseF = BigDecimal.ONE.divide(new BigDecimal(f.get(0).toString()), 8, RoundingMode.HALF_UP);
        } else {
            List<?> r = em.createNativeQuery("SELECT factor FROM md.uom_conversion WHERE from_uom_id = :f AND to_uom_id = :t")
                .setParameter("f", baseUom).setParameter("t", uomId).getResultList();
            if (!r.isEmpty()) {
                BigDecimal rev = new BigDecimal(r.get(0).toString());
                if (rev.compareTo(zero) > 0) {
                    lineF = rev;
                    baseF = BigDecimal.ONE;
                }
            }
        }

        // 2) UoM Set: pakai set milik item kalau ada; kalau tidak, cari set yang memuat kedua satuan
        if (lineF == null) {
            List<?> sets;
            if (itemSetId != null) {
                sets = em.createNativeQuery(
                    "SELECT :sid WHERE EXISTS (SELECT 1 FROM md.uom_set_dtl WHERE uom_set_id = :sid AND uom_id = :a) " +
                    "AND EXISTS (SELECT 1 FROM md.uom_set_dtl WHERE uom_set_id = :sid AND uom_id = :b)")
                    .setParameter("sid", itemSetId).setParameter("a", uomId).setParameter("b", baseUom).getResultList();
            } else {
                sets = em.createNativeQuery(
                    "SELECT usd.uom_set_id FROM md.uom_set_dtl usd " +
                    "WHERE usd.uom_id IN (:a, :b) " +
                    "GROUP BY usd.uom_set_id HAVING COUNT(DISTINCT usd.uom_id) = 2")
                    .setParameter("a", uomId).setParameter("b", baseUom).getResultList();
            }
            if (!sets.isEmpty()) {
                Object sId = sets.get(0);
                List<?> lf = em.createNativeQuery(
                    "SELECT conversion_factor FROM md.uom_set_dtl WHERE uom_set_id = :s AND uom_id = :u")
                    .setParameter("s", sId).setParameter("u", uomId).getResultList();
                List<?> bf = em.createNativeQuery(
                    "SELECT conversion_factor FROM md.uom_set_dtl WHERE uom_set_id = :s AND uom_id = :u")
                    .setParameter("s", sId).setParameter("u", baseUom).getResultList();
                if (!lf.isEmpty() && !bf.isEmpty()) {
                    lineF = new BigDecimal(lf.get(0).toString());
                    baseF = new BigDecimal(bf.get(0).toString());
                }
            }
        }

        if (lineF == null || baseF == null || lineF.compareTo(zero) == 0 || baseF.compareTo(zero) == 0)
            return new BigDecimal[]{ q, c };

        // 1 lineUom = lineF satuan-set; 1 baseUom = baseF satuan-set
        // qtyBase = qty * lineF / baseF;  costBase = cost * lineF / baseF
        BigDecimal ratio = lineF.divide(baseF, 8, RoundingMode.HALF_UP);
        return new BigDecimal[]{ q.multiply(ratio).setScale(4, RoundingMode.HALF_UP), c.multiply(ratio) };
    }

    @Transactional
    public void stockIn(Long itemId, Long warehouseId, BigDecimal qty, BigDecimal unitCost,
                        LocalDate date, String refType, Long refId, String refNo, String remarks) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) return;
        if (warehouseId == null) warehouseId = 1L;

        Object[] current = getOrCreateBalance(itemId, warehouseId);
        BigDecimal oldQty   = new BigDecimal(current[0].toString());
        BigDecimal oldValue = new BigDecimal(current[2].toString());

        BigDecimal newQty     = oldQty.add(qty);
        BigDecimal addValue   = qty.multiply(unitCost);
        BigDecimal newValue   = oldValue.add(addValue);
        BigDecimal newAvgCost = newQty.compareTo(BigDecimal.ZERO) > 0
            ? newValue.divide(newQty, 4, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        em.createNativeQuery(
            "UPDATE inv.stock_balance SET qty = :qty, avg_cost = :avg, total_value = :val, last_updated = NOW() " +
            "WHERE tenant_id = :tenant AND item_id = :itemId AND warehouse_id = :whId")
            .setParameter("tenant", tenant()).setParameter("qty", newQty).setParameter("avg", newAvgCost)
            .setParameter("val", newValue).setParameter("itemId", itemId)
            .setParameter("whId", warehouseId).executeUpdate();

        insertMovement(date, itemId, warehouseId, "IN", qty, unitCost, addValue,
                       newQty, newAvgCost, refType, refId, refNo, remarks);
    }

    @Transactional
    public BigDecimal stockOut(Long itemId, Long warehouseId, BigDecimal qty,
                                LocalDate date, String refType, Long refId, String refNo, String remarks) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        if (warehouseId == null) warehouseId = 1L;

        Object[] current = getOrCreateBalance(itemId, warehouseId);
        BigDecimal oldQty   = new BigDecimal(current[0].toString());
        BigDecimal avgCost  = new BigDecimal(current[1].toString());
        BigDecimal oldValue = new BigDecimal(current[2].toString());

        if (oldQty.compareTo(qty) < 0) {
            throw new RuntimeException("Stok tidak cukup untuk item ID " + itemId +
                ". Tersedia: " + oldQty + ", dibutuhkan: " + qty);
        }

        BigDecimal newQty     = oldQty.subtract(qty);
        BigDecimal cogs       = qty.multiply(avgCost);
        BigDecimal newValue   = oldValue.subtract(cogs);
        BigDecimal newAvgCost = newQty.compareTo(BigDecimal.ZERO) > 0
            ? newValue.divide(newQty, 4, RoundingMode.HALF_UP)
            : avgCost;

        em.createNativeQuery(
            "UPDATE inv.stock_balance SET qty = :qty, avg_cost = :avg, total_value = :val, last_updated = NOW() " +
            "WHERE tenant_id = :tenant AND item_id = :itemId AND warehouse_id = :whId")
            .setParameter("tenant", tenant()).setParameter("qty", newQty).setParameter("avg", newAvgCost)
            .setParameter("val", newValue).setParameter("itemId", itemId)
            .setParameter("whId", warehouseId).executeUpdate();

        insertMovement(date, itemId, warehouseId, "OUT", qty, avgCost, cogs,
                       newQty, newAvgCost, refType, refId, refNo, remarks);

        return avgCost;
    }

    /** Tenant-aware stock IN used by audited source-document reversals. */
    @Transactional
    public void stockInForTenant(Long tenantId, Long itemId, Long warehouseId, BigDecimal qty,
                                 BigDecimal unitCost, LocalDate date, String refType,
                                 Long refId, String refNo, String remarks) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Qty stock IN harus lebih besar dari nol.");
        }
        if (unitCost == null || unitCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Unit cost stock IN tidak valid.");
        }
        Object[] current = getOrCreateBalanceForTenant(tenantId, itemId, warehouseId);
        BigDecimal oldQty = new BigDecimal(current[0].toString());
        BigDecimal oldValue = new BigDecimal(current[2].toString());
        BigDecimal newQty = oldQty.add(qty);
        BigDecimal addValue = qty.multiply(unitCost);
        BigDecimal newValue = oldValue.add(addValue);
        BigDecimal newAvgCost = newQty.compareTo(BigDecimal.ZERO) > 0
                ? newValue.divide(newQty, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        em.createNativeQuery(
                "UPDATE inv.stock_balance SET qty=:qty, avg_cost=:avg, total_value=:val, last_updated=NOW() " +
                "WHERE tenant_id=:tenantId AND item_id=:itemId AND warehouse_id=:whId")
                .setParameter("qty", newQty).setParameter("avg", newAvgCost).setParameter("val", newValue)
                .setParameter("tenantId", tenantId).setParameter("itemId", itemId).setParameter("whId", warehouseId)
                .executeUpdate();
        insertMovementForTenant(tenantId, date, itemId, warehouseId, "IN", qty, unitCost, addValue,
                newQty, newAvgCost, refType, refId, refNo, remarks);
    }

    /** Tenant-aware stock OUT used by Delivery Order Goods Issue. */
    @Transactional
    public BigDecimal stockOutForTenant(Long tenantId, Long itemId, Long warehouseId, BigDecimal qty,
                                        LocalDate date, String refType, Long refId,
                                        String refNo, String remarks) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Qty stock OUT harus lebih besar dari nol.");
        }
        Object[] current = getOrCreateBalanceForTenant(tenantId, itemId, warehouseId);
        BigDecimal oldQty = new BigDecimal(current[0].toString());
        BigDecimal avgCost = new BigDecimal(current[1].toString());
        BigDecimal oldValue = new BigDecimal(current[2].toString());
        if (oldQty.compareTo(qty) < 0) {
            throw new IllegalStateException("Stok tidak cukup untuk item ID " + itemId +
                    ". Tersedia: " + oldQty + ", dibutuhkan: " + qty);
        }
        BigDecimal newQty = oldQty.subtract(qty);
        BigDecimal cogs = qty.multiply(avgCost);
        BigDecimal newValue = oldValue.subtract(cogs);
        BigDecimal newAvgCost = newQty.compareTo(BigDecimal.ZERO) > 0
                ? newValue.divide(newQty, 4, RoundingMode.HALF_UP) : avgCost;

        em.createNativeQuery(
                "UPDATE inv.stock_balance SET qty=:qty, avg_cost=:avg, total_value=:val, last_updated=NOW() " +
                "WHERE tenant_id=:tenantId AND item_id=:itemId AND warehouse_id=:whId")
                .setParameter("qty", newQty).setParameter("avg", newAvgCost).setParameter("val", newValue)
                .setParameter("tenantId", tenantId).setParameter("itemId", itemId).setParameter("whId", warehouseId)
                .executeUpdate();
        insertMovementForTenant(tenantId, date, itemId, warehouseId, "OUT", qty, avgCost, cogs,
                newQty, newAvgCost, refType, refId, refNo, remarks);
        return avgCost;
    }

    private Object[] getOrCreateBalanceForTenant(Long tenantId, Long itemId, Long warehouseId) {
        @SuppressWarnings("unchecked")
        List<?> rows = em.createNativeQuery(
                "SELECT qty, avg_cost, total_value FROM inv.stock_balance " +
                "WHERE tenant_id=:tenantId AND item_id=:itemId AND warehouse_id=:whId")
                .setParameter("tenantId", tenantId).setParameter("itemId", itemId).setParameter("whId", warehouseId)
                .getResultList();
        if (rows.isEmpty()) {
            em.createNativeQuery(
                    "INSERT INTO inv.stock_balance (tenant_id,item_id,warehouse_id,qty,avg_cost,total_value) " +
                    "VALUES (:tenantId,:itemId,:whId,0,0,0) ON CONFLICT (tenant_id,item_id,warehouse_id) DO NOTHING")
                    .setParameter("tenantId", tenantId).setParameter("itemId", itemId).setParameter("whId", warehouseId)
                    .executeUpdate();
            rows = em.createNativeQuery(
                    "SELECT qty, avg_cost, total_value FROM inv.stock_balance " +
                    "WHERE tenant_id=:tenantId AND item_id=:itemId AND warehouse_id=:whId")
                    .setParameter("tenantId", tenantId).setParameter("itemId", itemId).setParameter("whId", warehouseId)
                    .getResultList();
        }
        if (rows.isEmpty()) throw new IllegalStateException("Gagal membuat saldo stok untuk item " + itemId + ".");
        Object[] row = (Object[]) rows.get(0);
        return new Object[] {
                row[0] != null ? row[0] : BigDecimal.ZERO,
                row[1] != null ? row[1] : BigDecimal.ZERO,
                row[2] != null ? row[2] : BigDecimal.ZERO
        };
    }

    private void insertMovementForTenant(Long tenantId, LocalDate date, Long itemId, Long warehouseId,
                                         String direction, BigDecimal qty, BigDecimal unitCost,
                                         BigDecimal totalCost, BigDecimal balanceQty, BigDecimal balanceAvgCost,
                                         String refType, Long refId, String refNo, String remarks) {
        em.createNativeQuery(
                "INSERT INTO inv.stock_movement (tenant_id,movement_date,item_id,warehouse_id,direction, " +
                "qty,unit_cost,total_cost,balance_qty,balance_avg_cost,reference_type,reference_id,reference_no,remarks) " +
                "VALUES (:tenantId,:dt,:itemId,:whId,:dir,:qty,:uc,:tc,:bq,:bac,:rt,:rid,:rno,:rem)")
                .setParameter("tenantId", tenantId).setParameter("dt", date).setParameter("itemId", itemId)
                .setParameter("whId", warehouseId).setParameter("dir", direction).setParameter("qty", qty)
                .setParameter("uc", unitCost).setParameter("tc", totalCost).setParameter("bq", balanceQty)
                .setParameter("bac", balanceAvgCost).setParameter("rt", refType).setParameter("rid", refId)
                .setParameter("rno", refNo).setParameter("rem", remarks).executeUpdate();
    }

    private Object[] getOrCreateBalance(Long itemId, Long warehouseId) {
        List<?> rows = em.createNativeQuery(
            "SELECT qty, avg_cost, total_value FROM inv.stock_balance " +
            "WHERE tenant_id = :tenant AND item_id = :itemId AND warehouse_id = :whId")
            .setParameter("tenant", tenant()).setParameter("itemId", itemId)
            .setParameter("whId", warehouseId).getResultList();

        if (rows.isEmpty()) {
            em.createNativeQuery(
                "INSERT INTO inv.stock_balance (tenant_id, item_id, warehouse_id, qty, avg_cost, total_value) " +
                "VALUES (:tenant, :itemId, :whId, 0, 0, 0) ON CONFLICT DO NOTHING")
                .setParameter("tenant", tenant()).setParameter("itemId", itemId)
                .setParameter("whId", warehouseId).executeUpdate();
            return new Object[] { BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO };
        }
        Object[] row = (Object[]) rows.get(0);
        return new Object[] {
            row[0] != null ? row[0] : BigDecimal.ZERO,
            row[1] != null ? row[1] : BigDecimal.ZERO,
            row[2] != null ? row[2] : BigDecimal.ZERO
        };
    }

    private void insertMovement(LocalDate date, Long itemId, Long warehouseId, String direction,
                                 BigDecimal qty, BigDecimal unitCost, BigDecimal totalCost,
                                 BigDecimal balQty, BigDecimal balAvgCost,
                                 String refType, Long refId, String refNo, String remarks) {
        em.createNativeQuery(
            "INSERT INTO inv.stock_movement (tenant_id, movement_date, item_id, warehouse_id, direction, " +
            "qty, unit_cost, total_cost, balance_qty, balance_avg_cost, reference_type, reference_id, reference_no, remarks) " +
            "VALUES (:tenant, :dt, :itemId, :whId, :dir, :qty, :uc, :tc, :bq, :bac, :rt, :rid, :rno, :rem)")
            .setParameter("tenant", tenant()).setParameter("dt", date)
            .setParameter("itemId", itemId).setParameter("whId", warehouseId)
            .setParameter("dir", direction).setParameter("qty", qty).setParameter("uc", unitCost)
            .setParameter("tc", totalCost).setParameter("bq", balQty).setParameter("bac", balAvgCost)
            .setParameter("rt", refType).setParameter("rid", refId).setParameter("rno", refNo)
            .setParameter("rem", remarks).executeUpdate();
    }

    public BigDecimal getCurrentStock(Long itemId, Long warehouseId) {
        if (warehouseId == null) warehouseId = 1L;
        List<?> rows = em.createNativeQuery(
            "SELECT qty FROM inv.stock_balance WHERE tenant_id = :tenant AND item_id = :itemId AND warehouse_id = :whId")
            .setParameter("tenant", tenant()).setParameter("itemId", itemId)
            .setParameter("whId", warehouseId).getResultList();
        return rows.isEmpty() ? BigDecimal.ZERO : new BigDecimal(rows.get(0).toString());
    }
	
	    // ============================================================
    // RECALCULATE PERIODE — HANYA TRANSAKSI CONFIRMED
    // ============================================================
    @Transactional
    public java.util.Map<String, Object> recalculatePeriod(
            LocalDate startDate, LocalDate endDate, Long warehouseId) {

        if (warehouseId == null) warehouseId = 1L;
        String period = startDate.toString().substring(0, 7);

        // 1. Ambil item yang punya movement VALID sampai endDate
        List<?> itemIdRows = em.createNativeQuery(
            "SELECT DISTINCT sm.item_id FROM inv.stock_movement sm " +
            "WHERE sm.tenant_id = :tenant AND sm.warehouse_id = :whId " +
            "AND sm.movement_date <= :endDate " +
            "AND (" + buildValidReferenceFilter("sm") + ")")
            .setParameter("tenant", tenant())
            .setParameter("whId", warehouseId)
            .setParameter("endDate", endDate)
            .getResultList();

        int processed = 0;

        for (Object itemIdObj : itemIdRows) {
            Long itemId = ((Number) itemIdObj).longValue();

            // 2. Begin state — replay movement valid sebelum startDate
            BigDecimal[] beginState = replayUpTo(itemId, warehouseId, startDate.minusDays(1));
            BigDecimal beginQty     = beginState[0];
            BigDecimal beginAvgCost = beginState[1];
            BigDecimal beginValue   = beginQty.multiply(beginAvgCost);

            // 3. Movement dalam periode — HANYA yang CONFIRMED
            List<?> movements = em.createNativeQuery(
                "SELECT sm.direction, sm.qty, sm.unit_cost " +
                "FROM inv.stock_movement sm " +
                "WHERE sm.tenant_id = :tenant AND sm.warehouse_id = :whId AND sm.item_id = :itemId " +
                "AND sm.movement_date BETWEEN :startDate AND :endDate " +
                "AND (" + buildValidReferenceFilter("sm") + ") " +
                "ORDER BY sm.movement_date, sm.id")
                .setParameter("tenant", tenant())
                .setParameter("whId", warehouseId)
                .setParameter("itemId", itemId)
                .setParameter("startDate", startDate)
                .setParameter("endDate", endDate)
                .getResultList();

            BigDecimal inQty = BigDecimal.ZERO, inValue = BigDecimal.ZERO;
            BigDecimal outQty = BigDecimal.ZERO, outValue = BigDecimal.ZERO;
            BigDecimal runningQty   = beginQty;
            BigDecimal runningValue = beginValue;

            for (Object rowObj : movements) {
                Object[] mov = (Object[]) rowObj;
                String direction = mov[0].toString();
                BigDecimal mqty = new BigDecimal(mov[1].toString());

                if ("IN".equals(direction)) {
                    BigDecimal unitCost = new BigDecimal(mov[2].toString());
                    BigDecimal addValue = mqty.multiply(unitCost);
                    inQty   = inQty.add(mqty);
                    inValue = inValue.add(addValue);
                    runningQty   = runningQty.add(mqty);
                    runningValue = runningValue.add(addValue);
                } else {
                    BigDecimal avgCost = runningQty.compareTo(BigDecimal.ZERO) > 0
                        ? runningValue.divide(runningQty, 4, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                    BigDecimal cogs = mqty.multiply(avgCost);
                    outQty   = outQty.add(mqty);
                    outValue = outValue.add(cogs);
                    runningQty   = runningQty.subtract(mqty);
                    runningValue = runningValue.subtract(cogs);
                }
            }

            BigDecimal endQty     = runningQty;
            BigDecimal endValue   = runningValue;
            BigDecimal endAvgCost = endQty.compareTo(BigDecimal.ZERO) > 0
                ? endValue.divide(endQty, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            // 4. Upsert snapshot
            em.createNativeQuery(
                "INSERT INTO inv.stock_period_snapshot " +
                "(tenant_id, warehouse_id, item_id, period, period_start, period_end, " +
                " begin_qty, begin_avg_cost, begin_value, " +
                " in_qty, in_value, out_qty, out_value, " +
                " end_qty, end_avg_cost, end_value, cogs, recalculated_at) " +
                "VALUES (:tenant, :whId, :itemId, :period, :startDate, :endDate, " +
                " :beginQty, :beginAvgCost, :beginValue, " +
                " :inQty, :inValue, :outQty, :outValue, " +
                " :endQty, :endAvgCost, :endValue, :cogs, NOW()) " +
                "ON CONFLICT (tenant_id, warehouse_id, item_id, period) DO UPDATE SET " +
                " begin_qty=:beginQty, begin_avg_cost=:beginAvgCost, begin_value=:beginValue, " +
                " in_qty=:inQty, in_value=:inValue, out_qty=:outQty, out_value=:outValue, " +
                " end_qty=:endQty, end_avg_cost=:endAvgCost, end_value=:endValue, " +
                " cogs=:cogs, recalculated_at=NOW()")
                .setParameter("tenant", tenant())
                .setParameter("whId", warehouseId)
                .setParameter("itemId", itemId)
                .setParameter("period", period)
                .setParameter("startDate", startDate)
                .setParameter("endDate", endDate)
                .setParameter("beginQty", beginQty)
                .setParameter("beginAvgCost", beginAvgCost)
                .setParameter("beginValue", beginValue)
                .setParameter("inQty", inQty)
                .setParameter("inValue", inValue)
                .setParameter("outQty", outQty)
                .setParameter("outValue", outValue)
                .setParameter("endQty", endQty)
                .setParameter("endAvgCost", endAvgCost)
                .setParameter("endValue", endValue)
                .setParameter("cogs", outValue)
                .executeUpdate();

            processed++;
        }

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("period", period);
        result.put("items_processed", processed);
        result.put("message", "Recalculate selesai untuk periode " + period +
            " (hanya transaksi CONFIRMED)");
        return result;
    }

    private String buildValidReferenceFilter(String alias) {
    return "( " +
        // GRN/GRN_REVERSAL/GRN_VOID: valid HANYA jika status GRN asli = CONFIRMED
        "(" + alias + ".reference_type IN ('GRN','GRN_REVERSAL','GRN_VOID') AND EXISTS (" +
        "  SELECT 1 FROM pur.grn_hdr gh WHERE gh.id = " + alias + ".reference_id " +
        "  AND gh.status = 'CONFIRMED')) " +
        " OR " +
        // DO/DO_REVERSAL/DO_VOID: valid HANYA jika status DO asli = CONFIRMED
        "(" + alias + ".reference_type IN ('DO','DO_REVERSAL','DO_VOID') AND EXISTS (" +
        "  SELECT 1 FROM sal.delivery_order_hdr dh WHERE dh.id = " + alias + ".reference_id " +
        "  AND dh.status = 'CONFIRMED')) " +
        // Lain-lain (ADJUSTMENT, TRANSFER, OPNAME): selalu valid
        " OR (" + alias + ".reference_type NOT IN " +
        "     ('GRN','GRN_REVERSAL','GRN_VOID','DO','DO_REVERSAL','DO_VOID')) " +
        ")";
}

    /** Replay movement VALID sampai tanggal tertentu. */
    private BigDecimal[] replayUpTo(Long itemId, Long warehouseId, LocalDate upToDate) {
        List<?> movements = em.createNativeQuery(
            "SELECT sm.direction, sm.qty, sm.unit_cost FROM inv.stock_movement sm " +
            "WHERE sm.tenant_id = :tenant AND sm.warehouse_id = :whId AND sm.item_id = :itemId " +
            "AND sm.movement_date <= :upToDate " +
            "AND (" + buildValidReferenceFilter("sm") + ") " +
            "ORDER BY sm.movement_date, sm.id")
            .setParameter("tenant", tenant())
            .setParameter("whId", warehouseId)
            .setParameter("itemId", itemId)
            .setParameter("upToDate", upToDate)
            .getResultList();

        BigDecimal qty   = BigDecimal.ZERO;
        BigDecimal value = BigDecimal.ZERO;

        for (Object rowObj : movements) {
            Object[] mov = (Object[]) rowObj;
            String direction = mov[0].toString();
            BigDecimal mqty = new BigDecimal(mov[1].toString());

            if ("IN".equals(direction)) {
                BigDecimal unitCost = new BigDecimal(mov[2].toString());
                qty   = qty.add(mqty);
                value = value.add(mqty.multiply(unitCost));
            } else {
                BigDecimal avgCost = qty.compareTo(BigDecimal.ZERO) > 0
                    ? value.divide(qty, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
                qty   = qty.subtract(mqty);
                value = value.subtract(mqty.multiply(avgCost));
            }
        }

        BigDecimal avgCost = qty.compareTo(BigDecimal.ZERO) > 0
            ? value.divide(qty, 4, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        return new BigDecimal[] { qty, avgCost };
    }
}