package com.google.gerrit.entities;

/**
 * A single change to the attention set. To reconstruct the attention set these instances are parsed
 * in reverse chronological order, considering only the first record for each user.
 *
 * <p>See https://www.gerritcodereview.com/design-docs/attention-set.html for details.
 */
public class AttentionUpdate {  // ö Maybe "AttentionStatus" so it can be shared w/ readers?
  /** Account ID of the user that this update adds or removes. */
  public final int user; // ö Omit server ID? Cf. 233051

  /** A short human readable reason that explains why this update happened (e.g. "manual"). */
  public final String reason;

  /** Indicates whether the user was added to or removed from the attention set. */
  public final boolean removal;

  public AttentionUpdate(int user, String reason, boolean removal) {
    this.user = user;
    this.reason = reason;
    this.removal = removal;
  }
}
