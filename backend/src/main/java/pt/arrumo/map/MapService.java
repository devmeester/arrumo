package pt.arrumo.map;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.arrumo.domain.*;
import pt.arrumo.errors.ApiError;
import pt.arrumo.map.Dtos.*;

/**
 * Every operation on the maps (RM = raw material, FG = finished goods). Each one:
 * 1) checks that the user may change that map;
 * 2) locks the locations involved (SELECT ... FOR UPDATE), always in the same order so there are no deadlocks;
 * 3) checks that the version the user saw is the current one (otherwise 409: someone changed it meanwhile);
 * 4) records the movement (append-only table);
 * 5) publishes a MapChange that is only sent to screens after COMMIT.
 */
@Service
public class MapService {

    public enum Destination { SHIPPED, CORRIDOR, LEFTOVERS }

    private final LocationRepo locations;
    private final LotRepo lots;
    private final PalletRepo pallets;
    private final ProductRepo products;
    private final ZoneRepo zones;
    private final MovementRepo movements;
    private final ErpFgStockRepo erpFgStock;
    private final ApplicationEventPublisher events;

    public MapService(LocationRepo locations, LotRepo lots, PalletRepo pallets, ProductRepo products, ZoneRepo zones,
                       MovementRepo movements, ErpFgStockRepo erpFgStock, ApplicationEventPublisher events) {
        this.erpFgStock = erpFgStock;
        this.locations = locations;
        this.lots = lots;
        this.pallets = pallets;
        this.products = products;
        this.zones = zones;
        this.movements = movements;
        this.events = events;
    }

    /** seesRawMaterial = false for logistics users: they only get the finished-goods map. */
    @Transactional(readOnly = true)
    public MapDto snapshot(boolean seesRawMaterial) {
        Function<String, Product> byGtin = byGtin();
        return new MapDto(
                zones.findAllByOrderBySortOrder().stream().filter(z -> seesRawMaterial || "fg".equals(z.getId())).map(ZoneDto::from).toList(),
                locations.allWithContents().stream().filter(p -> seesRawMaterial || p.isFinishedGoods()).map(p -> LocationDto.from(p, byGtin)).toList(),
                seesRawMaterial ? lots.inLists().stream().map(LotDto::from).toList() : List.of(),
                (seesRawMaterial ? movements.findAllByOrderByIdDesc(PageRequest.of(0, 40))
                       : movements.findByAreaOrderByIdDesc("FG", PageRequest.of(0, 40))).stream().map(MovementDto::from).toList(),
                erpFgStock.all().stream().map(ErpFgStockDto::from).toList());
    }

    /**
     * Stores in a free location whatever was typed or scanned.
     * Raw material: a lot code (an unknown one is kept "without data", orange). Finished goods: the pallet's GS1 label.
     * Any other text (e.g. "Casa do Bacalhau") becomes a note on the location.
     */
    @Transactional
    public LocationDto store(String locationCode, String input, long version, String who) {
        Location p = lock(locationCode);
        requirePermission(p.isFinishedGoods());
        checkVersion(p, version);
        if (!p.isFree()) throw ApiError.conflict(p.name() + " não está livre.");
        if (p.isFinishedGoods()) return storePallet(p, input, who);

        LotCode l;
        try {
            l = LotCode.parse(input);
        } catch (IllegalArgumentException e) {
            return note(p, input, who);
        }
        Lot lot = lots.findByCode(l.lot()).orElse(null);
        String note = "";
        if (lot == null) {
            lot = lots.save(new Lot(l.lot(), null, null));
            note = "lote novo, sem dados";
        } else {
            if (lot.getStatus() == Lot.Status.SHIPPED)
                throw ApiError.conflict("O lote " + lot.getCode() + " já saiu da câmara. Se voltou, um gestor tem de o repor.");
            Location current = locations.findByLot(lot).orElse(null);
            if (current != null) throw ApiError.conflict("O lote " + lot.getCode() + " já está em " + current.name() + ". Usa Mover.");
            if (lot.getHolding() != null) {
                note = "vindo de " + listName(lot.getHolding());
                lot.setHolding(null);
            }
        }
        p.setLot(lot);
        p.touch(who);
        return finish(who, List.of(p), true,
                new Movement(who, Movement.Type.STORE, lot.getCode(), null, p.getCode(), note)).getFirst();
    }

