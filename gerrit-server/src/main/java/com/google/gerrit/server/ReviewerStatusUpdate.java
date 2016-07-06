package com.google.gerrit.server;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.notedb.ReviewerStateInternal;

import java.sql.Timestamp;

/**
 * Change to a reviewer's status.
 */
public class ReviewerStatusUpdate {
  public Timestamp date;
  public Account.Id author;
  public ReviewerStateInternal state;

  public ReviewerStatusUpdate(Timestamp ts, Account.Id author, ReviewerStateInternal state) {
    this.date = ts;
    this.author = author;
    this.state = state;
  }
}
