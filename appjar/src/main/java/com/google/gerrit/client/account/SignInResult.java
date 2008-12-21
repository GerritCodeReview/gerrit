// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.account;

import com.google.gerrit.client.reviewdb.Account;

/** Result from a sign-in attempt via the LoginServlet. */
public class SignInResult {
  public static enum Status {
    /** The user canceled the sign-in and wasn't able to complete it */
    @SuppressWarnings("hiding")
    CANCEL,

    /** The sign-in was successful and we have the account data */
    SUCCESS,
  }

  /** Singleton representing {@link Status#CANCEL}. */
  public static final SignInResult CANCEL =
      new SignInResult(Status.CANCEL, null);

  protected Status status;
  protected Account account;

  protected SignInResult() {
  }

  /** Create a new result. */
  public SignInResult(final Status s, final Account a) {
    status = s;
    account = a;
  }

  /** The status of the login attempt */
  public Status getStatus() {
    return status;
  }

  /** The account data; only if {@link #getStatus()} is {@link Status#SUCCESS} */
  public Account getAccount() {
    return account;
  }
}
