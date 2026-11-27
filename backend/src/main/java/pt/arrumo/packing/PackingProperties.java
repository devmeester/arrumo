package pt.arrumo.packing;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param webhookDaily    Power Automate flow URL that sends the 17:00 list (email + Teams)
 * @param webhookFinished flow URL that reports each finished packing job on Teams
 * @param cron            when the list goes out (Lisbon time). Default: 17:00, Monday to Friday
 */
@ConfigurationProperties("app.packing")
public record PackingProperties(String webhookDaily, String webhookFinished, String cron) {}
