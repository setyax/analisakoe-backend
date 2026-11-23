package id.analisakoe.sal.service;

import id.analisakoe.core.service.StockValuationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Delivery Order lifecycle and Goods Issue posting.
 * All stock-affecting work runs in one database transaction.
 */
@Service
public class DeliveryOrderService {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private StockValuationService stockValuationService;

    @Transactional(readOnly = true)
    public String previewDocNo(Long compId) {
        return buildDocNo(compId);
    }

    /** Create a DO with the active default warehouse already selected. */
    @Transactional
    public String create(Long compId, Long soId, LocalDate doDate, String remarks,
                         List<Long> itemIds, List<Long> uomIds,
                         List<BigDecimal> qtys, List<BigDecimal> prices) {

        if (itemIds == null || itemIds.isEmpty()) {
            throw new IllegalArgumentException("Minimal harus ada 1 item.");
        }
        if (qtys == null || qtys.size() != itemIds.size()) {
            throw new IllegalArgumentException("Data item dan qty tidak sinkron.");
        }

        Long tenantId = getCompanyTenant(compId);
        Long warehouseId = getDefaultWarehouse(compId);
        if (warehouseId == null) {
            throw new IllegalStateException("Perusahaan belum memiliki warehouse ACTIVE.");
        }

        Map<Long, BigDecimal> requested = new LinkedHashMap<>();
        for (int i = 0; i < itemIds.size(); i++) {
            Long itemId = itemIds.get(i);
            BigDecimal qty = qtys.get(i);
            if (itemId == null || qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            requested.merge(itemId, qty, BigDecimal::add);
        }
        if (requested.isEmpty()) {
            throw new IllegalArgumentException("Minimal harus ada 1 item dengan qty lebih dari 0.");
        }

        @SuppressWarnings("unchecked")
        List<Object> soRows = em.createNativeQuery(
                "SELECT COALESCE(customer_id, 0) FROM sal.sales_order_hdr " +
                "WHERE id = :soId AND company_id = :compId FOR UPDATE")
                .setParameter("soId", soId)
                .setParameter("compId", compId)
                .getResultList();
        if (soRows.isEmpty()) throw new IllegalStateException("Sales Order tidak ditemukan.");
        long custTmp = ((Number) soRows.get(0)).longValue();
        Long customerId = custTmp == 0 ? null : custTmp;

        for (Map.Entry<Long, BigDecimal> entry : requested.entrySet()) {
            Long itemId = entry.getKey();
            BigDecimal soQty;
            Object soQtyRaw = em.createNativeQuery(
                    "SELECT COALESCE(SUM(qty), 0) FROM sal.sales_order_dtl " +
                    "WHERE header_id = :soId AND item_id = :itemId")
                    .setParameter("soId", soId).setParameter("itemId", itemId).getSingleResult();
            soQty = new BigDecimal(soQtyRaw.toString());
            if (soQty.compareTo(BigDecimal.ZERO) == 0) {
                throw new IllegalStateException("Item ID " + itemId + " tidak ada di Sales Order ini.");
            }
            Object deliveredRaw = em.createNativeQuery(
                    "SELECT COALESCE(SUM(dod.qty), 0) FROM sal.delivery_order_dtl dod " +
                    "JOIN sal.delivery_order_hdr doh ON dod.header_id = doh.id " +
                    "WHERE doh.so_id = :soId AND dod.item_id = :itemId " +
                    "AND doh.status NOT IN ('VOID', 'CANCELLED')")
                    .setParameter("soId", soId).setParameter("itemId", itemId).getSingleResult();
            BigDecimal allocatedQty = new BigDecimal(deliveredRaw.toString());
            BigDecimal remainingQty = soQty.subtract(allocatedQty);
            if (entry.getValue().compareTo(remainingQty) > 0) {
                throw new IllegalStateException("Qty DO melebihi sisa SO. Item ID " + itemId +
                        " - Sisa yang belum dialokasikan: " + remainingQty);
            }
        }

        em.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:key))")
                .setParameter("key", "DO-" + compId + "-" + LocalDate.now().getYear())
                .getSingleResult();
        String docNo = buildDocNo(compId);

        Object newIdRaw = em.createNativeQuery(
                "INSERT INTO sal.delivery_order_hdr " +
                "(tenant_id, company_id, doc_no, doc_date, so_id, customer_id, warehouse_id, " +
                " remarks, status, picking_status, goods_issue_status, pod_status, created_at) " +
                "VALUES (:tenantId, :compId, :docNo, :doDate, :soId, CAST(:customerId AS BIGINT), " +
                ":warehouseId, CAST(:remarks AS TEXT), 'DRAFT', 'NOT_STARTED', 'NOT_POSTED', 'PENDING', NOW()) " +
                "RETURNING id")
                .setParameter("tenantId", tenantId)
                .setParameter("compId", compId)
                .setParameter("docNo", docNo)
                .setParameter("doDate", doDate)
                .setParameter("soId", soId)
                .setParameter("customerId", customerId)
                .setParameter("warehouseId", warehouseId)
                .setParameter("remarks", remarks)
                .getSingleResult();
        Long doId = ((Number) newIdRaw).longValue();

