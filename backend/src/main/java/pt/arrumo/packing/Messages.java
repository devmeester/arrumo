package pt.arrumo.packing;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * JSON body sent to Power Automate. Works as-is with the Teams template
 * "Post to a chat when a webhook request is received" (uses "attachments", an adaptive card)
 * and also carries "subject", "text" and "html" for the flow's send-email action.
 * The message text is in Portuguese: it is read by people at the plant.
 */
final class Messages {

    record Line(String title, String product, String quantity, String notes) {}

    private static final Locale PT = Locale.forLanguageTag("pt-PT");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private Messages() {}

    /** Next working day (Mon-Fri) after today. */
    static LocalDate nextWorkingDay(LocalDate today) {
        LocalDate d = today.plusDays(1);
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) d = d.plusDays(1);
        return d;
    }

    static String daily(LocalDate day, List<Line> lines) {
        String subject = "Embalamentos para " + day.getDayOfWeek().getDisplayName(TextStyle.FULL, PT) + ", " + DATE.format(day);
        List<String> blocks = new ArrayList<>();
        StringBuilder text = new StringBuilder(subject).append("\n\n");
        StringBuilder html = new StringBuilder("<p><b>").append(html(subject)).append("</b></p>");
        if (lines.isEmpty()) {
            blocks.add("Sem embalamentos planeados.");
            text.append("Sem embalamentos planeados.\n");
            html.append("<p>Sem embalamentos planeados.</p>");
        } else {
            html.append("<ol>");
            int n = 1;
            for (Line l : lines) {
                String row = n++ + ". " + summary(l);
                blocks.add(row + (l.notes().isBlank() ? "" : "\n" + l.notes()));
                text.append(row).append('\n');
                if (!l.notes().isBlank()) text.append("   ").append(l.notes()).append('\n');
                html.append("<li><b>").append(html(l.title())).append("</b>")
                        .append(l.product().isBlank() ? "" : " | " + html(l.product()))
                        .append(l.quantity().isBlank() ? "" : " | " + html(l.quantity()))
                        .append(l.notes().isBlank() ? "" : "<br><i>" + html(l.notes()) + "</i>")
                        .append("</li>");
            }
            html.append("</ol>");
        }
        return message(subject, blocks, text.toString(), html.toString());
    }

    static String finished(Line l, String who, ZonedDateTime occurredAt) {
        String subject = "Embalamento concluído: " + l.title();
        String footer = "Concluído às " + TIME.format(occurredAt) + (who == null ? "" : " por " + who);
        List<String> blocks = new ArrayList<>();
        blocks.add(summary(l));
        if (!l.notes().isBlank()) blocks.add(l.notes());
        blocks.add(footer);
        String text = subject + "\n" + String.join("\n", blocks);
        String html = "<p><b>" + html(subject) + "</b></p><p>" + html(summary(l)) + "</p>"
                + (l.notes().isBlank() ? "" : "<p><i>" + html(l.notes()) + "</i></p>") + "<p>" + html(footer) + "</p>";
        return message(subject, blocks, text, html);
    }

    private static String summary(Line l) {
        StringBuilder s = new StringBuilder(l.title());
        if (!l.product().isBlank()) s.append(" | ").append(l.product());
        if (!l.quantity().isBlank()) s.append(" | ").append(l.quantity());
        return s.toString();
    }

    private static String message(String subject, List<String> blocks, String text, String html) {
        StringBuilder body = new StringBuilder("[{\"type\":\"TextBlock\",\"text\":").append(json(subject))
                .append(",\"weight\":\"Bolder\",\"size\":\"Medium\",\"wrap\":true}");
        for (String b : blocks) body.append(",{\"type\":\"TextBlock\",\"text\":").append(json(b)).append(",\"wrap\":true}");
        body.append(']');
        return "{\"type\":\"message\",\"subject\":" + json(subject) + ",\"text\":" + json(text) + ",\"html\":" + json(html)
                + ",\"attachments\":[{\"contentType\":\"application/vnd.microsoft.card.adaptive\",\"contentUrl\":null,"
                + "\"content\":{\"$schema\":\"http://adaptivecards.io/schemas/adaptive-card.json\",\"type\":\"AdaptiveCard\","
                + "\"version\":\"1.4\",\"body\":" + body + "}}]}";
    }

    static String json(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.append('"').toString();
    }

    private static String html(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
