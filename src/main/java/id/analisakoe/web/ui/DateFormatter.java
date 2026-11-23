package id.analisakoe.web.ui;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/** Helper Thymeleaf: ${@dateFmt.format(tanggal)} → "dd-MM-yyyy". */
@Component("dateFmt")
public class DateFormatter {

    private static final DateTimeFormatter OUT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public String format(Object o) {
        if (o == null) return "-";
        try {
            if (o instanceof LocalDate) return ((LocalDate) o).format(OUT);
            if (o instanceof LocalDateTime) return ((LocalDateTime) o).toLocalDate().format(OUT);
            if (o instanceof OffsetDateTime) return ((OffsetDateTime) o).toLocalDate().format(OUT);
            if (o instanceof java.sql.Timestamp) return ((java.sql.Timestamp) o).toLocalDateTime().toLocalDate().format(OUT);
            if (o instanceof java.sql.Date) return ((java.sql.Date) o).toLocalDate().format(OUT);
            if (o instanceof java.util.Date) return new java.sql.Date(((java.util.Date) o).getTime()).toLocalDate().format(OUT);

            String s = o.toString().trim();
            if (s.isEmpty()) return "-";
            // String ISO "2026-11-23" atau "2026-11-23T..." → format ulang
            if (s.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
                return LocalDate.parse(s.substring(0, 10)).format(OUT);
            }
            return s; // sudah bukan tanggal ISO — tampilkan apa adanya
        } catch (Exception e) {
            return o.toString();
        }
    }
}
