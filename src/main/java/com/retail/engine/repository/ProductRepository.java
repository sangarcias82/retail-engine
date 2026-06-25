package com.retail.engine.repository;

import com.retail.engine.model.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySku(String sku);

    Optional<Product> findBySku(String sku);

    /**
     * Partial match on name or SKU with escaped LIKE patterns ({@code ESCAPE '\'}).
     * Callers must escape {@code %}, {@code _}, and {@code \} in the search term before binding.
     */
    @Query("""
            SELECT p FROM Product p
            WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :namePattern, '%')) ESCAPE '\\'
               OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :skuPattern, '%')) ESCAPE '\\'
            """)
    Page<Product> searchByNameOrSku(@Param("namePattern") String namePattern,
                                    @Param("skuPattern") String skuPattern,
                                    Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);
}
