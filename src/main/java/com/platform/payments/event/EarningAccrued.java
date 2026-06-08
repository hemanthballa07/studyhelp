package com.platform.payments.event;

import java.util.UUID;

public record EarningAccrued(UUID questionId, UUID expertId, int amountCents) {

    public static final String TYPE = "EarningAccrued";
}
