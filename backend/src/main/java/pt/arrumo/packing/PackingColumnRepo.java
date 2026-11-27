package pt.arrumo.packing;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PackingColumnRepo extends JpaRepository<PackingColumn, Long> {
    List<PackingColumn> findAllByOrderBySortOrder();
}
