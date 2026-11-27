package pt.arrumo.management;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.arrumo.domain.*;
import pt.arrumo.errors.ApiError;
import pt.arrumo.map.Dtos.MapChange;
import pt.arrumo.security.Audit;

@Service
public class LotService {

    public record ImportLine(String lot, String product, String weight, String notes, String location, String holding) {}
    public record ImportSummary(int created, int updated, int newProducts, int placed, List<String> warnings, List<Missing> missing) {}
    /** Lot (or pallet) that is in the app but missing from the full ERP list: it has already left. */
    public record Missing(String lot, String where, Long palletId) {}
    public record LotRequest(String code, String productCode, BigDecimal weightKg, LocalDate productionDate, String notes) {}

    private static final Pattern LOT_CODE = Pattern.compile("[A-Za-z0-9._/-]{3,30}");
    /** "260103 - BAC DUC C/VID ..." -> code 260103, description BAC DUC C/VID ... */
    private static final Pattern PRODUCT_TEXT = Pattern.compile("^\\s*([A-Za-z0-9._-]{1,20})\\s*(?:-\\s*(.+))?$");
    private static final int MAX_LINES = 5000;

    private final LotRepo lots;
    private final ProductRepo products;
    private final LocationRepo locations;
    private final MovementRepo movements;
    private final Audit audit;
    private final ErpFgStockRepo erpFgStock;
    private final ApplicationEventPublisher events;

    public LotService(LotRepo lots, ProductRepo products, LocationRepo locations, MovementRepo movements,
                       Audit audit, ErpFgStockRepo erpFgStock, ApplicationEventPublisher events) {
        this.erpFgStock = erpFgStock;
        this.lots = lots;
        this.products = products;
        this.locations = locations;
        this.movements = movements;
        this.audit = audit;
        this.events = events;
    }

    @Transactional
    public Lot create(LotRequest p) {
        String code = code(p.code());
        if (lots.findByCode(code).isPresent()) throw ApiError.conflict("O lote " + code + " já existe.");
        Lot l = new Lot(code, product(p.productCode()), weight(p.weightKg()));
        l.setHolding(Lot.Holding.CORRIDOR); // new lot, not on the map: it is in the corridor
        if (p.productionDate() != null) l.setProductionDate(p.productionDate());
        l.setNotes(notes(p.notes()));
        lots.save(l);
        audit.write("LOT_CREATED", code);
        notifyReload();
        return l;
    }

    /** Corrects or fills in a lot (this is how an orange "no data" lot gets completed). */
    @Transactional
    public Lot update(String currentCode, LotRequest p) {
        Lot l = lots.findByCode(currentCode).orElseThrow(() -> ApiError.notFound("Lote não encontrado."));
        Product prod = product(p.productCode());
        if (prod == null && l.getProduct() != null) throw ApiError.invalid("Escolhe o produto.");
        String before = summary(l);
        l.setProduct(prod);
        l.setWeightKg(weight(p.weightKg()));
        if (p.productionDate() != null) l.setProductionDate(p.productionDate());
        l.setNotes(notes(p.notes()));
        audit.write("LOT_UPDATED", currentCode + ": " + before + " -> " + summary(l));
        notifyReload();
        return l;
    }

    /** Only for lots created by mistake: in stock, not on the map and with no movements. */
    @Transactional
    public void delete(String code) {
        Lot l = lots.findByCode(code).orElseThrow(() -> ApiError.notFound("Lote não encontrado."));
        if (locations.findByLot(l).isPresent()) throw ApiError.conflict("O lote está no mapa. Liberta a posição primeiro.");
        if (l.getHolding() != null) throw ApiError.conflict("O lote está numa lista (Corredor/Sobras). Tira-o primeiro.");
        if (!movements.findByLotCodeOrderByIdDesc(code).isEmpty())
            throw ApiError.conflict("O lote tem movimentos registados e fica no histórico. Usa 'Saiu da câmara' em vez de apagar.");
        lots.delete(l);
        audit.write("LOT_DELETED", summary(l));
        notifyReload();
    }

