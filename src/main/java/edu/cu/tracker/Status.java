package edu.cu.tracker;

public enum Status {
    SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED;

    /** Allowed moves: SUBMITTED -> UNDER_REVIEW -> APPROVED or REJECTED, REJECTED -> SUBMITTED (resubmit). */
    public boolean canMoveTo(Status next) {
        return switch (this) {
            case SUBMITTED -> next == UNDER_REVIEW;
            case UNDER_REVIEW -> next == APPROVED || next == REJECTED;
            case REJECTED -> next == SUBMITTED;
            case APPROVED -> false;
        };
    }
}
