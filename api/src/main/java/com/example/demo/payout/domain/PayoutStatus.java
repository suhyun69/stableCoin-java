package com.example.demo.payout.domain;

public enum PayoutStatus {
    REQUESTED, AWAITING_CONFIRMATION, SETTLED, FAILED, REQUIRES_REVIEW;

    public boolean canTransitionTo(PayoutStatus next) {
        if (next == null) return false;
        if (this == next) return true;
        if (next == REQUIRES_REVIEW) return true;
        return switch (this) {
            case REQUESTED -> next == AWAITING_CONFIRMATION || next == FAILED;
            case AWAITING_CONFIRMATION -> next == SETTLED || next == FAILED;
            case SETTLED, FAILED, REQUIRES_REVIEW -> false;
        };
    }
}
