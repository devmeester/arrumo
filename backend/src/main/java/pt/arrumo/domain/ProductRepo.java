package pt.arrumo.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepo extends JpaRepository<Product, Long> {
    Optional<Product> findByCode(String code);
    Optional<Product> findByGtin(String gtin);
    List<Product> findAllByOrderByCode();
}
