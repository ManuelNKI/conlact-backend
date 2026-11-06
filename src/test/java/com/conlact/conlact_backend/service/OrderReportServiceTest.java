package com.conlact.conlact_backend.service;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class OrderReportServiceTest {
 @Test void escapesQuotesCommasAndLineBreaks() { assertThat(OrderReportService.cell("Pérez, \"Ana\"\nCliente")).isEqualTo("\"Pérez, \"\"Ana\"\"\nCliente\""); }
 @Test void neutralizesSpreadsheetFormulas() { for(String value:new String[]{"=1+1","+cmd","-cmd","@SUM(1)","  =1","\t=1"}) assertThat(OrderReportService.cell(value)).startsWith("\"'"); }
 @Test void handlesNullAndMoney() { assertThat(OrderReportService.cell(null)).isEqualTo("\"\""); assertThat(OrderReportService.cell(new java.math.BigDecimal("3.50"))).isEqualTo("\"3.50\""); }
}
