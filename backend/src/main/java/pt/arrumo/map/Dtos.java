package pt.arrumo.map;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import pt.arrumo.domain.ErpFgStock;
import pt.arrumo.domain.Lot;
import pt.arrumo.domain.Movement;
import pt.arrumo.domain.Pallet;
import pt.arrumo.domain.Location;
import pt.arrumo.domain.Product;
import pt.arrumo.domain.Zone;

/** What the API returns. Entities are never exposed directly. */
public final class Dtos {
    private Dtos() {}

    public record LotDto(String code, String productCode, String product, BigDecimal weightKg, LocalDate productionDate,
                          String notes, String status, String holding, boolean noData, String location) {
        public static LotDto from(Lot l) { return from(l, null); }

        public static LotDto from(Lot l, String location) {
            if (l == null) return null;
            var p = l.getProduct();
            return new LotDto(l.getCode(), p == null ? null : p.getCode(), p == null ? null : p.getDescription(),
                    l.getWeightKg(), l.getProductionDate(), l.getNotes(), l.getStatus().name(),
                    l.getHolding() == null ? null : l.getHolding().name(), l.noData(), location);
        }
    }

    /** Finished-goods pallet. The product is the one picked by hand or, failing that, the one matching the label GTIN. */
    public record PalletDto(long id, String sscc, String lot, String gtin, String productCode, String product,
                            BigDecimal weightKg, Integer boxes, LocalDate productionDate, LocalDate expiryDate,
                            String notes, String status, boolean noData, long version) {
        public static PalletDto from(Pallet p, Function<String, Product> byGtin) {
            if (p == null) return null;
            Product pr = p.getProduct() != null ? p.getProduct() : (p.getGtin() == null ? null : byGtin.apply(p.getGtin()));
            return new PalletDto(p.getId(), p.getSscc(), p.getLot(), p.getGtin(), pr == null ? null : pr.getCode(),
                    pr == null ? null : pr.getDescription(), p.getWeightKg(), p.getBoxes(), p.getProductionDate(), p.getExpiryDate(),
                    p.getNotes(), p.getStatus().name(), pr == null, p.getVersion());
        }
    }

    public record LocationDto(String code, String zone, String groupLabel, int rowNo, int colNo, LotDto lot, PalletDto pallet,
                             String note, long version, String updatedBy, Instant updatedAt) {
        public static LocationDto from(Location p, Function<String, Product> byGtin) {
            return new LocationDto(p.getCode(), p.getZoneId(), p.getGroupLabel(), p.getRowNo(), p.getColNo(),
                    LotDto.from(p.getLot()), PalletDto.from(p.getPallet(), byGtin), p.getNote(), p.getVersion(),
                    p.getUpdatedBy(), p.getUpdatedAt());
        }
    }

    public record ZoneDto(String id, String name, List<String> columnLabels) {
        public static ZoneDto from(Zone z) { return new ZoneDto(z.getId(), z.getName(), z.getColumnLabels()); }
    }

    public record MovementDto(long id, Instant occurredAt, String username, String type, String lot,
                               String from, String to, String note, String area) {
        public static MovementDto from(Movement m) {
            return new MovementDto(m.getId(), m.getOccurredAt(), m.getUsername(), m.getType().name(), m.getLotCode(),
                    m.getFromLocation(), m.getToLocation(), m.getNote(), m.getArea());
        }
    }

    /** ERP stock for finished goods: the total for the lot. */
    public record ErpFgStockDto(String lot, String productCode, String product, String quantity, BigDecimal weightKg,
                           BigDecimal boxes, String notes, Instant importedAt) {
        public static ErpFgStockDto from(ErpFgStock e) {
            var p = e.getProduct();
            return new ErpFgStockDto(e.getLot(), p == null ? null : p.getCode(), p == null ? null : p.getDescription(),
                    e.getQuantity(), e.getWeightKg(), e.getBoxes(), e.getNotes(), e.getImportedAt());
        }
    }

    public record MapDto(List<ZoneDto> zones, List<LocationDto> locations,
                          List<LotDto> lists, List<MovementDto> movements,
                          List<ErpFgStockDto> erpFgStock) {}

    /**
     * Sent in real time to every open screen after each change committed to the database.
     * area: "RM" (raw material), "FG" (finished goods) or null (affects everything, e.g. products).
     * Logistics users only get what is not RM.
     */
    public record MapChange(String by, List<LocationDto> locations, boolean lotsChanged, MovementDto movement,
                            boolean reload, String area) {
        public static MapChange reload(String by) { return new MapChange(by, List.of(), true, null, true, null); }
    }
}