    private LocationDto storePallet(Location p, String input, String who) {
        String t = input == null ? "" : input.strip();
        Pallet pl;
        String note = "";
        if (Gs1.looksLikeGs1(t)) {
            Gs1 e;
            try {
                e = Gs1.parse(t);
            } catch (IllegalArgumentException ex) {
                throw ApiError.invalid(ex.getMessage());
            }
            pl = e.sscc() == null ? null : pallets.findBySscc(e.sscc()).orElse(null);
            if (pl == null) {
                pl = pallets.save(newPallet(e));
                note = "palete nova";
            } else {
                fillIn(pl, e);
            }
        } else {
            LotCode l;
            try {
                l = LotCode.parse(t);
            } catch (IllegalArgumentException e) {
                return note(p, input, who);
            }
            pl = pallets.save(new Pallet(null, l.lot(), null)); // lot typed by hand, no label: kept without data
            note = "palete nova, sem etiqueta";
        }
        if (pl.getStatus() == Lot.Status.SHIPPED) {
            pl.restore();
            note = "voltou ao armazém";
        }
        Pallet thisPallet = pl;
        locations.findByPallet(thisPallet).ifPresent(x -> {
            throw ApiError.conflict("A palete " + thisPallet.name() + " já está em " + x.name() + ". Usa Mover.");
        });
        p.setPallet(pl);
        p.touch(who);
        return finish(who, List.of(p), true,
                new Movement(who, Movement.Type.STORE, pl.name(), null, p.getCode(), note).inFinishedGoods()).getFirst();
    }

    @Transactional
    public List<LocationDto> move(String from, String to, long versionFrom, long versionTo, String who) {
        if (Objects.equals(from, to)) throw ApiError.invalid("Escolhe outra posição.");
        // always lock in the same (alphabetical) order so two crossing moves cannot block each other
        boolean inOrder = from.compareTo(to) < 0;
        Location a = lock(inOrder ? from : to), b = lock(inOrder ? to : from);
        Location origin = inOrder ? a : b, destination = inOrder ? b : a;
        requirePermission(origin.isFinishedGoods());
        if (origin.isFinishedGoods() != destination.isFinishedGoods())
            throw ApiError.invalid("Não dá para mover entre a C5 e o produto acabado.");
        checkVersion(origin, versionFrom);
        checkVersion(destination, versionTo);
        if (origin.getLot() == null && origin.getPallet() == null) throw ApiError.conflict(origin.name() + " já está vazia.");
        if (!destination.isFree()) throw ApiError.conflict(destination.name() + " já não está livre.");

        String name;
        if (origin.getLot() != null) {
            name = origin.getLot().getCode();
            destination.setLot(origin.getLot());
            origin.setLot(null);
        } else {
            name = origin.getPallet().name();
            destination.setPallet(origin.getPallet());
            origin.setPallet(null);
        }
        origin.touch(who);
        destination.touch(who);
        Movement m = new Movement(who, Movement.Type.MOVE, name, origin.getCode(), destination.getCode(), "");
        return finish(who, List.of(origin, destination), false, origin.isFinishedGoods() ? m.inFinishedGoods() : m);
    }

    @Transactional
    public LocationDto release(String code, Destination destination, long version, String who) {
        Location p = lock(code);
        requirePermission(p.isFinishedGoods());
        checkVersion(p, version);
        if (p.getPallet() != null) {
            Pallet pl = p.getPallet();
            // a pallet is never left "loose": it is either in a slot or shipped (use Move to change its slot)
            if (destination != Destination.SHIPPED)
                throw ApiError.invalid("Uma palete só sai do lugar quando é expedida. Para a mudar de lugar usa Mover.");
            p.setPallet(null);
            p.touch(who);
            pl.ship();
            return finish(who, List.of(p), true, new Movement(who, Movement.Type.SHIP, pl.name(), code, null, "").inFinishedGoods()).getFirst();
        }
        Lot lot = p.getLot();
        if (lot == null) throw ApiError.conflict(code + " já está vazia.");
        p.setLot(null);
        p.touch(who);
        Movement.Type type = Movement.Type.REMOVE;
        String to = null;
        switch (destination) {
            case SHIPPED -> { lot.ship(); type = Movement.Type.SHIP; }
            case CORRIDOR -> { lot.setHolding(Lot.Holding.CORRIDOR); to = "CORRIDOR"; }
            case LEFTOVERS -> { lot.setHolding(Lot.Holding.LEFTOVERS); to = "LEFTOVERS"; }
        }
        return finish(who, List.of(p), true, new Movement(who, type, lot.getCode(), code, to, "")).getFirst();
    }

