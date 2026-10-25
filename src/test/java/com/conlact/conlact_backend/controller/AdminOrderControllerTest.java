package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.order.OrderCreateResponse;
import com.conlact.conlact_backend.dto.order.OrderStatusUpdateRequest;
import com.conlact.conlact_backend.dto.order.OrderStatusUpdateResponse;
import com.conlact.conlact_backend.entity.enums.OrderStatus;
import com.conlact.conlact_backend.exception.BadRequestException;
import com.conlact.conlact_backend.exception.GlobalExceptionHandler;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import com.conlact.conlact_backend.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.conlact.conlact_backend.security.UserPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.junit.jupiter.api.AfterEach;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminOrderControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private AdminOrderController adminOrderController;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        UserPrincipal adminPrincipal = new UserPrincipal(
                UUID.fromString("ba000000-0000-0000-0000-000000000001"),
                "admin@conlact.org",
                "Admin General",
                "admin"
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(adminPrincipal, null, adminPrincipal.getAuthorities())
        );

        mockMvc = MockMvcBuilders.standaloneSetup(adminOrderController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("BE-33: PATCH /api/admin/pedidos/{id}/estado actualiza el estado y responde 200 OK")
    void shouldUpdateOrderStatusSuccessfullySpanishRoute() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderStatusUpdateResponse mockResponse = OrderStatusUpdateResponse.builder()
                .id(orderId)
                .orderNumber(10042L)
                .previousStatus("pending")
                .currentStatus("preparing")
                .administrativeNotes("Transferencia Banco Pichincha #458921 confirmada")
                .updatedAt(OffsetDateTime.now())
                .build();

        when(orderService.updateOrderStatus(eq(orderId), any(OrderStatusUpdateRequest.class), any()))
                .thenReturn(mockResponse);

        String jsonPayload = """
                {
                  "nuevo_estado": "preparing",
                  "notas_administrativas": "Transferencia Banco Pichincha #458921 confirmada"
                }
                """;

        mockMvc.perform(patch("/api/admin/pedidos/{id}/estado", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.numero_pedido").value(10042))
                .andExpect(jsonPath("$.estado_anterior").value("pending"))
                .andExpect(jsonPath("$.estado_actual").value("preparing"))
                .andExpect(jsonPath("$.notas_administrativas").value("Transferencia Banco Pichincha #458921 confirmada"));
    }

    @Test
    @DisplayName("BE-33: PATCH /api/admin/orders/{id}/status actualiza el estado mediante alias en inglés")
    void shouldUpdateOrderStatusSuccessfullyEnglishRoute() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderStatusUpdateResponse mockResponse = OrderStatusUpdateResponse.builder()
                .id(orderId)
                .orderNumber(10042L)
                .previousStatus("preparing")
                .currentStatus("shipped")
                .updatedAt(OffsetDateTime.now())
                .build();

        when(orderService.updateOrderStatus(eq(orderId), any(OrderStatusUpdateRequest.class), any()))
                .thenReturn(mockResponse);

        String jsonPayload = """
                {
                  "status": "shipped",
                  "notes": "Despachado con Servientrega guía #998811"
                }
                """;

        mockMvc.perform(patch("/api/admin/orders/{id}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.estado_anterior").value("preparing"))
                .andExpect(jsonPath("$.estado_actual").value("shipped"));
    }

    @Test
    @DisplayName("BE-33: Debe responder 400 Bad Request si 'nuevo_estado' se envía vacío")
    void shouldRejectBlankNewStatus() throws Exception {
        UUID orderId = UUID.randomUUID();
        String jsonPayload = """
                {
                  "nuevo_estado": "   "
                }
                """;

        mockMvc.perform(patch("/api/admin/pedidos/{id}/estado", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.newStatus").exists());
    }

    @Test
    @DisplayName("BE-33: Debe responder 404 Not Found si la orden no existe")
    void shouldReturnNotFoundWhenOrderDoesNotExist() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.updateOrderStatus(eq(orderId), any(), any()))
                .thenThrow(new ResourceNotFoundException("Pedido no encontrado con ID: " + orderId));

        String jsonPayload = """
                {
                  "nuevo_estado": "paid"
                }
                """;

        mockMvc.perform(patch("/api/admin/pedidos/{id}/estado", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Pedido no encontrado con ID: " + orderId));
    }

    @Test
    @DisplayName("BE-33: Debe responder 400 Bad Request ante una transición inválida")
    void shouldReturnBadRequestOnInvalidTransition() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.updateOrderStatus(eq(orderId), any(), any()))
                .thenThrow(new BadRequestException("Transición de estado no válida: no se puede cambiar de 'completed' a 'pending'"));

        String jsonPayload = """
                {
                  "nuevo_estado": "pending"
                }
                """;

        mockMvc.perform(patch("/api/admin/pedidos/{id}/estado", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Transición de estado no válida: no se puede cambiar de 'completed' a 'pending'"));
    }

    @Test
    @DisplayName("BE-33: GET /api/admin/pedidos lista órdenes con filtro opcional de estado")
    void shouldListAdminOrdersWithFilter() throws Exception {
        OrderCreateResponse order = OrderCreateResponse.builder()
                .id(UUID.randomUUID())
                .orderNumber(10042L)
                .subtotal(new BigDecimal("10.00"))
                .shippingCost(BigDecimal.ZERO)
                .total(new BigDecimal("10.00"))
                .status("pending")
                .paymentMethod("transferencia")
                .build();

        when(orderService.getAllOrders(eq(OrderStatus.pending))).thenReturn(List.of(order));

        mockMvc.perform(get("/api/admin/pedidos")
                        .param("estado", "pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].numero_pedido").value(10042))
                .andExpect(jsonPath("$[0].estado").value("pending"));
    }
}
