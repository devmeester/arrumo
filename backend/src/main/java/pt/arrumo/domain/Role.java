package pt.arrumo.domain;

import java.util.List;

/**
 * Raw material (C5): VIEWER only looks; OPERATOR changes the map; MANAGER also manages products and lots;
 * ADMIN also manages users, the audit log and the packing board.
 * Finished goods: everyone sees it; LOGISTICS, MANAGER and ADMIN change it. LOGISTICS sees nothing else.
 */
public enum Role {
    VIEWER, OPERATOR, MANAGER, ADMIN, LOGISTICS;

    /** Authority to change the finished-goods map. */
    public static final String EDITS_FG = "ROLE_FG";

    public List<String> authorities() {
        return switch (this) {
            case VIEWER -> List.of("ROLE_VIEWER");
            case OPERATOR -> List.of("ROLE_VIEWER", "ROLE_OPERATOR");
            case MANAGER -> List.of("ROLE_VIEWER", "ROLE_OPERATOR", "ROLE_MANAGER", EDITS_FG);
            case ADMIN -> List.of("ROLE_VIEWER", "ROLE_OPERATOR", "ROLE_MANAGER", "ROLE_ADMIN", EDITS_FG);
            case LOGISTICS -> List.of("ROLE_LOGISTICS", EDITS_FG);
        };
    }
}
