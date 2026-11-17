package edu.cu.tracker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class SubmissionServiceTest {

    private SubmissionService service;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC);
        service = new SubmissionService(new MemoryStore(), fixed);
    }

    private Submission validSubmission() {
        return service.submit("24mcc20081", "Mohammad Iqbal Patan", "Project Submission Tracker",
                "https://github.com/iPatan7/project-submission-tracker");
    }

    @Test
    void newSubmissionGetsIdAndSubmittedStatus() {
        Submission s = validSubmission();
        assertEquals(1, s.id());
        assertEquals("24MCC20081", s.uid());
        assertEquals(Status.SUBMITTED, s.status());
        assertEquals("2026-11-17T10:00:00Z", s.submittedAt());
    }

    @Test
    void wrongUidIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.submit("12345", "A", "Title", "https://github.com/a/b"));
    }

    @Test
    void repoMustBeOnGithub() {
        assertThrows(IllegalArgumentException.class,
                () -> service.submit("24MCC20081", "A", "Title", "http://example.com/repo"));
    }

    @Test
    void titleIsRequired() {
        assertThrows(IllegalArgumentException.class,
                () -> service.submit("24MCC20081", "A", "  ", "https://github.com/a/b"));
    }

    @Test
    void reviewFlowWorks() {
        long id = validSubmission().id();
        assertEquals(Status.UNDER_REVIEW, service.changeStatus(id, "under_review").status());
        assertEquals(Status.APPROVED, service.changeStatus(id, "APPROVED").status());
    }

    @Test
    void cannotApproveWithoutReview() {
        long id = validSubmission().id();
        assertThrows(IllegalStateException.class, () -> service.changeStatus(id, "APPROVED"));
    }

    @Test
    void rejectedWorkCanBeResubmitted() {
        long id = validSubmission().id();
        service.changeStatus(id, "UNDER_REVIEW");
        service.changeStatus(id, "REJECTED");
        assertEquals(Status.SUBMITTED, service.changeStatus(id, "SUBMITTED").status());
    }

    @Test
    void unknownStatusIsRejected() {
        long id = validSubmission().id();
        assertThrows(IllegalArgumentException.class, () -> service.changeStatus(id, "DONE"));
    }

    @Test
    void missingSubmissionGivesNotFound() {
        assertThrows(NoSuchElementException.class, () -> service.get(99));
    }
}
