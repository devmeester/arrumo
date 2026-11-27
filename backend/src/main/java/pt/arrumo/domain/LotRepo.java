package pt.arrumo.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LotRepo extends JpaRepository<Lot, Long> {
    Optional<Lot> findByCode(String code);
    boolean existsByProduct(Product product);

    @Query("select l from Lot l left join fetch l.product where l.holding is not null and l.status = pt.arrumo.domain.Lot.Status.IN_STOCK order by l.holding, l.productionDate asc nulls last, l.code")
    List<Lot> inLists();

    @Query("""
           select l from Lot l left join l.product p
           where (:status is null or l.status = :status)
             and (:noData = false or p is null)
             and (:q = '' or lower(l.code) like concat('%', :q, '%') or lower(p.description) like concat('%', :q, '%')
                  or p.code like concat('%', :q, '%') or lower(l.notes) like concat('%', :q, '%'))
           order by l.productionDate desc nulls last, l.code desc
           """)
    List<Lot> search(Lot.Status status, boolean noData, String q, org.springframework.data.domain.Pageable page);
}
