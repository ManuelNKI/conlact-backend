package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.order.OrderCreateRequest;
import com.conlact.conlact_backend.dto.order.OrderCreateResponse;
import com.conlact.conlact_backend.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    private MockMvc mockMvc;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController orderController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(orderController).build();
    }

    private OrderCreateRequest createSampleOrderRequest() {
        return OrderCreateRequest.builder()
                .customer(OrderCreateRequest.CustomerDto.builder()
                        .name("Carlos López")
                        .taxId("1803456789")
                        .phone("+593998765432")
                        .email("carlos@gmail.com")
                        .address("Ficoa, Ambato")
                        .build())
                .deliveryMethod("delivery")
                .shippingZoneId(UUID.randomUUID())
                .paymentMethod("payphone")
                .clientNotes("Entregar en garita")
                .items(List.of(
                        OrderCreateRequest.OrderItemRequest.builder()
                                .variantId(UUID.randomUUID())
                                .quantity(2)
                                .unitPrice(new BigDecimal("3.25"))
                                .build()
                ))
                .build();
    }

    @Test
    @DisplayName("BE-29: POST /api/pedidos y alias /api/orders retornan 201 Created con respuesta de orden")
    void shouldCreateOrderSuccessfully() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderCreateResponse response = OrderCreateResponse.builder()
                .id(orderId)
                .orderNumber(10042L)
                .subtotal(new BigDecimal("13.00"))
                .shippingCost(new BigDecimal("2.50"))
                .total(new BigDecimal("15.50"))
                .status("pending")
                .paymentMethod("payphone")
                .payphoneUrl("https://pay.payphone.com.ec/pay?id=tx_9876543")
                .expiresAt(OffsetDateTime.now().plusMinutes(15))
                .build();

        when(orderService.createOrder(any())).thenReturn(response);

        OrderCreateRequest request = createSampleOrderRequest();

        mockMvc.perform(post("/api/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.numero_pedido").value(10042))
                .andExpect(jsonPath("$.subtotal").value(13.00))
                .andExpect(jsonPath("$.flete").value(2.50))
                .andExpect(jsonPath("$.total").value(15.50))
                .andExpect(jsonPath("$.estado").value("pending"))
                .andExpect(jsonPath("$.metodo_pago").value("payphone"))
                .andExpect(jsonPath("$.payphone_url").value("https://pay.payphone.com.ec/pay?id=tx_9876543"));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero_pedido").value(10042));
    }

    @Test
    @DisplayName("BE-29: GET /api/pedidos/track/{orderNumber} retorna 200 con el estado del pedido")
    void shouldTrackOrderByNumber() throws Exception {
        OrderCreateResponse response = OrderCreateResponse.builder()
                .id(UUID.randomUUID())
                .orderNumber(10042L)
                .total(new BigDecimal("15.50"))
                .status("pending")
                .paymentMethod("payphone")
                .build();

        when(orderService.getOrderByOrderNumber(10042L)).thenReturn(response);

        mockMvc.perform(get("/api/pedidos/track/10042"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero_pedido").value(10042))
                .andExpect(jsonPath("$.estado").value("pending"));
    }

    @Test
    @DisplayName("BE-29: GET /api/pedidos/{id} retorna 200 con detalle del pedido")
    void shouldGetOrderById() throws Exception {
        UUID id = UUID.randomUUID();
        OrderCreateResponse response = OrderCreateResponse.builder()
                .id(id)
                .orderNumber(10042L)
                .total(new BigDecimal("15.50"))
                .status("pending")
                .paymentMethod("payphone")
                .build();

        when(orderService.getOrderById(id)).thenReturn(response);

        mockMvc.perform(get("/api/pedidos/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.numero_pedido").value(10042));
    }

    @Test
    @DisplayName("BE-29: POST /api/pedidos con datos inválidos retorna 400 Bad Request")
    void shouldReturnBadRequestWhenInvalid() throws Exception {
        OrderCreateRequest invalidRequest = OrderCreateRequest.builder().build();

        mockMvc.perform(post("/api/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }
}
