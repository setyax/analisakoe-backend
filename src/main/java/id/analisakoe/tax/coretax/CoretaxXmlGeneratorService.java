package id.analisakoe.tax.coretax;

import id.analisakoe.md.entity.Item;
import id.analisakoe.md.repository.ItemRepository;
import id.analisakoe.sal.entity.SalesInvoiceHdr;
import id.analisakoe.sal.entity.SalesInvoiceDtl;
import org.springframework.stereotype.Service;

@Service
public class CoretaxXmlGeneratorService {
    private final ItemRepository itemRepo;

    public CoretaxXmlGeneratorService(ItemRepository itemRepo) {
        this.itemRepo = itemRepo;
    }

    public String generate(SalesInvoiceHdr invoice) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<TaxInvoiceBulk>\n");
        xml.append("  <TIN>1234567890123456</TIN>\n");
        xml.append("  <ListOfTaxInvoice>\n");
        xml.append("    <TaxInvoice>\n");
        xml.append("      <TaxInvoiceDate>").append(invoice.getDocDate()).append("</TaxInvoiceDate>\n");
        xml.append("      <TaxInvoiceOpt>Normal</TaxInvoiceOpt>\n");
        xml.append("      <TrxCode>01</TrxCode>\n");
        xml.append("      <BuyerTin>").append(invoice.getCustomerId()).append("</BuyerTin>\n");
        xml.append("      <ListOfGoodService>\n");
        
        for (SalesInvoiceDtl dtl : invoice.getDetails()) {
            Item item = itemRepo.findById(dtl.getItemId()).orElse(null);
            String djpCode = (item != null && item.getDjpGoodsCode() != null) ? item.getDjpGoodsCode() : "000000";

            xml.append("        <GoodService>\n");
            xml.append("          <Opt>A</Opt>\n");
            xml.append("          <GoodServiceCode>").append(djpCode).append("</GoodServiceCode>\n");
            xml.append("          <GoodServiceName>").append(item != null ? item.getName() : "Item").append("</GoodServiceName>\n");
            xml.append("          <Unit>UM.0018</Unit>\n");
            xml.append("          <Price>").append(dtl.getPrice()).append("</Price>\n");
            xml.append("          <Qty>").append(dtl.getQty()).append("</Qty>\n");
            xml.append("          <Total>").append(dtl.getTotal()).append("</Total>\n");
            xml.append("          <VatBase>").append(dtl.getTotal()).append("</VatBase>\n");
            xml.append("          <VatRate>11</VatRate>\n");
            xml.append("          <Vat>").append(dtl.getTotal().multiply(new java.math.BigDecimal("0.11"))).append("</Vat>\n");
            xml.append("        </GoodService>\n");
        }
        
        xml.append("      </ListOfGoodService>\n");
        xml.append("    </TaxInvoice>\n");
        xml.append("  </ListOfTaxInvoice>\n");
        xml.append("</TaxInvoiceBulk>");
        return xml.toString();
    }
}