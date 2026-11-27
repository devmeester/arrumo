package pt.arrumo.packing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** At 17:00 Monday to Friday (app.packing.cron), sends the next-day backlog list. */
@Component
public class DailyListJob {

    private static final Logger log = LoggerFactory.getLogger(DailyListJob.class);
    private final PackingService service;

    public DailyListJob(PackingService service) {
        this.service = service;
    }

    @Scheduled(cron = "${app.packing.cron}", zone = "Europe/Lisbon")
    public void send() {
        PackingService.DispatchDto r = service.sendDailyList("Automático");
        if (r.ok()) log.info("Packing list sent: {}", r.detail());
        else log.warn("Packing list NOT sent: {}", r.detail());
    }
}
