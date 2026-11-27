package pt.arrumo.packing;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.arrumo.errors.ApiError;
import pt.arrumo.security.Audit;

@Service
public class PackingService {

    public static final ZoneId LISBON = ZoneId.of("Europe/Lisbon");

    public record ColumnDto(long id, String name, int sortOrder, boolean dailyList, boolean reports) {
        static ColumnDto from(PackingColumn c) { return new ColumnDto(c.getId(), c.getName(), c.getSortOrder(), c.isDailyList(), c.isReports()); }
    }
    public record CardDto(long id, long columnId, int sortOrder, String title, String product, String quantity, String notes,
                            Instant reportedAt, String sendError, Instant updatedAt, long version) {
        static CardDto from(PackingCard c) {
            return new CardDto(c.getId(), c.getColumnId(), c.getSortOrder(), c.getTitle(), c.getProduct(), c.getQuantity(),
                    c.getNotes(), c.getReportedAt(), c.getSendError(), c.getUpdatedAt(), c.getVersion());
        }
    }
    public record DispatchDto(Instant occurredAt, boolean ok, String detail) {}
    public record Status(boolean dailyConfigured, boolean finishedConfigured, ZonedDateTime nextDispatch, DispatchDto lastDispatch) {}
    public record Board(List<ColumnDto> columns, List<CardDto> cards, Status status) {}
    public record CardRequest(Long columnId, String title, String product, String quantity, String notes, Long version) {}

    private final PackingColumnRepo columns;
    private final PackingCardRepo cards;
    private final PackingDispatchRepo dispatches;
    private final Notifier notifier;
    private final PackingProperties props;
    private final Webhooks webhooks;

