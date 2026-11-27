package pt.arrumo.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ErpFgStockRepo extends JpaRepository<ErpFgStock, String> {
    @Query("select e from ErpFgStock e left join fetch e.product order by e.lot")
    List<ErpFgStock> all();
}
