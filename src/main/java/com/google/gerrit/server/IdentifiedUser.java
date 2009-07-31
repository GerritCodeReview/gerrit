// Copyright 2009 Google Inc. All Rights Reserved.

package com.google.gerrit.server;

import com.google.gerrit.client.reviewdb.Account;

/** An authenticated user. */
public class IdentifiedUser extends CurrentUser {
  private final Account.Id accountId;

  public IdentifiedUser(final Account.Id i) {
    accountId = i;
  }

  /** The account identity for the user. */
  public Account.Id getAccountId() {
    return accountId;
  }

  @Override
  public String toString() {
    return "CurrentUser[" + getAccountId() + "]";
  }
}