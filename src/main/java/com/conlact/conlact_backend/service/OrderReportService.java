package com.conlact.conlact_backend.service;
import com.conlact.conlact_backend.dto.order.AdminOrderResponse;
import com.conlact.conlact_backend.entity.enums.OrderStatus;
import com.conlact.conlact_backend.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service @RequiredArgsConstructor
public class OrderReportService {
    private final OrderRepository repository;
    @Transactional(readOnly=true) public List<AdminOrderResponse> list(OrderStatus status) {
        return (status == null ? repository.findAllByOrderByCreatedAtDesc() : repository.findByStatusOrderByCreatedAtDesc(status))
            .stream().map(AdminOrderResponse::from).toList();
    }
    @Transactional(readOnly=true)
    public String csv(OrderStatus status) {
        StringBuilder csv = new StringBuilder("\uFEFFPedido,Fecha,Estado,Cliente,Telefono,Correo,Subtotal,Flete,Total,Notas\r\n");
        for (var o : list(status)) csv.append(String.join(",", cell(o.numero_pedido()),cell(o.fecha()),cell(o.estado()),cell(o.cliente()),
            cell(o.telefono()),cell(o.email()),cell(o.subtotal()),cell(o.flete()),cell(o.total()),cell(o.notas_administrativas()))).append("\r\n");
        return csv.toString();
    }
    // Quote all fields and neutralize spreadsheet formulas in user-supplied text.
    public static String cell(Object value) {
        String s = value == null ? "" : value.toString();
        if (s.stripLeading().matches("(?s)^[=+@-].*") || s.startsWith("\t") || s.startsWith("\r")) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