        for (int i = 0; i < itemIds.size(); i++) {
            Long itemId = itemIds.get(i);
            BigDecimal qty = qtys.get(i);
            if (itemId == null || qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            Long uomId = (uomIds != null && i < uomIds.size()) ? uomIds.get(i) : null;

            int inserted = em.createNativeQuery(
                    "INSERT INTO sal.delivery_order_dtl (header_id, item_id, uom_id, qty, gi_posted_qty) " +
                    "SELECT :doId, :itemId, COALESCE(CAST(:uomId AS BIGINT), " +
                    " (SELECT sod.uom_id FROM sal.sales_order_dtl sod " +
                    "  WHERE sod.header_id = :soId AND sod.item_id = :itemId LIMIT 1)), :qty, 0")
                    .setParameter("doId", doId)
                    .setParameter("itemId", itemId)
                    .setParameter("uomId", uomId)
                    .setParameter("soId", soId)
                    .setParameter("qty", qty)
                    .executeUpdate();
            if (inserted == 0) throw new IllegalStateException("Gagal menyimpan detail untuk Item ID " + itemId + ".");
        }
        return docNo;
    }

    /** Confirm only opens the DO; it never touches inventory. */
    @Transactional
    public String confirm(Long id, Long compId) {
        Object[] h = lockHeader(id, compId);
        String status = String.valueOf(h[0]);
        String docNo = String.valueOf(h[1]);
        if ("DRAFT".equals(status)) {
            em.createNativeQuery("UPDATE sal.delivery_order_hdr SET status='OPEN' WHERE id=:id AND company_id=:compId")
                    .setParameter("id", id).setParameter("compId", compId).executeUpdate();
            return docNo;
        }
        if ("OPEN".equals(status)) return docNo;
        if ("CONFIRMED".equals(status) && hasNoNetGoodsIssue(id)) {
            em.createNativeQuery("UPDATE sal.delivery_order_hdr SET status='OPEN' WHERE id=:id AND company_id=:compId")
                    .setParameter("id", id).setParameter("compId", compId).executeUpdate();
            return docNo;
        }
        throw new IllegalStateException("DO hanya dapat di-confirm dari DRAFT menjadi OPEN.");
    }

    /** Backward-compatible unconfirm; only allowed before any net GI quantity exists. */
    @Transactional
    public String unconfirm(Long id, Long compId) {
        Object[] h = lockHeader(id, compId);
        String status = String.valueOf(h[0]);
        String docNo = String.valueOf(h[1]);
        if (!"OPEN".equals(status) && !"CONFIRMED".equals(status)) {
            throw new IllegalStateException("Hanya DO OPEN (atau legacy CONFIRMED) yang dapat dikembalikan ke DRAFT.");
        }
        if (!hasNoNetGoodsIssue(id)) {
            throw new IllegalStateException("DO sudah memiliki Goods Issue. Lakukan reversal sebelum mengubah status.");
        }
        em.createNativeQuery("UPDATE sal.delivery_order_hdr SET status='DRAFT', picking_status='NOT_STARTED' WHERE id=:id")
                .setParameter("id", id).executeUpdate();
        return docNo;
    }

    @Transactional
    public String startPicking(Long id, Long compId) {
        Object[] h = lockHeader(id, compId);
        if (!"OPEN".equals(h[0])) throw new IllegalStateException("Picking hanya dapat dimulai dari status OPEN.");
        em.createNativeQuery("UPDATE sal.delivery_order_hdr SET status='IN_PICKING', picking_status='IN_PROGRESS' WHERE id=:id")
                .setParameter("id", id).executeUpdate();
        return String.valueOf(h[1]);
    }

    @Transactional
    public String readyToShip(Long id, Long compId) {
        Object[] h = lockHeader(id, compId);
        if (!"IN_PICKING".equals(h[0])) throw new IllegalStateException("Ready to Ship hanya dapat dilakukan setelah In Picking.");
        em.createNativeQuery("UPDATE sal.delivery_order_hdr SET status='READY_TO_SHIP', picking_status='READY' WHERE id=:id")
                .setParameter("id", id).executeUpdate();
        return String.valueOf(h[1]);
    }

    @Transactional
    public void changeWarehouse(Long id, Long compId, Long warehouseId) {
        Object[] h = lockHeader(id, compId);
        String status = String.valueOf(h[0]);
        if (!List.of("DRAFT", "OPEN", "IN_PICKING", "READY_TO_SHIP").contains(status)) {
            throw new IllegalStateException("Warehouse tidak dapat diubah setelah Goods Issue dimulai.");
        }
        if (!hasNoNetGoodsIssue(id)) throw new IllegalStateException("Warehouse tidak dapat diubah karena DO sudah memiliki GI.");
        Long tenantId = ((Number) h[3]).longValue();
        validateWarehouse(tenantId, compId, warehouseId);
        em.createNativeQuery("UPDATE sal.delivery_order_hdr SET warehouse_id=:whId WHERE id=:id")
                .setParameter("whId", warehouseId).setParameter("id", id).executeUpdate();
    }

