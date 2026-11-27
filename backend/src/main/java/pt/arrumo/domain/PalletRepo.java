package pt.arrumo.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PalletRepo extends JpaRepository<Pallet, Long> {
    Optional<Pallet> findBySscc(String sscc);
}
