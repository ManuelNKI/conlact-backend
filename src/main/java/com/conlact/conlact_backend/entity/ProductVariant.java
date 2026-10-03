package com.conlact.conlact_backend.entity;

import com.conlact.conlact_backend.exception.InsufficientStockException;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "product_variants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "sku", nullable = false, unique = true)
    private String sku;

    @Column(name = "presentation_name", nullable = false)
    private String presentationName;

    @Column(name = "weight_grams")
    private Integer weightGrams;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "stock", nullable = false)
    @Builder.Default
    private Integer stock = 0;

    @Column(name = "low_stock_threshold", nullable = false)
    @Builder.Default
    private Integer lowStockThreshold = 5;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // --- Métodos de Negocio: Stock Central y Validación de Disponibilidad ---

    /**
     * Comprueba si existe suficiente stock para cubrir la cantidad solicitada.
     */
    public boolean hasAvailableStock(int requestedQuantity) {
        return requestedQuantity > 0 && this.stock >= requestedQuantity;
    }

    /**
     * Determina si el nivel de stock actual se encuentra en o por debajo del umbral de alerta.
     */
    public boolean isLowStock() {
        return this.stock <= this.lowStockThreshold;
    }

    /**
     * Descuenta del stock centralizado la cantidad solicitada.
     * Si no hay suficiente stock, arroja InsufficientStockException.
     *
     * @param quantity cantidad positiva a descontar
     */
    public void deductStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("La cantidad a descontar debe ser mayor a cero");
        }
        if (!hasAvailableStock(quantity)) {
            throw new InsufficientStockException(
                    String.format("Stock insuficiente para la variante '%s' (SKU: %s). Disponible: %d, Solicitado: %d",
                            this.presentationName, this.sku, this.stock, quantity));
        }
        this.stock -= quantity;
    }

    /**
     * Incrementa el stock centralizado (reabastecimiento / devolución).
     *
     * @param quantity cantidad positiva a agregar
     */
    public void addStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("La cantidad a reabastecer debe ser mayor a cero");
        }
        this.stock += quantity;
    }

    /**
     * Ajuste absoluto directo del inventario central.
     *
     * @param newStock nuevo valor de stock (no negativo)
     */
    public void updateStock(int newStock) {
        if (newStock < 0) {
            throw new IllegalArgumentException("El stock no puede ser negativo");
        }
        this.stock = newStock;
    }
}
