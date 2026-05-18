package dev.cyberjar.spring.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class QuestAssignedEventListener {

    private static final Logger log = LoggerFactory.getLogger(QuestAssignedEventListener.class);

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQuestAssigned(QuestAssignedEvent event) {
        log.info(
                "Quest {} assigned to {} ({})",
                event.questId(),
                event.heroName(),
                event.heroClass()
        );
    }

}
