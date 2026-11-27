package pt.arrumo.map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pt.arrumo.domain.Lot;
import pt.arrumo.events.EventChannel;
import pt.arrumo.map.Dtos.*;

@RestController
@RequestMapping("/api")
public class MapController {

    public record StoreRequest(@NotBlank @Size(max = 200) String input, long version) {}
    public record MoveRequest(@NotBlank String destination, long version, long versionTo) {}
    public record ReleaseRequest(@NotNull MapService.Destination destination, long version) {}
    public record NoteRequest(@NotBlank @Size(max = 120) String note, long version) {}
    public record VersionRequest(long version) {}
    public record ListRequest(@NotBlank @Size(max = 200) String input) {}
    public record ListRemoveRequest(@NotNull MapService.Destination destination) {}
    public record NotesRequest(@Size(max = 500) String notes) {}
    public record PalletRequest(String productCode, @Size(max = 30) String lot, BigDecimal weightKg, Integer boxes, LocalDate expiryDate, Long version) {}
    public record PalletNotesRequest(@Size(max = 500) String notes, Long version) {}
    public record ShipRequest(@NotNull @Size(max = 5000) List<@NotBlank @Size(max = 30) String> lots, @NotNull @Size(max = 5000) List<@NotNull Long> pallets) {}

    private final MapService service;
    private final EventChannel channel;

    public MapController(MapService service, EventChannel channel) {
        this.service = service;
        this.channel = channel;
    }

    /** Every role; logistics users only get the finished goods. */
    @GetMapping("/map")
    @PreAuthorize("isAuthenticated()")
    public MapDto map(Authentication a) {
        return service.snapshot(seesRawMaterial(a));
    }

    @GetMapping(path = "/events", produces = "text/event-stream")
    @PreAuthorize("isAuthenticated()")
    public SseEmitter events(Authentication a) {
        return channel.open(seesRawMaterial(a));
    }

    private static boolean seesRawMaterial(Authentication a) {
        return a.getAuthorities().stream().anyMatch(g -> "ROLE_VIEWER".equals(g.getAuthority()));
    }

    @PostMapping("/locations/{code}/store")
    @PreAuthorize("isAuthenticated()") // permission depends on the map (raw material or finished goods): see MapService
    public LocationDto store(@PathVariable String code, @Valid @RequestBody StoreRequest p, Principal me) {
        return service.store(code, p.input(), p.version(), me.getName());
    }

    @PostMapping("/locations/{code}/move")
    @PreAuthorize("isAuthenticated()") // permission depends on the map (raw material or finished goods): see MapService
    public List<LocationDto> move(@PathVariable String code, @Valid @RequestBody MoveRequest p, Principal me) {
        return service.move(code, p.destination(), p.version(), p.versionTo(), me.getName());
    }

    @PostMapping("/locations/{code}/release")
    @PreAuthorize("isAuthenticated()") // permission depends on the map (raw material or finished goods): see MapService
    public LocationDto release(@PathVariable String code, @Valid @RequestBody ReleaseRequest p, Principal me) {
        return service.release(code, p.destination(), p.version(), me.getName());
    }

    @PostMapping("/locations/{code}/note")
    @PreAuthorize("isAuthenticated()") // permission depends on the map (raw material or finished goods): see MapService
    public LocationDto addNote(@PathVariable String code, @Valid @RequestBody NoteRequest p, Principal me) {
        return service.addNote(code, p.note(), p.version(), me.getName());
    }

    @PostMapping("/locations/{code}/remove-note")
    @PreAuthorize("isAuthenticated()") // permission depends on the map (raw material or finished goods): see MapService
    public LocationDto removeNote(@PathVariable String code, @RequestBody VersionRequest p, Principal me) {
        return service.removeNote(code, p.version(), me.getName());
    }

    @PostMapping("/lists/{holding}")
    @PreAuthorize("hasRole('OPERATOR')")
    public LotDto toList(@PathVariable Lot.Holding holding, @Valid @RequestBody ListRequest p, Principal me) {
        return service.toList(holding, p.input(), me.getName());
    }

    /** Ships in bulk what is missing from the full ERP list. The service checks who may change what. */
    @PostMapping("/ship")
    @PreAuthorize("isAuthenticated()")
    public java.util.Map<String, Integer> shipAll(@Valid @RequestBody ShipRequest p, Principal me) {
        return java.util.Map.of("shipped", service.shipAll(p.lots(), p.pallets(), me.getName()));
    }

    @PostMapping("/lists/lot/{code}/remove")
    @PreAuthorize("hasRole('OPERATOR')")
    public void removeFromList(@PathVariable String code, @Valid @RequestBody ListRemoveRequest p, Principal me) {
        service.removeFromList(code, p.destination(), me.getName());
    }

    @PutMapping("/pallets/{id}/notes")
    @PreAuthorize("isAuthenticated()")
    public PalletDto palletNotes(@PathVariable long id, @Valid @RequestBody PalletNotesRequest p, Principal me) {
        return service.palletNotes(id, p.notes(), p.version(), me.getName());
    }

    @PutMapping("/pallets/{id}")
    @PreAuthorize("isAuthenticated()")
    public PalletDto correctPallet(@PathVariable long id, @Valid @RequestBody PalletRequest p, Principal me) {
        return service.correctPallet(id, p.productCode(), p.lot(), p.weightKg(), p.boxes(), p.expiryDate(), p.version(), me.getName());
    }

    @PutMapping("/lots/{code}/notes")
    @PreAuthorize("hasRole('OPERATOR')")
    public LotDto notes(@PathVariable String code, @Valid @RequestBody NotesRequest p, Principal me) {
        return service.notes(code, p.notes(), me.getName());
    }
}