    /** Restores a lot that was marked as shipped by mistake (it goes back to the corridor). */
    @Transactional
    public Lot restore(String code) {
        Lot l = lots.findByCode(code).orElseThrow(() -> ApiError.notFound("Lote não encontrado."));
        if (l.getStatus() != Lot.Status.SHIPPED) throw ApiError.conflict("O lote não está como expedido.");
        l.restore();
        l.setHolding(Lot.Holding.CORRIDOR);
        movements.save(new Movement(Audit.currentUser(), Movement.Type.MOVE, code, "SHIPPED", "CORRIDOR", "reposto"));
        audit.write("LOT_RESTORED", code);
        notifyReload();
        return l;
    }

    /**
     * Import pasted from Excel or the ERP. Creates what is missing, updates what exists and, if a location column
     * (or a list: corridor/leftovers) is present, puts the lot there when free. One transaction: all or nothing.
     */
    @Transactional
    public ImportSummary importLots(List<ImportLine> lines, String who) {
        if (lines == null || lines.isEmpty()) throw ApiError.invalid("Não há linhas para importar.");
        if (lines.size() > MAX_LINES) throw ApiError.invalid("No máximo " + MAX_LINES + " linhas de cada vez.");
        int created = 0, updated = 0, placed = 0, returned = 0;
        int[] added = {0};
        List<String> warnings = new ArrayList<>();
        Map<String, Product> cache = new HashMap<>();

        int n = 1;
        for (ImportLine li : lines) {
            n++;
            String code = li.lot() == null ? "" : li.lot().strip().toUpperCase(Locale.ROOT);
            if (code.isEmpty()) continue;
            if (!LOT_CODE.matcher(code).matches()) { warnings.add("Linha " + n + ": lote inválido '" + clip(code) + "'."); continue; }

            Product prod;
            try {
                prod = productFromErp(li.product(), cache, added);
            } catch (IllegalArgumentException e) {
                warnings.add("Linha " + n + ": produto '" + clip(li.product()) + "' não reconhecido."); continue;
            }
            BigDecimal kg;
            try {
                kg = weightText(li.weight());
            } catch (NumberFormatException e) {
                warnings.add("Linha " + n + ": peso '" + clip(li.weight()) + "' não é um número.");
                continue;
            }

            // No kg (only boxes/units): keep the ERP quantity in the notes so it is not lost
            String notes = li.notes() == null ? "" : li.notes().strip();
            if (kg == null && li.weight() != null && !li.weight().isBlank())
                notes = ("Qtd. ERP: " + li.weight().strip() + (notes.isEmpty() ? "" : " | " + notes));

            Lot l = lots.findByCode(code).orElse(null);
            if (l == null) {
                l = lots.save(new Lot(code, prod, kg));
                l.setNotes(notes(notes));
                l.setHolding(Lot.Holding.CORRIDOR); // no location given: it is in the corridor (changed below if a location/list comes in)
                created++;
            } else {
                if (prod != null) l.setProduct(prod);
                if (kg != null) l.setWeightKg(kg);
                if (!notes.isEmpty()) l.setNotes(notes(notes));
                updated++;
            }

            String pos = Location.normalize(li.location()); // R514a, r514A -> R514a
            String holding = holdingName(li.holding());
            if (!pos.isEmpty() || !holding.isEmpty()) {
                // The ERP says where it is: if it was marked as shipped (mistake or bulk ship), it comes back to the room
                String from = null;
                if (l.getStatus() == Lot.Status.SHIPPED) { l.restore(); from = "SHIPPED"; returned++; }
                Location current = locations.findByLot(l).orElse(null);
                if (current != null) {
                    if (!current.getCode().equals(pos)) warnings.add("Linha " + n + ": " + code + " já está em " + current.name() + ", ficou lá.");
                    continue;
                }
                if (!holding.isEmpty()) {
                    try {
                        if (l.getHolding() == Lot.Holding.valueOf(holding)) continue; // already there
                        l.setHolding(Lot.Holding.valueOf(holding));
                        movements.save(new Movement(who, Movement.Type.MOVE, code, from, holding, from == null ? "importação" : "voltou com a importação"));
                        placed++;
                    } catch (IllegalArgumentException e) {
                        if (from != null) l.setHolding(Lot.Holding.CORRIDOR);
                        warnings.add("Linha " + n + ": local '" + clip(holding) + "' desconhecido (usa CORREDOR ou SOBRAS).");
                    }
                    continue;
                }
                Location p = locations.lock(pos).orElse(null);
                if (p == null || !p.isFree()) {
                    if (from != null) l.setHolding(Lot.Holding.CORRIDOR); // came back but has no place: goes to the corridor
                    warnings.add("Linha " + n + ": " + (p == null ? "a posição " + clip(pos) + " não existe" : pos + " já está ocupada ou reservada")
                            + "; " + code + " não foi colocado.");
                    continue;
                }
                l.setHolding(null);
                p.setLot(l);
                p.touch(who);
                locations.flush(); // the next lot may want the same location: it must see it taken
                movements.save(new Movement(who, Movement.Type.STORE, code, from, pos, from == null ? "importação" : "voltou com a importação"));
                placed++;
            }
        }
        // Full stock list (no location/list given): report the lots in the room that are not in it.
        boolean complete = lines.stream().allMatch(li -> (li.location() == null || li.location().isBlank()) && (li.holding() == null || li.holding().isBlank()));
        java.util.Set<String> received = new java.util.HashSet<>();
        for (ImportLine li : lines) if (li.lot() != null) received.add(li.lot().strip().toUpperCase(Locale.ROOT));
        // Leftovers come in a separate ERP list: the raw-material list checks the map and the corridor,
        // the leftovers list only checks the leftovers.
        boolean leftoversOnly = lines.stream().allMatch(li -> "LEFTOVERS".equals(holdingName(li.holding())));
        List<Missing> missing = new ArrayList<>();
        if (complete) {
            for (Location p : locations.allWithContents())
                if (p.getLot() != null && !received.contains(p.getLot().getCode())) missing.add(new Missing(p.getLot().getCode(), p.getCode(), null));
        }
        if (complete || leftoversOnly) {
            Lot.Holding target = leftoversOnly ? Lot.Holding.LEFTOVERS : Lot.Holding.CORRIDOR;
            for (Lot l : lots.inLists())
                if (l.getHolding() == target && !received.contains(l.getCode())) missing.add(new Missing(l.getCode(), l.getHolding().name(), null));
        }
        if (returned > 0) warnings.add(0, returned + (returned == 1 ? " lote estava como saído e voltou à câmara." : " lotes estavam como saídos e voltaram à câmara."));
        int newProducts = added[0];
        ImportSummary r = new ImportSummary(created, updated, newProducts, placed, warnings.size() > 200 ? warnings.subList(0, 200) : warnings, missing);
        audit.write("LOT_IMPORT", created + " created, " + updated + " updated, " + newProducts
                + " new products, " + placed + " placed, " + warnings.size() + " warnings");
        notifyReload();
        return r;
    }

