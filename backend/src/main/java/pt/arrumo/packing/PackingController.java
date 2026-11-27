package pt.arrumo.packing;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pt.arrumo.packing.PackingService.*;

/** Packing board: administrators only. */
@RestController
@RequestMapping("/api/packing")
@PreAuthorize("hasRole('ADMIN')")
public class PackingController {

    public record MoveRequest(long columnId, int index, long version) {}
    public record ColumnRequest(String name, boolean dailyList, boolean reports) {}
    public record IndexRequest(int index) {}

    private final PackingService service;
    private final Webhooks webhooks;

    public PackingController(PackingService service, Webhooks webhooks) {
        this.service = service;
        this.webhooks = webhooks;
    }

    @GetMapping
    public Board board() {
        return service.board();
    }

    @PostMapping("/cards")
    public CardDto create(@RequestBody CardRequest p) {
        return service.create(p);
    }

    @PutMapping("/cards/{id}")
    public CardDto edit(@PathVariable long id, @RequestBody CardRequest p) {
        return service.edit(id, p);
    }

    /** When a card enters a reporting column (finished), it is sent to Teams straight away. */
    @PostMapping("/cards/{id}/move")
    public CardDto move(@PathVariable long id, @RequestBody MoveRequest p) {
        boolean report = service.move(id, p.columnId(), p.index(), p.version());
        return report ? service.report(id) : null;
    }

    @PostMapping("/cards/{id}/report")
    public CardDto report(@PathVariable long id) {
        return service.report(id);
    }

    @PostMapping("/cards/{id}/archive")
    public ResponseEntity<Void> archive(@PathVariable long id) {
        service.archive(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/cards/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/columns")
    public ColumnDto createColumn(@RequestBody ColumnRequest p) {
        return service.createColumn(p.name());
    }

    @PutMapping("/columns/{id}")
    public ColumnDto editColumn(@PathVariable long id, @RequestBody ColumnRequest p) {
        return service.editColumn(id, p.name(), p.dailyList(), p.reports());
    }

    @PostMapping("/columns/{id}/move")
    public ResponseEntity<Void> moveColumn(@PathVariable long id, @RequestBody IndexRequest p) {
        service.moveColumn(id, p.index());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/columns/{id}/archive")
    public int archiveColumn(@PathVariable long id) {
        return service.archiveColumn(id);
    }

    @DeleteMapping("/columns/{id}")
    public ResponseEntity<Void> deleteColumn(@PathVariable long id) {
        service.deleteColumn(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/send-now")
    public DispatchDto sendNow() {
        return service.sendDailyList("Enviado à mão");
    }

    public record ConfigRequest(@jakarta.validation.constraints.Size(max = 2000) String daily,
                               @jakarta.validation.constraints.Size(max = 2000) String finished) {}

    /** Status of the Power Automate URLs (never the whole URL). */
    @GetMapping("/config")
    public Webhooks.Status config() { return webhooks.status(); }

    /** null = leave as is; "" = delete; anything else = store encrypted. */
    @PutMapping("/config")
    public Webhooks.Status saveConfig(@jakarta.validation.Valid @RequestBody ConfigRequest p, java.security.Principal me) {
        if (p.daily() != null) webhooks.set(Webhooks.DAILY, p.daily(), me.getName());
        if (p.finished() != null) webhooks.set(Webhooks.FINISHED, p.finished(), me.getName());
        return webhooks.status();
    }
}
