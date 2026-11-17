package edu.cu.tracker;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

public class SubmissionService {

    private static final Pattern UID = Pattern.compile("\\d{2}[A-Z]{3}\\d{5}");
    private static final int MAX_TITLE = 120;

    private final SubmissionStore store;
    private final Clock clock;

    public SubmissionService(SubmissionStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public Submission submit(String uid, String studentName, String title, String repoUrl) {
        String cleanUid = uid == null ? "" : uid.trim().toUpperCase();
        if (!UID.matcher(cleanUid).matches()) {
            throw new IllegalArgumentException("uid must look like 24MCC20081");
        }
        if (isBlank(studentName)) {
            throw new IllegalArgumentException("studentName is required");
        }
        if (isBlank(title) || title.trim().length() > MAX_TITLE) {
            throw new IllegalArgumentException("title is required (max " + MAX_TITLE + " characters)");
        }
        if (repoUrl == null || !repoUrl.trim().startsWith("https://github.com/")) {
            throw new IllegalArgumentException("repoUrl must be a https://github.com/ link");
        }
        String now = now();
        Submission s = new Submission(0, cleanUid, studentName.trim(), title.trim(),
                repoUrl.trim(), Status.SUBMITTED, now, now);
        return store.save(s);
    }

    // synchronized so two reviewers cannot change the same submission at the same time
    // (the app runs as one instance; more replicas would need a Redis transaction here)
    public synchronized Submission changeStatus(long id, String statusText) {
        Submission current = get(id);
        Status next;
        try {
            next = Status.valueOf(statusText.trim().toUpperCase());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("unknown status: " + statusText);
        }
        if (!current.status().canMoveTo(next)) {
            throw new IllegalStateException("cannot move from " + current.status() + " to " + next);
        }
        Submission updated = current.withStatus(next, now());
        store.update(updated);
        return updated;
    }

    public Submission get(long id) {
        return store.find(id).orElseThrow(() -> new NoSuchElementException("submission " + id + " not found"));
    }

    public List<Submission> list() {
        return store.findAll();
    }

    private String now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.SECONDS).toString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
