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

package com.google.gerrit.httpd;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.mail.RegisterNewEmailSender;

/** Verifies the token sent by {@link RegisterNewEmailSender}. */
public interface RestTokenVerifier {
  /**
   * Construct a token to verify a REST PUT request.
   *
   * @param user the caller that wants to make a PUT request
   * @param url the URL being requested
   * @return an unforgeable string to send to the user as the body of a GET
   *         request. Presenting the string in a follow-up POST request provides
   *         proof the user has the ability to read messages sent to thier
   *         browser and they likely aren't making the request via XSRF.
   */
  public String sign(Account.Id user, String url);

  /**
   * Decode a token previously created.
   *
   * @param user the user making the verify request.
   * @param url the url user is attempting to access.
   * @param token the string created by sign.
   * @throws InvalidTokenException the token is invalid, expired, malformed,
   *         etc.
   */
  public void verify(Account.Id user, String url, String token)
      throws InvalidTokenException;

  /** Exception thrown when a token does not parse correctly. */
  public static class InvalidTokenException extends Exception {
    private static final long serialVersionUID = 1L;

    public InvalidTokenException() {
      super("Invalid token");
    }

    public InvalidTokenException(Throwable cause) {
      super("Invalid token", cause);
    }
  }
}
