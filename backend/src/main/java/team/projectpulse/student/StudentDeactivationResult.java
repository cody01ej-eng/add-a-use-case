package team.projectpulse.student;

/** Outcome determined in the same transaction as the account-state write. */
public record StudentDeactivationResult(Student student, boolean changed) {
}
