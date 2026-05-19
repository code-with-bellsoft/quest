package dev.cyberjar.event;


import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logging.Logger;

@ApplicationScoped
public class AfterCommitEventPublisher {

    private static final Logger log =
            Logger.getLogger(AfterCommitEventPublisher.class);

    private final TransactionSynchronizationRegistry synchronizationRegistry;
    private final QuestAssignedEventListener listener;
    private final ManagedExecutor executor;

    @Inject
    public AfterCommitEventPublisher(
            TransactionSynchronizationRegistry synchronizationRegistry,
            QuestAssignedEventListener listener,
            ManagedExecutor executor
    ) {
        this.synchronizationRegistry = synchronizationRegistry;
        this.listener = listener;
        this.executor = executor;
    }


    public void publishAfterCommit(QuestAssignedEvent event) {
        synchronizationRegistry.registerInterposedSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {
                // Nothing to do before commit.
            }

            @Override
            public void afterCompletion(int status) {
                if (status != Status.STATUS_COMMITTED) {
                    log.debugf(
                            "Skipping quest assigned event because transaction did not commit. Status: %d",
                            status
                    );
                    return;
                }

                executor.execute(() -> listener.onQuestAssigned(event));
            }
        });
    }

}