    @Transactional
    public LocationDto addNote(String code, String note, long version, String who) {
        Location p = lock(code);
        requirePermission(p.isFinishedGoods());
        checkVersion(p, version);
        if (!p.isFree()) throw ApiError.conflict(code + " não está livre.");
        return note(p, note, who);
    }

    @Transactional
    public LocationDto removeNote(String code, long version, String who) {
        Location p = lock(code);
        requirePermission(p.isFinishedGoods());
        checkVersion(p, version);
        if (p.getNote() == null) throw ApiError.conflict(code + " não tem nota.");
        String before = p.getNote();
        p.setNote(null);
        p.touch(who);
        Movement m = new Movement(who, Movement.Type.REMOVE_NOTE, null, code, null, before);
        return finish(who, List.of(p), false, p.isFinishedGoods() ? m.inFinishedGoods() : m).getFirst();
    }

    /** Puts a lot in the corridor or leftovers list (no location; raw material only). */
    @Transactional
    public LotDto toList(Lot.Holding holding, String input, String who) {
        requirePermission(false);
        LotCode l = parse(input);
        Lot lot = lots.findByCode(l.lot()).orElse(null);
        if (lot == null) {
            lot = lots.save(new Lot(l.lot(), null, null));
        } else {
            if (lot.getStatus() == Lot.Status.SHIPPED)
                throw ApiError.conflict("O lote " + lot.getCode() + " já saiu da câmara.");
            locations.findByLot(lot).ifPresent(p -> {
                throw ApiError.conflict("O lote está em " + p.name() + ". Abre essa posição e usa Libertar > " + listName(holding) + ".");
            });
            if (lot.getHolding() == holding) throw ApiError.conflict("O lote já está em " + listName(holding) + ".");
        }
        String from = lot.getHolding() == null ? null : lot.getHolding().name();
        lot.setHolding(holding);
        finish(who, List.of(), true, new Movement(who, Movement.Type.MOVE, lot.getCode(), from, holding.name(), ""));
        return LotDto.from(lot);
    }

    @Transactional
    public void removeFromList(String lotCode, Destination destination, String who) {
        requirePermission(false);
        Lot lot = lots.findByCode(lotCode).orElseThrow(() -> ApiError.notFound("Lote não encontrado."));
        if (lot.getHolding() == null) throw ApiError.conflict("O lote já não está em nenhuma lista.");
        String from = lot.getHolding().name();
        Movement.Type type = Movement.Type.MOVE;
        String to = null;
        if (destination == Destination.SHIPPED) {
            lot.ship();
            type = Movement.Type.SHIP;
        } else {
            // raw material has no "unplaced" state: outside a location and outside leftovers means the corridor
            Lot.Holding l = destination == Destination.LEFTOVERS ? Lot.Holding.LEFTOVERS : Lot.Holding.CORRIDOR;
            if (l == lot.getHolding()) throw ApiError.conflict("O lote já está aí.");
            lot.setHolding(l);
            to = l.name();
        }
        finish(who, List.of(), true, new Movement(who, type, lot.getCode(), from, to, ""));
    }

    @Transactional
    public LotDto notes(String lotCode, String text, String who) {
        requirePermission(false);
        Lot lot = lots.findByCode(lotCode).orElseThrow(() -> ApiError.notFound("Lote não encontrado."));
        lot.setNotes(notesText(text));
        List<Location> where = locations.findByLot(lot).map(List::of).orElse(List.of());
        finish(who, where, true, null);
        return LotDto.from(lot);
    }

    // ---------------------------------------------------------------------------------------- pallets

    @Transactional
    public PalletDto palletNotes(long id, String text, Long version, String who) {
        requirePermission(true);
        Pallet pl = pallet(id, version);
        pl.setNotes(notesText(text));
        finishPallet(pl, who);
        return PalletDto.from(pl, byGtin());
    }

