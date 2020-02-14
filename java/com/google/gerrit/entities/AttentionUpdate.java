package com.google.gerrit.entities;

import org.eclipse.jgit.lib.ObjectId;

/** tödö */
public class AttentionUpdate {  // ö Should this be AttentionSetEntry, mapped directly to a json line?
  public final int user;  // ö
  public final String reason;
  public final boolean removal = false;
  public AttentionUpdate(int user, String reason) {
    this.user = user;
    this.reason = reason;
  }
}
