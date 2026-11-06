package com.conlact.conlact_backend;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
class OrderTransactionalFlowIntegrationTest extends AdminApiIntegrationSupport {
 private static final String VARIANT="ba000000-0000-0000-0000-000000000001";
 private Map<String,Object> payload(String method){return Map.of("cliente",Map.of("nombre","Cliente manual","cedula_ruc","1720000001","telefono","0998877665"),"metodo_entrega","pickup","metodo_pago",method,"items",List.of(Map.of("variante_id",VARIANT,"cantidad",5,"precio_unitario",0.01)));}
 private int stock(){return jdbc.queryForObject("SELECT stock FROM product_variants WHERE id=?::uuid",Integer.class,VARIANT);}
 @Test void transferUsesServerPriceAndManualApprovalConsumesReservationOnce() throws Exception {
   int before=stock();var created=request(HttpMethod.POST,"/api/pedidos",payload("transferencia"),null);
   assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);var order=json(created);String id=order.path("id").asText();
   assertThat(order.path("total").asDouble()).isEqualTo(12.5);assertThat(stock()).isEqualTo(before-5);
   assertThat(order.path("payphone_url").isNull()||order.path("payphone_url").isMissingNode()).isTrue();
   var status=Map.of("nuevo_estado","pagado","notas_administrativas","Transferencia verificada REF-123");
   assertThat(request(HttpMethod.PATCH,"/api/admin/pedidos/"+id+"/estado",status,null).getStatusCode().is4xxClientError()).isTrue();
   assertThat(request(HttpMethod.POST,"/api/webhooks/payphone",Map.of("order_id",id,"status","Approved"),null).getStatusCode().is4xxClientError()).isTrue();
   assertThat(request(HttpMethod.PATCH,"/api/admin/pedidos/"+id+"/estado",status,token).getStatusCode()).isEqualTo(HttpStatus.OK);
   assertThat(stock()).isEqualTo(before-5);
   assertThat(jdbc.queryForObject("SELECT status::text FROM inventory_reservations WHERE order_id=?::uuid",String.class,id)).isEqualTo("consumed");
   assertThat(request(HttpMethod.PATCH,"/api/admin/pedidos/"+id+"/estado",status,token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
   for(String next:List.of("preparando","enviado","completado")) assertThat(request(HttpMethod.PATCH,"/api/admin/pedidos/"+id+"/estado",Map.of("nuevo_estado",next),token).getStatusCode()).isEqualTo(HttpStatus.OK);
   var csv=request(HttpMethod.GET,"/api/admin/pedidos/exportar-csv",null,token);assertThat(csv.getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(csv.getBody()).contains("Cliente manual","completed");
 }
 @Test void cancellationRestoresReservedStockAndGatewayCannotCreateOrders() throws Exception {
   int before=stock();assertThat(request(HttpMethod.POST,"/api/pedidos",payload("payphone"),null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);assertThat(stock()).isEqualTo(before);
   var order=json(request(HttpMethod.POST,"/api/pedidos",payload("transferencia"),null));String id=order.path("id").asText();
   assertThat(request(HttpMethod.PATCH,"/api/admin/pedidos/"+id+"/estado",Map.of("nuevo_estado","cancelado"),token).getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(stock()).isEqualTo(before);
 }
}
