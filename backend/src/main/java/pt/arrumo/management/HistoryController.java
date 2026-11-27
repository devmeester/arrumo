package pt.arrumo.management;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pt.arrumo.domain.AuditEntryRepo;
import pt.arrumo.domain.MovementRepo;
import pt.arrumo.map.Dtos.MovementDto;

@RestController
@RequestMapping("/api")
public class HistoryController {

    public record AuditEntryDto(long id, Instant occurredAt, String username, String ip, String action, String detail) {}

    private final MovementRepo movements;
    private final AuditEntryRepo audit;

    public HistoryController(MovementRepo movements, AuditEntryRepo audit) {
        this.movements = movements;
        this.audit = audit;
    }

    @GetMapping("/movements")
    @PreAuthorize("hasRole('VIEWER')")
    public List<MovementDto> movements(@RequestParam(defaultValue = "500") int limit) {
        return movements.findAllByOrderByIdDesc(PageRequest.of(0, Math.max(1, Math.min(limit, 5000))))
                .stream().map(MovementDto::from).toList();
    }

    @GetMapping("/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AuditEntryDto> audit(@RequestParam(defaultValue = "500") int limit) {
        return audit.findAllByOrderByIdDesc(PageRequest.of(0, Math.max(1, Math.min(limit, 5000)))).stream()
                .map(a -> new AuditEntryDto(a.getId(), a.getOccurredAt(), a.getUsername(), a.getIp(), a.getAction(), a.getDetail()))
                .toList();
    }
}
