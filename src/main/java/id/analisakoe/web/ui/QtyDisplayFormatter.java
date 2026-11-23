package id.analisakoe.web.ui;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Formatter kuantitas untuk template Thymeleaf.
 * Menghilangkan nol desimal di belakang tanpa mengubah presisi angka.
 */
@Component("qtyDisplay")
public class QtyDisplayFormatter {

    public String format(Object value) {
        if (value == null) return "0";

        final BigDecimal number;

        if (value instanceof BigDecimal decimal) {
            number = decimal;
        } else if (value instanceof Number numeric) {
            number = new BigDecimal(numeric.toString());
        } else {
            String text = value.toString().trim();
            if (text.isEmpty()) return "0";

            try {
                number = new BigDecimal(text);
            } catch (NumberFormatException ex) {
                return text;
            }
        }

        BigDecimal normalized = number.stripTrailingZeros();

        if (normalized.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }

        DecimalFormatSymbols symbols =
            DecimalFormatSymbols.getInstance(Locale.forLanguageTag("id-ID"));

        DecimalFormat format =
            new DecimalFormat("#,##0.############################", symbols);

        format.setMaximumFractionDigits(28);
        format.setGroupingUsed(true);

        return format.format(normalized);
    }
}