// Copyright (C) 2012 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.mail;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.mail.send.RegisterNewEmailSender;

/** Verifies the token sent by {@link RegisterNewEmailSender}. */
public interface EmailTokenVerifier {
  /**
   * Construct a token to verify an email address for a user.
   *
   * @param accountId the caller that wants to add an email to their account.
   * @param emailAddress the address to add.
   * @return an unforgeable string to email to {@code emailAddress}. Presenting the string provides
   *     proof the user has the ability to read messages sent to that address. Must not be null.
   */
  String encode(Account.Id accountId, String emailAddress);

  /**
   * Decode a token previously created.
   *
   * @param tokenString the string created by encode. Never null.
   * @return a pair of account id and email address.
   * @throws InvalidTokenException the token is invalid, expired, malformed, etc.
   */
  ParsedToken decode(String tokenString) throws InvalidTokenException;

  /** Exception thrown when a token does not parse correctly. */
  class InvalidTokenException extends Exception {
    private static final long serialVersionUID = 1L;

    public InvalidTokenException() {
      super("Invalid token");
    }

    public InvalidTokenException(Throwable cause) {
      super("Invalid token", cause);
    }
  }

  /** Pair returned from decode to provide the data used during encode. */
  class ParsedToken {
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
