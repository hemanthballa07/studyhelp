package com.platform.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A user's subscription. One per user. Identity owns this table. */
@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Subscription() {
        // for JPA
    }

    public Subscription(UUID id, UUID userId, Plan plan, SubscriptionStatus status,
                        Instant activatedAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.plan = plan;
        this.status = status;
        this.activatedAt = activatedAt;
        this.updatedAt = updatedAt;
    }

    /** Activate this subscription on a plan as of {@code now}. */
    public void activate(Plan plan, Instant now) {
        this.plan = plan;
        this.status = SubscriptionStatus.ACTIVE;
        this.activatedAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Plan getPlan() {
        return plan;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
