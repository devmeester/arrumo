package pt.arrumo.domain;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovementRepo extends JpaRepository<Movement, Long> {
    List<Movement> findAllByOrderByIdDesc(Pageable page);
    List<Movement> findByAreaOrderByIdDesc(String area, Pageable page);
    List<Movement> findByLotCodeOrderByIdDesc(String lotCode);
}
