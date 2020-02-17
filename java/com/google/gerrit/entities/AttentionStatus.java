package com.google.gerrit.entities;

import com.google.auto.value.AutoValue;

/**
 * A single change to the attention set. To reconstruct the attention set these instances are parsed
 * in reverse chronological order, considering only the first record for each user.
 *
 * <p>See https://www.gerritcodereview.com/design-docs/attention-set.html for details.
 */
@AutoValue
public abstract class AttentionStatus {
  /** The user included in or excluded from the attention set. */
  public abstract Account.Id account();

  /** Indicates whether the user is included in or exlcuded from from the attention set. */
  public abstract boolean removal();

  /** A short human readable reason that explains this status (e.g. "manual"). */
  public abstract String reason();

  public static AttentionStatus create(Account.Id account, boolean removal, String reason) {
    return new AutoValue_AttentionStatus(account, removal, reason);
  }
}