    public PackingService(PackingColumnRepo columns, PackingCardRepo cards, PackingDispatchRepo dispatches,
                               Notifier notifier, PackingProperties props, Webhooks webhooks) {
        this.webhooks = webhooks;
        this.columns = columns;
        this.cards = cards;
        this.dispatches = dispatches;
        this.notifier = notifier;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public Board board() {
        ZonedDateTime next = CronExpression.parse(props.cron()).next(ZonedDateTime.now(LISBON));
        DispatchDto last = dispatches.findFirstByTypeOrderByIdDesc("DAILY")
                .map(e -> new DispatchDto(e.getOccurredAt(), e.isOk(), e.getDetail())).orElse(null);
        return new Board(
                columns.findAllByOrderBySortOrder().stream().map(ColumnDto::from).toList(),
                cards.findByArchivedFalseOrderByColumnIdAscSortOrderAsc().stream().map(CardDto::from).toList(),
                new Status(!blank(webhooks.daily()), !blank(webhooks.finished()), next, last));
    }

    // ---------------------------------------------------------------- cards

    @Transactional
    public CardDto create(CardRequest p) {
        PackingColumn col = column(p.columnId());
        PackingCard c = new PackingCard(col.getId(), (int) cards.countByColumnIdAndArchivedFalse(col.getId()), text(p.title(), 200, true));
        fill(c, p);
        return CardDto.from(cards.save(c));
    }

    @Transactional
    public CardDto edit(long id, CardRequest p) {
        PackingCard c = card(id, p.version());
        c.setTitle(text(p.title(), 200, true));
        fill(c, p);
        c.touch();
        cards.flush();
        return CardDto.from(c);
    }

    /** Moves the card to the given column and position. Returns true if it entered a reporting column and was not reported yet. */
    @Transactional
    public boolean move(long id, long columnId, int index, long version) {
        PackingCard c = card(id, version);
        PackingColumn destination = column(columnId);
        List<PackingCard> origin = new ArrayList<>(cards.findByColumnIdAndArchivedFalseOrderBySortOrder(c.getColumnId()));
        origin.remove(c);
        renumber(origin);
        List<PackingCard> list = destination.getId().equals(c.getColumnId())
                ? origin : new ArrayList<>(cards.findByColumnIdAndArchivedFalseOrderBySortOrder(destination.getId()));
        list.add(Math.max(0, Math.min(index, list.size())), c);
        c.setColumnId(destination.getId());
        renumber(list);
        c.touch();
        if (!destination.isReports()) c.setSendError(null); // left the reporting column: the warning no longer matters
        return destination.isReports() && c.getReportedAt() == null;
    }

    @Transactional
    public void archive(long id) {
        PackingCard c = cards.findById(id).orElseThrow(() -> ApiError.notFound("Cartão não encontrado."));
        c.setArchived(true);
    }

    /** Archives every card in a column (e.g. clearing the week's finished jobs). */
    @Transactional
    public int archiveColumn(long columnId) {
        List<PackingCard> l = cards.findByColumnIdAndArchivedFalseOrderBySortOrder(column(columnId).getId());
        l.forEach(c -> c.setArchived(true));
        return l.size();
    }

    @Transactional
    public void delete(long id) {
        PackingCard c = cards.findById(id).orElseThrow(() -> ApiError.notFound("Cartão não encontrado."));
        cards.delete(c);
        cards.flush();
        renumber(cards.findByColumnIdAndArchivedFalseOrderBySortOrder(c.getColumnId()));
    }

    // ---------------------------------------------------------------- colunas

    @Transactional
    public ColumnDto createColumn(String name) {
        return ColumnDto.from(columns.save(new PackingColumn(text(name, 60, true), (int) columns.count() + 1)));
    }

    @Transactional
    public ColumnDto editColumn(long id, String name, boolean dailyList, boolean reports) {
        PackingColumn c = column(id);
        c.setName(text(name, 60, true));
        c.setDailyList(dailyList);
        c.setReports(reports);
        return ColumnDto.from(c);
    }

    @Transactional
    public void moveColumn(long id, int index) {
        List<PackingColumn> l = new ArrayList<>(columns.findAllByOrderBySortOrder());
        PackingColumn c = column(id);
        l.remove(c);
        l.add(Math.max(0, Math.min(index, l.size())), c);
        for (int i = 0; i < l.size(); i++) l.get(i).setSortOrder(i + 1);
    }

    @Transactional
    public void deleteColumn(long id) {
        PackingColumn c = column(id);
        if (cards.countByColumnIdAndArchivedFalse(id) > 0) throw ApiError.conflict("A coluna tem cartões. Move-os ou arquiva-os primeiro.");
        if (cards.findAll().stream().anyMatch(x -> x.getColumnId().equals(id)))
            throw ApiError.conflict("A coluna tem cartões arquivados; não pode ser apagada. Muda-lhe o nome em vez disso.");
        columns.delete(c);
    }

    // ---------------------------------------------------------------- dispatches

    /** Reports a finished card on Teams. Saves the time if it worked, or the error to show on the card. */
    @Transactional
    public CardDto report(long id) {
        PackingCard c = cards.findById(id).orElseThrow(() -> ApiError.notFound("Cartão não encontrado."));
        String json = Messages.finished(line(c), Audit.currentUser(), ZonedDateTime.now(LISBON));
        String error = notifier.send(webhooks.finished(), json);
        if (error == null) {
            c.setReportedAt(Instant.now());
            c.setSendError(null);
        } else {
            c.setSendError(error);
        }
        dispatches.save(new PackingDispatch("FINISHED", error == null, c.getTitle() + (error == null ? "" : ": " + error)));
        cards.flush();
        return CardDto.from(c);
    }

    /** List of cards in the columns marked for the daily dispatch (usually the next-day backlog). */
    @Transactional
    public DispatchDto sendDailyList(String origin) {
        Map<Long, PackingColumn> byId = columns.findAll().stream().collect(Collectors.toMap(PackingColumn::getId, Function.identity()));
        List<Messages.Line> lines = cards.findByArchivedFalseOrderByColumnIdAscSortOrderAsc().stream()
                .filter(c -> byId.get(c.getColumnId()) != null && byId.get(c.getColumnId()).isDailyList())
                .sorted((a, b) -> byId.get(a.getColumnId()).getSortOrder() != byId.get(b.getColumnId()).getSortOrder()
                        ? Integer.compare(byId.get(a.getColumnId()).getSortOrder(), byId.get(b.getColumnId()).getSortOrder())
                        : Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .map(PackingService::line).toList();
        LocalDate day = Messages.nextWorkingDay(LocalDate.now(LISBON));
        String error = notifier.send(webhooks.daily(), Messages.daily(day, lines));
        String detail = origin + ": " + lines.size() + " embalamentos" + (error == null ? "" : ". " + error);
        PackingDispatch e = dispatches.save(new PackingDispatch("DAILY", error == null, detail));
        return new DispatchDto(e.getOccurredAt(), e.isOk(), e.getDetail());
    }

    // ----------------------------------------------------------------

    private static Messages.Line line(PackingCard c) {
        return new Messages.Line(c.getTitle(), c.getProduct(), c.getQuantity(), c.getNotes());
    }

    private void fill(PackingCard c, CardRequest p) {
        c.setProduct(text(p.product(), 250, false));
        c.setQuantity(text(p.quantity(), 60, false));
        c.setNotes(text(p.notes(), 2000, false));
    }

    private PackingColumn column(Long id) {
        if (id == null) throw ApiError.invalid("Escolhe a coluna.");
        return columns.findById(id).orElseThrow(() -> ApiError.notFound("Coluna não encontrada."));
    }

    private PackingCard card(long id, Long version) {
        PackingCard c = cards.findById(id).orElseThrow(() -> ApiError.notFound("Cartão não encontrado."));
        if (version != null && c.getVersion() != version)
            throw ApiError.conflict("Este cartão foi alterado noutro ecrã. O quadro foi atualizado; tenta de novo.");
        return c;
    }

    private static void renumber(List<PackingCard> l) {
        for (int i = 0; i < l.size(); i++) l.get(i).setSortOrder(i);
    }

    private static String text(String s, int max, boolean required) {
        String t = s == null ? "" : s.strip();
        if (required && t.isEmpty()) throw ApiError.invalid("Escreve um título.");
        if (t.length() > max) throw ApiError.invalid("Texto demasiado longo (máximo " + max + " caracteres).");
        return t;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
