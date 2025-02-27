// Copyright (C) 2025 The Android Open Source Project
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

import com.google.gerrit.entities.Account;

public class AuthTokenConflictException extends InvalidAuthTokenException {
  private static final long serialVersionUID = 1L;

  public AuthTokenConflictException(String id, Account.Id accountId) {
    super(message(id, accountId));
  }

  public AuthTokenConflictException(String id, Account.Id accountId, Throwable cause) {
    super(message(id, accountId), cause);
  }

  private static String message(String id, Account.Id accountId) {
    return String.format("A token with id %s already exists for account %d.", id, accountId.get());
  }
}
