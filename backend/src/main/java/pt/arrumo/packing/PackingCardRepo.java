package pt.arrumo.packing;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PackingCardRepo extends JpaRepository<PackingCard, Long> {
    List<PackingCard> findByArchivedFalseOrderByColumnIdAscSortOrderAsc();
    List<PackingCard> findByColumnIdAndArchivedFalseOrderBySortOrder(Long columnId);
    long countByColumnIdAndArchivedFalse(Long columnId);
}
