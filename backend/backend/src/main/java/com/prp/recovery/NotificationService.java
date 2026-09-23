package com.prp.recovery;

import com.prp.domain.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

/** Customer messages. Logs for now; plug in email / SMS / WhatsApp providers here. */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    public void sendReminder(Payment p) {
        log.info("Reminder sent to {} for payment {}", p.getCustomer().getEmail(), p.getId());
        p.setLastNotifiedAt(Instant.now());
    }

    public void sendCardUpdateLink(Payment p) {
        log.info("Card update link sent to {} for payment {}", p.getCustomer().getEmail(), p.getId());
        p.setLastNotifiedAt(Instant.now());
    }
}
