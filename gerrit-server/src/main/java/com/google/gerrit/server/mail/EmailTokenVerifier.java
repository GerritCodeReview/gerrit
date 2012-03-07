// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.gerrit.server.mail;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AuthRequest;

/** Verifies the token sent by {@link RegisterNewEmailSender}. */
public interface EmailTokenVerifier {
  /**
   * Construct a token to verify an email address for a user.
   *
   * @param accountId the caller that wants to add an email to their account.
   * @param emailAddress the address to add.
   * @return an unforgeable string to email to {@code emailAddress}. Presenting
   *         the string provides proof the user has the ability to read messages
   *         sent to that address.
   */
  public String encode(Account.Id accountId, String emailAddress);

  /**
   * Decode a token previously created.
   * @param tokenString the string created by encode.
   * @return a pair of account id and email address.
   * @throws InvalidTokenException the token is invalid, expired, malformed, etc.
   */
  public ParsedToken decode(String tokenString) throws InvalidTokenException;

  /** Exception thrown when a token does not parse correctly. */
  public static class InvalidTokenException extends Exception {
    public InvalidTokenException() {
      super("Invalid token");
    }

    public InvalidTokenException(Throwable cause) {
      super("Invalid token", cause);
    }
  }

  /** Pair returned from decode to provide the data used during encode. */
  public static class ParsedToken {
    private final Account.Id accountId;
    private final String emailAddress;

    public ParsedToken(Account.Id accountId, String emailAddress) {
      this.accountId = accountId;
      this.emailAddress = emailAddress;
    }

    public Account.Id getAccountId() {
      return accountId;
    }

    public String getEmailAddress() {
      return emailAddress;
    }

    public AuthRequest toAuthRequest() {
      return AuthRequest.forEmail(getEmailAddress());
    }

    @Override
    public String toString() {
      return accountId + " adds " + emailAddress;
    }
  }
}