    /**
     * Post one GI batch. A client-generated idempotency key makes browser retries safe.
     * Header, detail, stock movement, stock balance, and DO posted quantities commit together.
     */
    @Transactional
    public String postGoodsIssue(Long id, Long compId, String idempotencyKey,
                                 List<Long> detailIds, List<BigDecimal> quantities) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 80) {
            throw new IllegalArgumentException("Idempotency key wajib diisi (maksimal 80 karakter).");
        }
        if (detailIds == null || quantities == null || detailIds.size() != quantities.size()) {
            throw new IllegalArgumentException("Detail ID dan kuantitas GI tidak sinkron.");
        }
        Map<Long, BigDecimal> requested = normalizeLines(detailIds, quantities);
        if (requested.isEmpty()) throw new IllegalArgumentException("Masukkan minimal satu kuantitas GI lebih besar dari nol.");

        Object[] h = lockHeader(id, compId);
        Long tenantId = ((Number) h[3]).longValue();
        LocalDate docDate = toLocalDate(h[4]);
        Long warehouseId = h[5] == null ? null : ((Number) h[5]).longValue();
        String status = String.valueOf(h[0]);
        String docNo = String.valueOf(h[1]);
        String giStatus = String.valueOf(h[6]);

        @SuppressWarnings("unchecked")
        List<Object[]> previous = em.createNativeQuery(
                "SELECT id, doc_no FROM inv.transaction_header " +
                "WHERE company_id=:compId AND source_do_id=:doId AND idempotency_key=:key " +
                "AND doc_type='OUT' AND status='POSTED'")
                .setParameter("compId", compId).setParameter("doId", id).setParameter("key", idempotencyKey)
                .getResultList();
        if (!previous.isEmpty()) {
            Long previousId = ((Number) previous.get(0)[0]).longValue();
            Map<Long, BigDecimal> previousLines = getTransactionDoLines(previousId);
            if (!sameQuantities(requested, previousLines)) {
                throw new IllegalStateException("Idempotency key sudah pernah dipakai dengan kuantitas berbeda.");
            }
            return String.valueOf(previous.get(0)[1]);
        }

        if (!List.of("READY_TO_SHIP", "PARTIALLY_SHIPPED").contains(status)) {
            throw new IllegalStateException("Goods Issue hanya dapat diposting dari READY_TO_SHIP atau PARTIALLY_SHIPPED.");
        }
        if ("LEGACY_REVIEW".equals(giStatus)) {
            throw new IllegalStateException("DO SHIPPED lama membutuhkan rekonsiliasi manual sebelum Goods Issue.");
        }
        if (docDate.isAfter(LocalDate.now())) throw new IllegalStateException("Tanggal Goods Issue tidak boleh di masa depan.");
        if (warehouseId == null) throw new IllegalStateException("DO belum memiliki warehouse.");
        validateWarehouse(tenantId, compId, warehouseId);
        ensureInventoryPeriodOpen(tenantId, compId, docDate);

        Map<Long, Object[]> detailData = new LinkedHashMap<>();
        Map<Long, BigDecimal> stockRequired = new java.util.TreeMap<>();
        for (Map.Entry<Long, BigDecimal> entry : requested.entrySet()) {
            Long detailId = entry.getKey();
            BigDecimal qty = entry.getValue();
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery(
                    "SELECT item_id, uom_id, qty, gi_posted_qty FROM sal.delivery_order_dtl " +
                    "WHERE id=:detailId AND header_id=:doId FOR UPDATE")
                    .setParameter("detailId", detailId).setParameter("doId", id).getResultList();
            if (rows.isEmpty()) throw new IllegalStateException("Baris DO " + detailId + " tidak ditemukan.");
            Object[] row = rows.get(0);
            Long itemId = ((Number) row[0]).longValue();
            Long uomId = ((Number) row[1]).longValue();
            BigDecimal doQty = new BigDecimal(row[2].toString());
            BigDecimal postedQty = new BigDecimal(row[3].toString());
            BigDecimal remaining = doQty.subtract(postedQty);
            if (qty.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("Qty GI harus lebih besar dari nol.");
            if (qty.compareTo(remaining) > 0) {
                throw new IllegalStateException("Qty GI melebihi sisa DO untuk detail " + detailId + ". Sisa: " + remaining);
            }
            validateItemAndUom(tenantId, compId, itemId, uomId);
            BigDecimal qtyBase = stockValuationService.convertLineToBase(itemId, uomId, qty, BigDecimal.ZERO)[0];
            if (qtyBase.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("Konversi UOM menghasilkan qty tidak valid untuk detail " + detailId + ". Pastikan UoM Set item terkonfigurasi.");
            }
            detailData.put(detailId, new Object[]{itemId, uomId, qty, qtyBase});
            stockRequired.merge(itemId, qtyBase, BigDecimal::add);
        }

        // Lock each stock row once and validate the aggregated requirement per item.
        for (Map.Entry<Long, BigDecimal> entry : stockRequired.entrySet()) {
            BigDecimal available = lockStockBalance(tenantId, entry.getKey(), warehouseId);
            if (available.compareTo(entry.getValue()) < 0) {
                throw new IllegalStateException("Stok tidak cukup untuk Item ID " + entry.getKey() +
                        ". Tersedia: " + available + ", dibutuhkan: " + entry.getValue());
            }
        }

        Long sequence = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM inv.transaction_header WHERE source_do_id=:doId AND doc_type='OUT'")
                .setParameter("doId", id).getSingleResult()).longValue() + 1L;
        String giDocNo = String.format("GI-%d-%04d", id, sequence);
        Object giIdRaw = em.createNativeQuery(
                "INSERT INTO inv.transaction_header " +
                "(tenant_id, company_id, doc_type, doc_no, doc_date, warehouse_id, remarks, status, source_do_id, idempotency_key) " +
                "VALUES (:tenantId, :compId, 'OUT', :docNo, :docDate, :whId, :remarks, 'POSTED', :doId, :key) RETURNING id")
                .setParameter("tenantId", tenantId).setParameter("compId", compId)
                .setParameter("docNo", giDocNo).setParameter("docDate", docDate)
                .setParameter("whId", warehouseId).setParameter("remarks", "Goods Issue untuk DO " + docNo)
                .setParameter("doId", id).setParameter("key", idempotencyKey).getSingleResult();
        Long giId = ((Number) giIdRaw).longValue();

        for (Map.Entry<Long, Object[]> entry : detailData.entrySet()) {
            Long doDetailId = entry.getKey();
            Long itemId = ((Number) entry.getValue()[0]).longValue();
            Long uomId = ((Number) entry.getValue()[1]).longValue();
            BigDecimal qty = (BigDecimal) entry.getValue()[2];
            BigDecimal qtyBase = (BigDecimal) entry.getValue()[3];

            stockValuationService.stockOutForTenant(tenantId, itemId, warehouseId, qtyBase, docDate,
                    "DO_GI", giId, giDocNo, "Goods Issue " + giDocNo + " dari DO " + docNo);
            Long movementId = latestMovementId(tenantId, "DO_GI", giId, giDocNo, itemId);
            em.createNativeQuery(
                    "INSERT INTO inv.transaction_detail " +
                    "(header_id, item_id, uom_id, qty, source_do_detail_id, stock_movement_id) " +
                    "VALUES (:headerId, :itemId, :uomId, :qty, :doDetailId, :movementId)")
                    .setParameter("headerId", giId).setParameter("itemId", itemId)
                    .setParameter("uomId", uomId).setParameter("qty", qty)
                    .setParameter("doDetailId", doDetailId).setParameter("movementId", movementId)
                    .executeUpdate();
            em.createNativeQuery(
                    "UPDATE sal.delivery_order_dtl SET gi_posted_qty=gi_posted_qty+:qty " +
                    "WHERE id=:detailId AND header_id=:doId AND gi_posted_qty+:qty<=qty")
                    .setParameter("qty", qty).setParameter("detailId", doDetailId).setParameter("doId", id)
                    .executeUpdate();
        }

        refreshGoodsIssueStatus(id);
        Long soId = h[2] == null ? null : ((Number) h[2]).longValue();
        if (soId != null) updateSOStatus(soId);
        return giDocNo;
    }

    /** Full-document reversal. The original OUT remains POSTED and an IN is appended. */
    @Transactional
    public String reverseGoodsIssue(Long doId, Long compId, Long giHeaderId) {
        Object[] h = lockHeader(doId, compId);
        Long tenantId = ((Number) h[3]).longValue();
        LocalDate docDate = toLocalDate(h[4]);
        Long warehouseId = h[5] == null ? null : ((Number) h[5]).longValue();
        String doNo = String.valueOf(h[1]);
        String doStatus = String.valueOf(h[0]);

        @SuppressWarnings("unchecked")
        List<Object[]> oldReversals = em.createNativeQuery(
                "SELECT id, doc_no FROM inv.transaction_header WHERE reversal_of_header_id=:giId AND doc_type='IN'")
                .setParameter("giId", giHeaderId).getResultList();
        if (!oldReversals.isEmpty()) return String.valueOf(oldReversals.get(0)[1]);
        if ("CLOSED".equals(doStatus) || "DELIVERED".equals(doStatus) || "DELIVERED".equals(h[7])) {
            throw new IllegalStateException("DO yang sudah Delivered/Closed tidak dapat di-reversal sebelum POD dikoreksi.");
        }

        @SuppressWarnings("unchecked")
        List<Object[]> originalRows = em.createNativeQuery(
                "SELECT id, doc_no FROM inv.transaction_header " +
                "WHERE id=:giId AND source_do_id=:doId AND company_id=:compId AND doc_type='OUT' AND status='POSTED' FOR UPDATE")
                .setParameter("giId", giHeaderId).setParameter("doId", doId).setParameter("compId", compId)
                .getResultList();
        if (originalRows.isEmpty()) throw new IllegalStateException("Goods Issue asal tidak ditemukan atau bukan milik DO ini.");
        String originalDocNo = String.valueOf(originalRows.get(0)[1]);

        @SuppressWarnings("unchecked")
        List<Object[]> lines = em.createNativeQuery(
                "SELECT id, source_do_detail_id, item_id, uom_id, qty, stock_movement_id " +
                "FROM inv.transaction_detail WHERE header_id=:giId ORDER BY id")
                .setParameter("giId", giHeaderId).getResultList();
        if (lines.isEmpty()) throw new IllegalStateException("Goods Issue tidak memiliki detail untuk dibalik.");

        LocalDate reversalDate = LocalDate.now();
        ensureInventoryPeriodOpen(tenantId, compId, reversalDate);
        if (warehouseId == null) throw new IllegalStateException("Warehouse DO tidak ditemukan.");
        String reversalDocNo = "GIR-" + giHeaderId;
        Object reversalIdRaw = em.createNativeQuery(
                "INSERT INTO inv.transaction_header " +
                "(tenant_id, company_id, doc_type, doc_no, doc_date, warehouse_id, remarks, status, source_do_id, reversal_of_header_id) " +
                "VALUES (:tenantId, :compId, 'IN', :docNo, :docDate, :whId, :remarks, 'POSTED', :doId, :giId) RETURNING id")
                .setParameter("tenantId", tenantId).setParameter("compId", compId)
                .setParameter("docNo", reversalDocNo).setParameter("docDate", reversalDate)
                .setParameter("whId", warehouseId)
                .setParameter("remarks", "Reversal Goods Issue " + originalDocNo + " untuk DO " + doNo)
                .setParameter("doId", doId).setParameter("giId", giHeaderId).getSingleResult();
        Long reversalId = ((Number) reversalIdRaw).longValue();

        for (Object[] line : lines) {
            Long originalDetailId = ((Number) line[0]).longValue();
            Long doDetailId = line[1] == null ? null : ((Number) line[1]).longValue();
            Long itemId = ((Number) line[2]).longValue();
            Long uomId = ((Number) line[3]).longValue();
            BigDecimal qty = new BigDecimal(line[4].toString());
            Long movementId = line[5] == null ? null : ((Number) line[5]).longValue();
            if (doDetailId == null || movementId == null) {
                throw new IllegalStateException("Detail GI " + originalDetailId + " tidak memiliki referensi DO/stock movement.");
            }
            // Konversi qty baris ke satuan dasar untuk pengembalian stok
            BigDecimal qtyBase = stockValuationService.convertLineToBase(itemId, uomId, qty, BigDecimal.ZERO)[0];
            if (qtyBase.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("Konversi UOM menghasilkan qty tidak valid untuk reversal detail " + originalDetailId + ".");
            }
            @SuppressWarnings("unchecked")
            List<Object> unitCostRows = em.createNativeQuery(
                    "SELECT unit_cost FROM inv.stock_movement WHERE id=:movementId AND tenant_id=:tenantId " +
                    "AND reference_id=:giId AND reference_type='DO_GI' AND direction='OUT'")
                    .setParameter("movementId", movementId).setParameter("tenantId", tenantId)
                    .setParameter("giId", giHeaderId).getResultList();
            if (unitCostRows.isEmpty()) {
                throw new IllegalStateException("Stock movement asal tidak ditemukan untuk detail " + originalDetailId + ".");
            }
            BigDecimal unitCost = new BigDecimal(unitCostRows.get(0).toString());

            lockStockBalance(tenantId, itemId, warehouseId);
            stockValuationService.stockInForTenant(tenantId, itemId, warehouseId, qtyBase, unitCost, reversalDate,
                    "DO_GI_REVERSAL", reversalId, reversalDocNo,
                    "Reversal " + originalDocNo + " untuk DO " + doNo);
            Long reversalMovementId = latestMovementId(tenantId, "DO_GI_REVERSAL", reversalId, reversalDocNo, itemId);

            em.createNativeQuery(
                    "INSERT INTO inv.transaction_detail " +
                    "(header_id, item_id, uom_id, qty, source_do_detail_id, reversal_of_detail_id, stock_movement_id) " +
                    "VALUES (:headerId, :itemId, :uomId, :qty, :doDetailId, :originalDetailId, :movementId)")
                    .setParameter("headerId", reversalId).setParameter("itemId", itemId)
                    .setParameter("uomId", uomId).setParameter("qty", qty)
                    .setParameter("doDetailId", doDetailId).setParameter("originalDetailId", originalDetailId)
                    .setParameter("movementId", reversalMovementId).executeUpdate();

            int changed = em.createNativeQuery(
                    "UPDATE sal.delivery_order_dtl SET gi_posted_qty=gi_posted_qty-:qty " +
                    "WHERE id=:doDetailId AND header_id=:doId AND gi_posted_qty>=:qty")
                    .setParameter("qty", qty).setParameter("doDetailId", doDetailId).setParameter("doId", doId)
                    .executeUpdate();
            if (changed == 0) throw new IllegalStateException("Qty posted DO tidak cukup untuk reversal detail " + doDetailId + ".");
        }

        refreshGoodsIssueStatus(doId);
        Long soId = h[2] == null ? null : ((Number) h[2]).longValue();
        if (soId != null) updateSOStatus(soId);
        return reversalDocNo;
    }

    @Transactional
    public String markDelivered(Long id, Long compId, String podReference, String podRemarks, String deliveredBy) {
        if (deliveredBy == null || deliveredBy.isBlank()) {
            throw new IllegalStateException("Username petugas Delivered tidak ditemukan.");
        }

        String actor = deliveredBy.trim();

        if (actor.length() > 100) {
            throw new IllegalArgumentException("Username petugas melebihi 100 karakter.");
        }
        Object[] h = lockHeader(id, compId);
        if (!"SHIPPED".equals(h[0]) || !"POSTED".equals(h[6])) {
            throw new IllegalStateException("POD hanya dapat dicatat setelah seluruh kuantitas Goods Issue terposting.");
        }
        em.createNativeQuery(
                "UPDATE sal.delivery_order_hdr SET status='DELIVERED', pod_status='DELIVERED', " +
                "delivered_at=NOW(), delivered_by=:deliveredBy, pod_reference=:reference, pod_remarks=:remarks WHERE id=:id")
                .setParameter("deliveredBy", actor).setParameter("reference", podReference).setParameter("remarks", podRemarks)
                .setParameter("id", id).executeUpdate();
        Long soId = h[2] == null ? null : ((Number) h[2]).longValue();
        if (soId != null) updateSOStatus(soId);
        return String.valueOf(h[1]);
    }

    @Transactional
    public String closeDo(Long id, Long compId) {
        Object[] h = lockHeader(id, compId);
        if (!"DELIVERED".equals(h[0]) || !"DELIVERED".equals(h[7])) {
            throw new IllegalStateException("DO hanya dapat ditutup setelah Delivered/POD.");
        }
        em.createNativeQuery("UPDATE sal.delivery_order_hdr SET status='CLOSED' WHERE id=:id")
                .setParameter("id", id).executeUpdate();
        return String.valueOf(h[1]);
    }

    @Transactional
    public String voidDo(Long id, Long compId) {
        Object[] h = lockHeader(id, compId);
        String status = String.valueOf(h[0]);
        if ("VOID".equals(status) || "CANCELLED".equals(status)) return String.valueOf(h[1]);
        // DO yang sudah memiliki Sales Invoice aktif tidak boleh dibatalkan.
        Long activeInvoiceCount = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM sal.sales_invoice_hdr " +
                "WHERE do_id=:doId AND status IS DISTINCT FROM 'VOID'")
                .setParameter("doId", id).getSingleResult()).longValue();

        if (activeInvoiceCount > 0) {
            throw new IllegalStateException(
                "DO sudah memiliki Sales Invoice aktif. " +
                "Lakukan proses VOID Sales Invoice melalui prosedur resmi terlebih dahulu.");
        }
        if (!List.of("DRAFT", "OPEN", "IN_PICKING", "READY_TO_SHIP", "CONFIRMED").contains(status)) {
            throw new IllegalStateException("DO yang sudah Goods Issue tidak dapat dibatalkan. Lakukan reversal terlebih dahulu.");
        }
        if (!hasNoNetGoodsIssue(id)) throw new IllegalStateException("DO memiliki kuantitas GI posted. Lakukan reversal dahulu.");
        em.createNativeQuery("UPDATE sal.delivery_order_hdr SET status='CANCELLED' WHERE id=:id")
                .setParameter("id", id).executeUpdate();
        Long soId = h[2] == null ? null : ((Number) h[2]).longValue();
        if (soId != null) updateSOStatus(soId);
        return String.valueOf(h[1]);
    }

    @Transactional
    public void delete(Long id, Long compId) {
        Object[] h = lockHeader(id, compId);
        if (!"DRAFT".equals(h[0])) throw new IllegalStateException("Hanya DO berstatus DRAFT yang bisa dihapus.");
        if (!hasNoNetGoodsIssue(id)) throw new IllegalStateException("DO dengan Goods Issue tidak dapat dihapus.");
        em.createNativeQuery("DELETE FROM sal.delivery_order_dtl WHERE header_id=:id").setParameter("id", id).executeUpdate();
        em.createNativeQuery("DELETE FROM sal.delivery_order_hdr WHERE id=:id AND company_id=:compId")
                .setParameter("id", id).setParameter("compId", compId).executeUpdate();
    }

    private Map<Long, BigDecimal> normalizeLines(List<Long> ids, List<BigDecimal> quantities) {
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            Long detailId = ids.get(i);
            BigDecimal qty = quantities.get(i);
            if (detailId == null || qty == null) throw new IllegalArgumentException("Detail ID/qty tidak boleh kosong.");
            if (qty.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("Qty GI tidak boleh negatif.");
            if (qty.compareTo(BigDecimal.ZERO) == 0) continue;
            result.merge(detailId, qty, BigDecimal::add);
        }
        return result;
    }

    private Map<Long, BigDecimal> getTransactionDoLines(Long headerId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT source_do_detail_id, qty FROM inv.transaction_detail WHERE header_id=:headerId")
                .setParameter("headerId", headerId).getResultList();
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            if (row[0] != null) result.merge(((Number) row[0]).longValue(), new BigDecimal(row[1].toString()), BigDecimal::add);
        }
        return result;
    }

    private boolean sameQuantities(Map<Long, BigDecimal> a, Map<Long, BigDecimal> b) {
        if (!a.keySet().equals(b.keySet())) return false;
        for (Long key : a.keySet()) if (a.get(key).compareTo(b.get(key)) != 0) return false;
        return true;
    }

    private Long latestMovementId(Long tenantId, String refType, Long refId, String refNo, Long itemId) {
        @SuppressWarnings("unchecked")
        List<Object> rows = em.createNativeQuery(
                "SELECT id FROM inv.stock_movement WHERE tenant_id=:tenantId AND reference_type=:refType " +
                "AND reference_id=:refId AND reference_no=:refNo AND item_id=:itemId " +
                "ORDER BY id DESC LIMIT 1")
                .setParameter("tenantId", tenantId).setParameter("refType", refType)
                .setParameter("refId", refId).setParameter("refNo", refNo).setParameter("itemId", itemId)
                .getResultList();
        if (rows.isEmpty()) throw new IllegalStateException("Stock movement gagal dibuat untuk item " + itemId + ".");
        return ((Number) rows.get(0)).longValue();
    }

    private void refreshGoodsIssueStatus(Long doId) {
        Object[] totals = (Object[]) em.createNativeQuery(
                "SELECT COALESCE(SUM(qty),0), COALESCE(SUM(gi_posted_qty),0) " +
                "FROM sal.delivery_order_dtl WHERE header_id=:id")
                .setParameter("id", doId).getSingleResult();
        BigDecimal ordered = new BigDecimal(totals[0].toString());
        BigDecimal posted = new BigDecimal(totals[1].toString());
        boolean hasReversal = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM inv.transaction_header WHERE source_do_id=:id AND reversal_of_header_id IS NOT NULL")
                .setParameter("id", doId).getSingleResult()).longValue() > 0;

        String giStatus;
        String doStatus;
        if (posted.compareTo(BigDecimal.ZERO) == 0) {
            giStatus = hasReversal ? "REVERSED" : "NOT_POSTED";
            doStatus = "READY_TO_SHIP";
        } else if (posted.compareTo(ordered) >= 0) {
            giStatus = "POSTED";
            doStatus = "SHIPPED";
        } else {
            giStatus = "PARTIAL";
            doStatus = "PARTIALLY_SHIPPED";
        }
        em.createNativeQuery("UPDATE sal.delivery_order_hdr SET goods_issue_status=:giStatus, status=:doStatus WHERE id=:id")
                .setParameter("giStatus", giStatus).setParameter("doStatus", doStatus).setParameter("id", doId).executeUpdate();
    }

    private void ensureInventoryPeriodOpen(Long tenantId, Long compId, LocalDate docDate) {
        String period = String.format("%04d-%02d", docDate.getYear(), docDate.getMonthValue());
        @SuppressWarnings("unchecked")
        List<Object> rows = em.createNativeQuery(
                "SELECT status FROM inv.inventory_period_control " +
                "WHERE tenant_id=:tenantId AND company_id=:compId AND period=:period FOR UPDATE")
                .setParameter("tenantId", tenantId).setParameter("compId", compId).setParameter("period", period)
                .getResultList();
        if (rows.isEmpty()) throw new IllegalStateException("Periode inventory " + period + " belum dibuka di inv.inventory_period_control.");
        if (!"OPEN".equals(rows.get(0))) throw new IllegalStateException("Periode inventory " + period + " sudah CLOSED.");
    }

    private void validateWarehouse(Long tenantId, Long compId, Long warehouseId) {
        Number count = (Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM md.warehouse WHERE id=:whId AND tenant_id=:tenantId " +
                "AND company_id=:compId AND status='ACTIVE'")
                .setParameter("whId", warehouseId).setParameter("tenantId", tenantId).setParameter("compId", compId)
                .getSingleResult();
        if (count.longValue() != 1) throw new IllegalStateException("Warehouse tidak aktif atau bukan milik company/tenant DO.");
    }

    private void validateItemAndUom(Long tenantId, Long compId, Long itemId, Long uomId) {
        @SuppressWarnings("unchecked")
        List<Object[]> itemRows = em.createNativeQuery(
                "SELECT tenant_id, company_id, status, deleted_at, item_type, base_uom_id " +
                "FROM md.item WHERE id=:itemId")
                .setParameter("itemId", itemId).getResultList();
        if (itemRows.isEmpty()) throw new IllegalStateException("Item " + itemId + " tidak ditemukan.");
        Object[] item = itemRows.get(0);
        if (((Number) item[0]).longValue() != tenantId || ((Number) item[1]).longValue() != compId ||
                !"ACTIVE".equals(item[2]) || item[3] != null || !"STOCK".equals(item[4])) {
            throw new IllegalStateException("Item " + itemId + " tidak aktif, bukan stock item, atau bukan milik company/tenant ini.");
        }
        if (((Number) item[5]).longValue() != uomId) {
            // Satuan baris berbeda dari satuan dasar item — qty dikonversi via convertLineToBase saat GI.
        }
        Number uomCount = (Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM md.uom WHERE id=:uomId AND tenant_id=:tenantId")
                .setParameter("uomId", uomId).setParameter("tenantId", tenantId).getSingleResult();
        if (uomCount.longValue() != 1) throw new IllegalStateException("UOM tidak ditemukan atau bukan milik tenant DO.");
    }

    private BigDecimal lockStockBalance(Long tenantId, Long itemId, Long warehouseId) {
        em.createNativeQuery(
                "INSERT INTO inv.stock_balance (tenant_id, item_id, warehouse_id, qty, avg_cost, total_value) " +
                "VALUES (:tenantId, :itemId, :warehouseId, 0, 0, 0) " +
                "ON CONFLICT (tenant_id, item_id, warehouse_id) DO NOTHING")
                .setParameter("tenantId", tenantId).setParameter("itemId", itemId).setParameter("warehouseId", warehouseId)
                .executeUpdate();
        Object qty = em.createNativeQuery(
                "SELECT qty FROM inv.stock_balance WHERE tenant_id=:tenantId AND item_id=:itemId " +
                "AND warehouse_id=:warehouseId FOR UPDATE")
                .setParameter("tenantId", tenantId).setParameter("itemId", itemId).setParameter("warehouseId", warehouseId)
                .getSingleResult();
        return new BigDecimal(qty.toString());
    }

    private boolean hasNoNetGoodsIssue(Long doId) {
        Object value = em.createNativeQuery(
                "SELECT COALESCE(SUM(gi_posted_qty),0) FROM sal.delivery_order_dtl WHERE header_id=:id")
                .setParameter("id", doId).getSingleResult();
        return new BigDecimal(value.toString()).compareTo(BigDecimal.ZERO) == 0;
    }

    /** lockHeader returns: status, doc_no, so_id, tenant_id, doc_date, warehouse_id, gi_status, pod_status */
    private Object[] lockHeader(Long id, Long compId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT status, doc_no, so_id, tenant_id, doc_date, warehouse_id, " +
                "COALESCE(goods_issue_status,'NOT_POSTED'), COALESCE(pod_status,'PENDING') " +
                "FROM sal.delivery_order_hdr WHERE id=:id AND company_id=:compId FOR UPDATE")
                .setParameter("id", id).setParameter("compId", compId).getResultList();
        if (rows.isEmpty()) throw new IllegalStateException("Delivery Order tidak ditemukan.");
        return rows.get(0);
    }

    private Long getCompanyTenant(Long compId) {
        @SuppressWarnings("unchecked")
        List<Object> rows = em.createNativeQuery("SELECT tenant_id FROM core.company WHERE id=:id AND status='ACTIVE'")
                .setParameter("id", compId).getResultList();
        if (rows.isEmpty()) throw new IllegalStateException("Company aktif tidak ditemukan.");
        return ((Number) rows.get(0)).longValue();
    }

    private Long getDefaultWarehouse(Long compId) {
        @SuppressWarnings("unchecked")
        List<Object> rows = em.createNativeQuery(
                "SELECT id FROM md.warehouse WHERE company_id=:compId AND status='ACTIVE' ORDER BY id LIMIT 1")
                .setParameter("compId", compId).getResultList();
        return rows.isEmpty() ? null : ((Number) rows.get(0)).longValue();
    }

    private void updateSOStatus(Long soId) {
        Object pendingRaw = em.createNativeQuery(
                "SELECT COUNT(*) FROM sal.sales_order_dtl sod " +
                "WHERE sod.header_id=:soId AND sod.qty > COALESCE(( " +
                " SELECT SUM(dod.gi_posted_qty) FROM sal.delivery_order_dtl dod " +
                " JOIN sal.delivery_order_hdr doh ON dod.header_id=doh.id " +
                " WHERE doh.so_id=:soId AND doh.status IN ('DELIVERED','CLOSED') " +
                " AND dod.item_id=sod.item_id),0)")
                .setParameter("soId", soId).getSingleResult();
        long pendingItems = ((Number) pendingRaw).longValue();
        String newStatus = pendingItems == 0 ? "DELIVERED" : "CONFIRMED";
        em.createNativeQuery("UPDATE sal.sales_order_hdr SET status=:status WHERE id=:soId AND status IN ('CONFIRMED','DELIVERED')")
                .setParameter("status", newStatus).setParameter("soId", soId).executeUpdate();
    }

    private String buildDocNo(Long compId) {
        String prefix = String.format("DO-%d-", LocalDate.now().getYear());
        int pos = prefix.length() + 1;
        Object result = em.createNativeQuery(
                "SELECT COALESCE(MAX(CASE WHEN SUBSTRING(doc_no FROM " + pos + ") ~ '^[0-9]+$' " +
                "THEN CAST(SUBSTRING(doc_no FROM " + pos + ") AS INTEGER) END),0) " +
                "FROM sal.delivery_order_hdr WHERE company_id=:compId AND doc_no LIKE :pattern")
                .setParameter("compId", compId).setParameter("pattern", prefix + "%").getSingleResult();
        long next = ((Number) result).longValue() + 1;
        return prefix + String.format("%04d", next);
    }

    private LocalDate toLocalDate(Object raw) {
        if (raw instanceof java.sql.Date d) return d.toLocalDate();
        if (raw instanceof LocalDate ld) return ld;
        return LocalDate.parse(raw.toString().substring(0, 10));
    }
}
