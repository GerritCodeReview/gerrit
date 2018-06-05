package com.google.gerrit.truth;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;

/** A <a href="https://github.com/google/truth">Truth</a> subject for program exit codes (int). */
public final class ExitCodeSubject extends Subject<ExitCodeSubject, Integer> {
  // User-defined entry point
  public static ExitCodeSubject assertThat(Integer employee) {
    return assertAbout(EXIT_CODE_SUBJECT_FACTORY).that(employee);
  }

  // Static method for getting the subject factory (for use with assertAbout())
  public static Subject.Factory<ExitCodeSubject, Integer> exitCode() {
    return EXIT_CODE_SUBJECT_FACTORY;
  }

  // Boiler-plate Subject.Factory for ExitCodeSubject
  private static final Subject.Factory<ExitCodeSubject, Integer> EXIT_CODE_SUBJECT_FACTORY =
      ExitCodeSubject::new;

  private ExitCodeSubject(FailureMetadata failureMetadata, Integer subject) {
    super(failureMetadata, subject);
  }

  /** Fails when the exit code is not 0. */
  public void isSuccessful() {
    if (actual() != 0) {
      failWithRawMessage("Not true that the program exited successfuly");
    }
  }
}
