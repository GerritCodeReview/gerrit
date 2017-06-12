// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.account;

/**
 * Thrown by {@link AccountManager} if the user name for a newly created account could not be set
 * and the realm does not allow the user to set a user name manually.
 */
public class AccountUserNameException extends AccountException {
  private static final long serialVersionUID = 1L;

  public AccountUserNameException(final String message, final Throwable why) {
    super(message, why);
  }
}
