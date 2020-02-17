package com.google.gerrit.entities;

import com.google.auto.value.AutoValue;
import com.google.gerrit.common.Nullable;
import java.time.Instant;

/**
 * A single update to the attention set. To reconstruct the attention set these instances are parsed
 * in reverse chronological order. Since each update contains all required information and
 * invalidates all previous state (hence the name -Status rather than -Update), only the most recent
 * record is relevant for each user.
 *
 * <p>See https://www.gerritcodereview.com/design-docs/attention-set.html for details.
 */
@AutoValue
public abstract class AttentionStatus {
  /** Users can be added to or removed from the attention set. */
  public enum Operation {
    ADD,
    REMOVE
  }

  /**
   * The time at which this status was set. This is null for instances to be written because the
   * timestamp in the commit message will be used.
   */
  @Nullable
  public abstract Instant timestamp();

  /** The user included in or excluded from the attention set. */
  public abstract Account.Id account();

  /** Indicates whether the user is added to or removed from the attention set. */
  public abstract Operation operation();

  /** A short human readable reason that explains this status (e.g. "manual"). */
  public abstract String reason();

  /**
   * Create an instance from data read from NoteDB. This includes the timestamp taken from the
   * commit.
   */
  public static AttentionStatus createFromRead(
      Instant timestamp, Account.Id account, Operation operation, String reason) {
    return new AutoValue_AttentionStatus(timestamp, account, operation, reason);
  }

  /**
   * Create an instance to be written to NoteDB. This has no timestamp because the timestamp of the
   * commit will be used.
   */
  public static AttentionStatus createForWrite(
      Account.Id account, Operation operation, String reason) {
    return new AutoValue_AttentionStatus(null, account, operation, reason);
  }
}