    public record FgImportSummary(int lots, int newProducts, int withoutPallet, List<String> warnings, List<Missing> missing) {}

    /**
     * Finished-goods stock exported by the ERP (lot, product, stock, notes). One row per lot, with the total.
     * Replaces the previous stock entirely: the ERP list is the truth. Pallets on the map do not change; the app compares.
     */
    @Transactional
    public FgImportSummary importFg(List<ImportLine> lines, String who) {
        if (lines == null || lines.isEmpty()) throw ApiError.invalid("Não há linhas para importar.");
        if (lines.size() > MAX_LINES) throw ApiError.invalid("No máximo " + MAX_LINES + " linhas de cada vez.");
        int[] added = {0};
        List<String> warnings = new ArrayList<>();
        Map<String, Product> cache = new HashMap<>();
        Map<String, ErpFgStock> stock = new java.util.LinkedHashMap<>();
        int n = 0;
        for (ImportLine li : lines) {
            n++;
            String code = li.lot() == null ? "" : li.lot().strip().toUpperCase(Locale.ROOT);
            if (code.isEmpty()) continue;
            if (!LOT_CODE.matcher(code).matches()) { warnings.add("Linha " + n + ": lote inválido '" + clip(code) + "'."); continue; }
            Product prod;
            try {
                prod = productFromErp(li.product(), cache, added);
            } catch (IllegalArgumentException e) {
                warnings.add("Linha " + n + ": produto '" + clip(li.product()) + "' não reconhecido."); continue;
            }
            String qty = li.weight() == null ? "" : li.weight().strip();
            BigDecimal kg, boxes;
            try {
                kg = weightText(qty);
                boxes = boxesText(qty);
            } catch (NumberFormatException e) {
                warnings.add("Linha " + n + ": quantidade '" + clip(qty) + "' não é um número."); continue;
            }
            if (stock.containsKey(code)) warnings.add("Linha " + n + ": o lote " + code + " vem repetido, ficou a última linha.");
            stock.put(code, new ErpFgStock(code, prod, cut(qty, 80), kg, boxes, notes(li.notes())));
        }
        if (stock.isEmpty()) throw ApiError.invalid("Nenhuma linha válida. Nada foi mudado.");
        erpFgStock.deleteAllInBatch();
        erpFgStock.flush();
        erpFgStock.saveAll(stock.values());

        java.util.Set<String> withPallet = new java.util.HashSet<>();
        List<Missing> missing = new ArrayList<>();
        for (Location p : locations.allWithContents()) {
            if (p.getPallet() == null || p.getPallet().getLot() == null) continue;
            String lot = p.getPallet().getLot().toUpperCase(Locale.ROOT);
            withPallet.add(lot);
            if (!stock.containsKey(lot)) missing.add(new Missing(lot, p.getCode(), p.getPallet().getId()));
        }
        int withoutPallet = (int) stock.keySet().stream().filter(l -> !withPallet.contains(l)).count();
        audit.write("FG_IMPORT", stock.size() + " lots, " + added[0] + " new products, " + withoutPallet + " without a pallet on the map");
        notifyReload();
        return new FgImportSummary(stock.size(), added[0], withoutPallet, warnings.size() > 200 ? warnings.subList(0, 200) : warnings, missing);
    }

