package com.conlact.conlact_backend.controller;
import com.conlact.conlact_backend.dto.order.*;
import com.conlact.conlact_backend.entity.enums.OrderStatus;
import com.conlact.conlact_backend.service.OrderReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/admin/pedidos")
public class AdminOrderReportController {
    private final OrderReportService service;
    @GetMapping("/operacion") public List<AdminOrderResponse> list(@RequestParam(required=false) String estado) {
        return service.list(parse(estado));
    }
    @GetMapping(value="/exportar-csv", produces="text/csv;charset=UTF-8")
    public ResponseEntity<String> csv(@RequestParam(required=false) String estado) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=pedidos-conlact.csv")
            .body(service.csv(parse(estado)));
    }
    private OrderStatus parse(String status) { return status == null || status.isBlank() ? null : OrderStatusUpdateRequest.parseStatus(status); }
}
