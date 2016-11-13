// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.api.accounts;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import java.util.List;

public interface AccountExternalIdCreator {

  /**
   * Returns additional external identifiers to assign to a given user when creating an account.
   *
   * @param id the identifier of the account.
   * @param username the name of the user.
   * @param email an optional email address to assign to the external identifiers, or {@code null}.
   * @return a list of external identifiers, or an empty list.
   */
  List<AccountExternalId> create(Account.Id id, String username, String email);
}