    // ----------------------------------------------------------------------------------------------

    /** "270165 - BAC DUC ..." -> product 270165 (created if missing; the ERP name wins). Blank -> null. */
    private Product productFromErp(String text, Map<String, Product> cache, int[] added) {
        if (text == null || text.isBlank()) return null;
        Matcher m = PRODUCT_TEXT.matcher(text.strip());
        if (!m.matches()) throw new IllegalArgumentException();
        String pc = m.group(1);
        Product prod = cache.get(pc);
        if (prod != null) return prod;
        String desc = m.group(2) == null ? null : cut(m.group(2).strip(), 200);
        prod = products.findByCode(pc).orElse(null);
        if (prod == null) {
            prod = products.save(new Product(pc, desc == null ? pc : desc));
            added[0]++;
        } else if (desc != null && !desc.equals(prod.getDescription())) {
            prod.setDescription(desc);
        }
        cache.put(pc, prod);
        return prod;
    }

    private static final Pattern BOXES_IN_TEXT = Pattern.compile("([0-9][0-9.,]*)\\s*\\(?CX\\)?");

    /** "576.0 (CX); 4,032.0 (KG)" -> 576; "6.0 CX" -> 6; no boxes -> null. */
    static BigDecimal boxesText(String t) {
        if (t == null) return null;
        Matcher m = BOXES_IN_TEXT.matcher(t.toUpperCase(Locale.ROOT));
        return m.find() ? number(m.group(1)) : null;
    }

