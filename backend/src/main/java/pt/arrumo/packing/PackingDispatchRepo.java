package pt.arrumo.packing;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PackingDispatchRepo extends JpaRepository<PackingDispatch, Long> {
    Optional<PackingDispatch> findFirstByTypeOrderByIdDesc(String type);
}
