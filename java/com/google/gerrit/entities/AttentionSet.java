package com.google.gerrit.entities;

import org.eclipse.jgit.lib.ObjectId;

/** ö redo data structures */
public class AttentionSet {
  public final String ad;
  public final ObjectId commitId;
  public AttentionSet(String ad, ObjectId commitId) {
    this.ad = ad;
    this.commitId = commitId;
  }
}