    /** List name from an import: accepts the English names and the Portuguese ones people type in Excel. */
    private static String holdingName(String text) {
        String t = text == null ? "" : text.strip().toUpperCase(Locale.ROOT);
        return switch (t) {
            case "CORREDOR" -> "CORRIDOR";
            case "SOBRAS" -> "LEFTOVERS";
            default -> t;
        };
    }

    private void notifyReload() {
        events.publishEvent(MapChange.reload(Audit.currentUser()));
    }

    private static String code(String c) {
        String s = c == null ? "" : c.strip().toUpperCase(Locale.ROOT);
        if (!LOT_CODE.matcher(s).matches()) throw ApiError.invalid("Código de lote inválido (3 a 30 letras, números, . _ / -).");
        return s;
    }

    private Product product(String code) {
        if (code == null || code.isBlank()) return null;
        return products.findByCode(code.strip()).orElseThrow(() -> ApiError.invalid("O produto " + code + " não existe."));
    }

    private static BigDecimal weight(BigDecimal kg) {
        if (kg == null) return null;
        if (kg.signum() < 0 || kg.compareTo(new BigDecimal("100000")) > 0) throw ApiError.invalid("Peso fora do intervalo.");
        return kg.setScale(3, java.math.RoundingMode.HALF_UP);
    }

    private static final Pattern KG_IN_TEXT = Pattern.compile("([0-9][0-9.,]*)\\s*\\(?KG\\)?");

    /**
     * Accepts the ERP format ("271.0 KG", "1,500.5 KG", "0.76 (CX); 3.8 (KG)") and the Portuguese one ("271,5", "1.234,5 kg").
     * Quantities only in boxes or units ("1.0 CX", "0.2 (CX); 2.0 (UN)") return null: they are not a weight.
     */
    static BigDecimal weightText(String t) {
        if (t == null || t.isBlank()) return null;
        String u = t.toUpperCase(Locale.ROOT);
        Matcher m = KG_IN_TEXT.matcher(u);
        String s;
        if (m.find()) s = m.group(1);
        else if (u.contains("CX") || u.contains("UN")) return null;
        else s = u.replaceAll("\\s", "");
        return number(s);
    }

    /** "1,500.5" (ERP) or "1.234,5" (Portuguese) -> BigDecimal with 3 decimals. */
    private static BigDecimal number(String s) {
        int comma = s.lastIndexOf(','), dot = s.lastIndexOf('.');
        if (comma >= 0 && dot >= 0) {
            s = comma > dot ? s.replace(".", "").replace(',', '.') : s.replace(",", "");
        } else if (comma >= 0) {
            s = s.matches("\\d{1,3}(,\\d{3})+") ? s.replace(",", "") : s.replace(',', '.');
        }
        BigDecimal v = new BigDecimal(s);
        if (v.signum() < 0 || v.compareTo(new BigDecimal("100000")) > 0) throw new NumberFormatException();
        return v.setScale(3, java.math.RoundingMode.HALF_UP);
    }

    private static String notes(String n) {
        String s = n == null ? "" : n.strip();
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    private static String summary(Lot l) {
        return l.getCode() + " " + (l.getProduct() == null ? "(no product)" : l.getProduct().getCode())
                + " " + (l.getWeightKg() == null ? "" : l.getWeightKg() + " kg");
    }

    private static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String clip(String s) {
        String t = s == null ? "" : s.replaceAll("\\p{Cntrl}", "");
        return t.length() > 30 ? t.substring(0, 30) + "…" : t;
    }
}
