package edu.cu.tracker;

import java.util.List;
import java.util.Optional;

public interface SubmissionStore {

    /** Saves a new submission and returns it with its id filled in. */
    Submission save(Submission submission);

    Optional<Submission> find(long id);

    List<Submission> findAll();

    void update(Submission submission);

    /** Used by /health. Throws if the store cannot be reached. */
    void ping();

    String name();
}
