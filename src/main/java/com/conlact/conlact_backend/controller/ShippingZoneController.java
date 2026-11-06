package com.conlact.conlact_backend.controller;
import com.conlact.conlact_backend.repository.ShippingZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.math.BigDecimal;
@RestController @RequiredArgsConstructor
public class ShippingZoneController {
    private final ShippingZoneRepository repository;
    public record Zone(UUID id, String name, BigDecimal delivery_fee, String delivery_days) {}
    @GetMapping({"/api/shipping-zones","/api/zonas-envio"}) public List<Zone> list() {
        return repository.findByIsActiveTrue().stream().map(z -> new Zone(z.getId(),z.getName(),z.getDeliveryFee(),z.getDeliveryDaysText())).toList();
    }
}
