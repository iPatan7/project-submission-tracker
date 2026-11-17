package edu.cu.tracker;

public record Submission(long id, String uid, String studentName, String title,
                         String repoUrl, Status status, String submittedAt, String updatedAt) {

    public Submission withId(long newId) {
        return new Submission(newId, uid, studentName, title, repoUrl, status, submittedAt, updatedAt);
    }

    public Submission withStatus(Status newStatus, String time) {
        return new Submission(id, uid, studentName, title, repoUrl, newStatus, submittedAt, time);
    }
}
