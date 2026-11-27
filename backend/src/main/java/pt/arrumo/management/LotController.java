package pt.arrumo.management;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pt.arrumo.domain.Lot;
import pt.arrumo.domain.LotRepo;
import pt.arrumo.domain.MovementRepo;
import pt.arrumo.domain.Location;
import pt.arrumo.domain.LocationRepo;
import pt.arrumo.errors.ApiError;
import pt.arrumo.map.Dtos.LotDto;
import pt.arrumo.map.Dtos.MovementDto;

@RestController
@RequestMapping("/api/lots")
public class LotController {

    public record ImportRequest(@Size(max = 5000) List<LotService.ImportLine> lines) {}
    public record History(LotDto lot, List<MovementDto> movements) {}

    private final LotService service;
    private final LotRepo lots;
    private final LocationRepo locations;
    private final MovementRepo movements;

    public LotController(LotService service, LotRepo lots, LocationRepo locations, MovementRepo movements) {
        this.service = service;
        this.lots = lots;
        this.locations = locations;
        this.movements = movements;
    }

    /** Search for the management screen. status = IN_STOCK, SHIPPED or empty; noData = only the orange ones. */
    @GetMapping
    @PreAuthorize("hasRole('VIEWER')")
    @Transactional(readOnly = true)
    public List<LotDto> search(@RequestParam(defaultValue = "") String q,
                                   @RequestParam(required = false) Lot.Status status,
                                   @RequestParam(defaultValue = "false") boolean noData,
                                   @RequestParam(defaultValue = "200") int limit) {
        String term = q.strip().toLowerCase();
        if (term.length() > 60) throw ApiError.invalid("Pesquisa demasiado longa.");
        return lots.search(status, noData, term, PageRequest.of(0, Math.max(1, Math.min(limit, 1000)))).stream()
                .map(l -> LotDto.from(l, locations.findByLot(l).map(Location::getCode).orElse(null)))
                .toList();
    }

    /** Traceability: where the lot has been, who moved it and when. */
    @GetMapping("/{code}")
    @PreAuthorize("hasRole('VIEWER')")
    @Transactional(readOnly = true)
    public History history(@PathVariable String code) {
        Lot l = lots.findByCode(code).orElseThrow(() -> ApiError.notFound("Lote não encontrado."));
        return new History(LotDto.from(l, locations.findByLot(l).map(Location::getCode).orElse(null)),
                movements.findByLotCodeOrderByIdDesc(code).stream().map(MovementDto::from).toList());
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public LotDto create(@RequestBody LotService.LotRequest p) {
        return LotDto.from(service.create(p));
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasRole('MANAGER')")
    public LotDto update(@PathVariable String code, @RequestBody LotService.LotRequest p) {
        return LotDto.from(service.update(code, p));
    }

    @DeleteMapping("/{code}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable String code) {
        service.delete(code);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{code}/restore")
    @PreAuthorize("hasRole('MANAGER')")
    public LotDto restore(@PathVariable String code) {
        return LotDto.from(service.restore(code));
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('MANAGER')")
    public LotService.ImportSummary importLots(@Valid @RequestBody ImportRequest p, Principal me) {
        return service.importLots(p.lines(), me.getName());
    }

    /** Finished-goods stock from the ERP. Anyone who can change the FG map (logistics, manager, admin) can import it. */
    @PostMapping("/import-fg")
    @PreAuthorize("hasAuthority('ROLE_FG')")
    public LotService.FgImportSummary importFg(@Valid @RequestBody ImportRequest p, Principal me) {
        return service.importFg(p.lines(), me.getName());
    }
}
