package com.tregy.backend.service.impl;

import com.tregy.backend.repository.AppUserRepository;
import com.tregy.backend.service.AccountPurgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Removes accounts once their grace period is over. Each account is deleted in its own transaction,
 * so one failure never blocks the others, and the cascades remove everything that belongs to it. The
 * delete is a no-op for an id that is already gone, so a second instance running the same job is
 * harmless. Only the internal id is logged (ADR 0009).
 */
@Service
public class AccountPurgeServiceImpl implements AccountPurgeService {

    private static final Logger log = LoggerFactory.getLogger(AccountPurgeServiceImpl.class);

    private final AppUserRepository users;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Duration grace;

    public AccountPurgeServiceImpl(AppUserRepository users, PlatformTransactionManager tm, Clock clock,
                                   @Value("${app.account-deletion.grace-days:14}") long graceDays) {
        this.users = users;
        this.tx = new TransactionTemplate(tm);
        this.clock = clock;
        this.grace = Duration.ofDays(graceDays);
    }

    // "-" disables the schedule (the default is every day at 03:30 server time).
    @Scheduled(cron = "${app.account-deletion.purge-cron:0 30 3 * * *}")
    void scheduled() {
        purgeDue();
    }

    @Override
    public int purgeDue() {
        Instant cutoff = Instant.now(clock).minus(grace);
        List<String> due = users.findIdsWithDeletionRequestedBefore(cutoff);
        int deleted = 0;
        for (String id : due) {
            try {
                Boolean done = tx.execute(status -> {
                    // Re-check inside the transaction: the owner may have signed in (cancelled) since the list was read.
                    return users.findByIdForUpdate(id)
                            .filter(u -> u.getDeletionRequestedAt() != null && u.getDeletionRequestedAt().isBefore(cutoff))
                            .map(u -> {
                                users.delete(u);
                                users.flush();
                                return true;
                            }).orElse(false);
                });
                if (Boolean.TRUE.equals(done)) {
                    deleted++;
                    log.info("account_deleted userId={} reason=grace_period_ended", id);
                }
            } catch (RuntimeException e) {
                log.error("account_purge_failed userId={}", id, e);
            }
        }
        return deleted;
    }
}
