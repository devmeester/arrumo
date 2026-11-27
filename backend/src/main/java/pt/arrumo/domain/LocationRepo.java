package pt.arrumo.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface LocationRepo extends JpaRepository<Location, Long> {
    @Query("select p from Location p left join fetch p.lot l left join fetch l.product left join fetch p.pallet pl left join fetch pl.product order by p.zoneId, p.rowNo, p.colNo")
    List<Location> allWithContents();

    /** Locks the row until the transaction ends: two users can never change the same location at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Location p where p.code = :code")
    Optional<Location> lock(String code);

    Optional<Location> findByLot(Lot lot);

    Optional<Location> findByPallet(Pallet pallet);
}
