package pt.arrumo.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneRepo extends JpaRepository<Zone, String> {
    List<Zone> findAllByOrderBySortOrder();
}
