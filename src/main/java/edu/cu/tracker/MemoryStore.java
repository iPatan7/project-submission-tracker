package edu.cu.tracker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** In-memory store for unit tests and for running the image without Redis (STORE_TYPE=memory). */
public class MemoryStore implements SubmissionStore {

    private final Map<Long, Submission> data = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    @Override
    public Submission save(Submission submission) {
        Submission saved = submission.withId(seq.incrementAndGet());
        data.put(saved.id(), saved);
        return saved;
    }

    @Override
    public Optional<Submission> find(long id) {
        return Optional.ofNullable(data.get(id));
    }

    @Override
    public List<Submission> findAll() {
        List<Submission> all = new ArrayList<>(data.values());
        all.sort(Comparator.comparingLong(Submission::id));
        return all;
    }

    @Override
    public void update(Submission submission) {
        data.put(submission.id(), submission);
    }

    @Override
    public void ping() {
        // always reachable
    }

    @Override
    public String name() {
        return "memory";
    }
}
