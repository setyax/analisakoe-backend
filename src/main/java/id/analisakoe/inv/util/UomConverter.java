package id.analisakoe.inv.util;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility untuk konversi UoM berdasarkan UoM Set.
 * Contoh: 2 Dus → berapa PCS?
 */
public class UomConverter {

    /**
     * Konversi qty dari satu UoM ke UoM lain dalam set yang sama.
     * 
     * @param em EntityManager
     * @param uomSetId ID UoM Set
     * @param fromUomId UoM asal
     * @param toUomId UoM tujuan
     * @param qty Qty dalam UoM asal
     * @return Qty dalam UoM tujuan
     */
    public static BigDecimal convert(EntityManager em, Long uomSetId,
                                      Long fromUomId, Long toUomId, BigDecimal qty) {
        if (fromUomId.equals(toUomId)) return qty;

        // Ambil semua UoM dalam set beserta parent & factor
        List<Object[]> rows = em.createNativeQuery(
            "SELECT uom_id, parent_uom_id, conversion_factor FROM md.uom_set_dtl WHERE uom_set_id = :setId")
            .setParameter("setId", uomSetId).getResultList();

        Map<Long, Long> parentMap = new HashMap<>();
        Map<Long, BigDecimal> factorMap = new HashMap<>();
        for (Object[] row : rows) {
            Long uomId = ((Number) row[0]).longValue();
            Long parentId = row[1] != null ? ((Number) row[1]).longValue() : null;
            BigDecimal factor = new BigDecimal(row[2].toString());
            parentMap.put(uomId, parentId);
            factorMap.put(uomId, factor);
        }

        // Konversi fromUomId → base
        BigDecimal qtyInBase = toBase(fromUomId, qty, parentMap, factorMap);
        
        // Konversi base → toUomId (reverse)
        BigDecimal result = fromBase(toUomId, qtyInBase, parentMap, factorMap);
        
        return result;
    }

    // Konversi ke base UoM (PCS)
    private static BigDecimal toBase(Long uomId, BigDecimal qty, 
                                     Map<Long, Long> parentMap, Map<Long, BigDecimal> factorMap) {
        BigDecimal result = qty;
        Long current = uomId;
        while (parentMap.get(current) != null) {
            result = result.multiply(factorMap.get(current));
            current = parentMap.get(current);
        }
        return result;
    }

    // Konversi dari base ke UoM tujuan
    private static BigDecimal fromBase(Long uomId, BigDecimal qtyInBase,
                                       Map<Long, Long> parentMap, Map<Long, BigDecimal> factorMap) {
        // Bangun chain dari uomId ke root
        java.util.List<Long> chain = new java.util.ArrayList<>();
        Long current = uomId;
        while (current != null) {
            chain.add(current);
            current = parentMap.get(current);
        }
        
        // chain = [DUS, BOX, PCS] untuk uomId=DUS
        // Walk dari root ke leaf, bagi dengan factor
        BigDecimal result = qtyInBase;
        for (int i = chain.size() - 1; i > 0; i--) {
            result = result.divide(factorMap.get(chain.get(i - 1)), 4, java.math.RoundingMode.HALF_UP);
        }
        return result;
    }
}