    /** Fill in or correct by hand (orange pallet "without data", or an incomplete label). */
    @Transactional
    public PalletDto correctPallet(long id, String productCode, String lot, BigDecimal weightKg, Integer boxes,
                                    LocalDate expiryDate, Long version, String who) {
        requirePermission(true);
        Pallet pl = pallet(id, version);
        if (productCode != null && !productCode.isBlank())
            pl.setProduct(products.findByCode(productCode.strip()).orElseThrow(() -> ApiError.invalid("O produto " + productCode + " não existe.")));
        if (lot != null && !lot.isBlank()) {
            if (lot.strip().length() > 30) throw ApiError.invalid("Lote demasiado longo.");
            pl.setLot(lot.strip().toUpperCase());
            if (pl.getProductionDate() == null) pl.setProductionDate(Lot.dateFromCode(pl.getLot()));
        }
        if (weightKg != null) {
            if (weightKg.signum() < 0 || weightKg.compareTo(new BigDecimal("100000")) > 0) throw ApiError.invalid("Peso fora do intervalo.");
            pl.setWeightKg(weightKg);
        }
        if (boxes != null) {
            if (boxes < 0) throw ApiError.invalid("Número de caixas inválido.");
            pl.setBoxes(boxes);
        }
        if (expiryDate != null) pl.setExpiryDate(expiryDate);
        finishPallet(pl, who);
        return PalletDto.from(pl, byGtin());
    }

    /** Loads the pallet and checks nobody changed it since the user saw it (otherwise 409). */
    private Pallet pallet(long id, Long seenVersion) {
        Pallet pl = pallets.findById(id).orElseThrow(() -> ApiError.notFound("Palete não encontrada."));
        if (seenVersion == null || pl.getVersion() != seenVersion)
            throw ApiError.conflict("Outra pessoa alterou esta palete agora mesmo. O mapa foi atualizado; confirma e tenta de novo.");
        return pl;
    }

    // ----------------------------------------------------------------------------------------------

