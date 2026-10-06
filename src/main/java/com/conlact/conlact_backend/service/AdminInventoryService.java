package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.variant.ProductVariantResponse;
import com.conlact.conlact_backend.dto.variant.StockOperationRequest;
import com.conlact.conlact_backend.dto.variant.StockSetRequest;
import com.conlact.conlact_backend.entity.AuditLog;
import com.conlact.conlact_backend.exception.ConflictException;
import com.conlact.conlact_backend.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class AdminInventoryService {
    private final ProductVariantService productVariantService;
    private final AuditLogRepository auditLogRepository;

    @Transactional
    public ProductVariantResponse deductStock(UUID id, StockOperationRequest request, UUID actorId) {
        return changeStock(id, request.getVersion(), request.getReason(), actorId, "STOCK_DEDUCT",
                () -> productVariantService.deductStock(id, request.getQuantity()));
    }

    @Transactional
    public ProductVariantResponse replenishStock(UUID id, StockOperationRequest request, UUID actorId) {
        return changeStock(id, request.getVersion(), request.getReason(), actorId, "STOCK_REPLENISH",
                () -> productVariantService.addStock(id, request.getQuantity()));
    }

    @Transactional
    public ProductVariantResponse setStock(UUID id, StockSetRequest request, UUID actorId) {
        return changeStock(id, request.getVersion(), request.getReason(), actorId, "STOCK_SET",
                () -> productVariantService.setStock(id, request.getStock()));
    }

    private ProductVariantResponse changeStock(UUID id, Long expectedVersion, String reason, UUID actorId,
                                               String action, Supplier<ProductVariantResponse> operation) {
        ProductVariantResponse before = productVariantService.getVariantById(id);
        if (expectedVersion != null && !expectedVersion.equals(before.getVersion())) {
            throw new ConflictException("El stock fue modificado. Actualice los datos antes de volver a guardar");
        }
        ProductVariantResponse after = operation.get();
        Map<String, Object> newData = new LinkedHashMap<>();
        newData.put("stock", after.getStock());
        newData.put("version", after.getVersion());
        newData.put("change", (long) after.getStock() - before.getStock());
        if (reason != null && !reason.isBlank()) {
            newData.put("reason", reason.trim());
        }
        // La auditoría comparte la transacción del stock: ambos se guardan o ambos se revierten.
        auditLogRepository.save(AuditLog.builder()
                .actorUserId(actorId)
                .entityName("product_variants")
                .entityId(id.toString())
                .action(action)
                .oldData(Map.of("stock", before.getStock(), "version", before.getVersion()))
                .newData(newData)
                .build());
        return after;
    }
}