    /** Checks the user may change this map: raw material needs OPERATOR; finished goods needs LOGISTICS/MANAGER/ADMIN. */
    private static void requirePermission(boolean isFinishedGoods) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        String needs = isFinishedGoods ? Role.EDITS_FG : "ROLE_OPERATOR";
        if (a == null || a.getAuthorities().stream().noneMatch(g -> needs.equals(g.getAuthority())))
            throw new AccessDeniedException("Not allowed to change this map.");
    }

    private LocationDto note(Location p, String text, String who) {
        String n = text == null ? "" : text.strip();
        if (n.isEmpty() || n.length() > 120) throw ApiError.invalid("Escreve um texto até 120 caracteres.");
        p.setNote(n);
        p.touch(who);
        Movement m = new Movement(who, Movement.Type.ADD_NOTE, null, null, p.getCode(), n);
        return finish(who, List.of(p), false, p.isFinishedGoods() ? m.inFinishedGoods() : m).getFirst();
    }

    private static Pallet newPallet(Gs1 e) {
        Pallet pl = new Pallet(e.sscc(), e.lot(), e.gtin());
        fillIn(pl, e);
        return pl;
    }

    /** Only fills in what the pallet does not have yet (never overwrites data corrected by hand). */
    private static void fillIn(Pallet pl, Gs1 e) {
        if (pl.getLot() == null && e.lot() != null) pl.setLot(e.lot());
        if (pl.getGtin() == null && e.gtin() != null) pl.setGtin(e.gtin());
        if (pl.getWeightKg() == null && e.weightKg() != null) pl.setWeightKg(e.weightKg());
        if (pl.getBoxes() == null && e.boxes() != null) pl.setBoxes(e.boxes());
        if (e.production() != null && pl.getProductionDate() == null) pl.setProductionDate(e.production());
        if (pl.getProductionDate() == null) pl.setProductionDate(Lot.dateFromCode(pl.getLot()));
        if (pl.getExpiryDate() == null && e.expiryDate() != null) pl.setExpiryDate(e.expiryDate());
    }

    private Function<String, Product> byGtin() {
        Map<String, Product> m = products.findAll().stream().filter(p -> p.getGtin() != null && p.isActive())
                .collect(Collectors.toMap(Product::getGtin, Function.identity(), (x, y) -> x));
        return m::get;
    }

    private void finishPallet(Pallet pl, String who) {
        List<Location> where = locations.findByPallet(pl).map(List::of).orElse(List.of());
        pallets.flush();
        Function<String, Product> pg = byGtin();
        events.publishEvent(new MapChange(who, where.stream().map(p -> LocationDto.from(p, pg)).toList(), true, null, false, "FG"));
    }

    private static String notesText(String text) {
        String t = text == null ? "" : text.strip();
        if (t.length() > 500) throw ApiError.invalid("A observação pode ter até 500 caracteres.");
        return t;
    }

    private LotCode parse(String input) {
        try {
            return LotCode.parse(input);
        } catch (IllegalArgumentException e) {
            throw ApiError.invalid(e.getMessage());
        }
    }

    /**
     * Ships in one go the lots and pallets that are missing from the full ERP list.
     * Anything that has already left or changed meanwhile is skipped. Returns how many were shipped.
     */
    @Transactional
    public int shipAll(List<String> codes, List<Long> palletIds, String who) {
        if (!codes.isEmpty()) requirePermission(false);
        if (!palletIds.isEmpty()) requirePermission(true);
        List<Lot> ls = codes.stream().distinct().map(c -> lots.findByCode(c.strip().toUpperCase(java.util.Locale.ROOT)).orElse(null))
                .filter(l -> l != null && l.getStatus() == Lot.Status.IN_STOCK).toList();
        List<Pallet> ps = palletIds.stream().distinct().map(id -> pallets.findById(id).orElse(null))
                .filter(pl -> pl != null && pl.getStatus() != Lot.Status.SHIPPED).toList();
        // lock locations in a fixed order, like in move
        java.util.TreeMap<String, Object> byLocation = new java.util.TreeMap<>();
        List<Lot> listOnly = new ArrayList<>();
        for (Lot l : ls) locations.findByLot(l).ifPresentOrElse(p -> byLocation.put(p.getCode(), l), () -> listOnly.add(l));
        for (Pallet pl : ps) locations.findByPallet(pl).ifPresent(p -> byLocation.put(p.getCode(), pl));
        List<Movement> movs = new ArrayList<>();
        for (var e : byLocation.entrySet()) {
            Location p = lock(e.getKey());
            if (e.getValue() instanceof Lot l && p.getLot() == l) {
                p.setLot(null); p.touch(who); l.ship();
                movs.add(new Movement(who, Movement.Type.SHIP, l.getCode(), p.getCode(), null, "não veio no stock do ERP"));
            } else if (e.getValue() instanceof Pallet pl && p.getPallet() == pl) {
                p.setPallet(null); p.touch(who); pl.ship();
                movs.add(new Movement(who, Movement.Type.SHIP, pl.name(), p.getCode(), null, "não veio no stock do ERP").inFinishedGoods());
            }
        }
        for (Lot l : listOnly) {
            if (l.getHolding() == null) continue;
            String from = l.getHolding().name();
            l.ship();
            movs.add(new Movement(who, Movement.Type.SHIP, l.getCode(), from, null, "não veio no stock do ERP"));
        }
        locations.flush();
        movements.saveAll(movs);
        if (!movs.isEmpty()) events.publishEvent(MapChange.reload(who));
        return movs.size();
    }

    private Location lock(String code) {
        return locations.lock(code).orElseThrow(() -> ApiError.notFound("A posição " + code + " não existe."));
    }

    private static void checkVersion(Location p, long seenVersion) {
        if (p.getVersion() != seenVersion) {
            String who = p.getUpdatedBy() == null ? "outra pessoa" : p.getUpdatedBy();
            throw ApiError.conflict(p.name() + " acabou de ser alterada por " + who
                    + ". O mapa já foi atualizado; confirma e tenta de novo.");
        }
    }

    private List<LocationDto> finish(String who, List<Location> changed, boolean lotsChanged, Movement m) {
        locations.flush(); // flush the UPDATEs now: DTOs get the new versions and conflicts blow up here
        MovementDto mov = m == null ? null : MovementDto.from(movements.saveAndFlush(m));
        Function<String, Product> pg = byGtin();
        List<LocationDto> dtos = new ArrayList<>();
        for (Location p : changed) dtos.add(LocationDto.from(p, pg));
        String area = m != null ? m.getArea() : changed.stream().anyMatch(Location::isFinishedGoods) ? "FG" : "RM";
        events.publishEvent(new MapChange(who, dtos, lotsChanged, mov, false, area));
        return dtos;
    }

    /** Portuguese list name for messages shown to users. */
    private static String listName(Lot.Holding h) {
        return h == Lot.Holding.CORRIDOR ? "Corredor" : "Sobras";
    }
}
