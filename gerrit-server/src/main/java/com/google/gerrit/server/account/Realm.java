// Copyright (C) 2009 The Android Open Source Project
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

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;

import java.util.Set;

public interface Realm {
  /** Can the end-user modify this field of their own account? */
  public boolean allowsEdit(Account.FieldName field);

  public AuthRequest authenticate(AuthRequest who) throws AccountException;

  public void onCreateAccount(AuthRequest who, Account account);

  public Set<AccountGroup.UUID> groups(AccountState who);

  /**
   * Locate an account whose local username is the given account name.
   * <p>
   * Generally this only works for local realms, such as one backed by an LDAP
   * directory, or where there is an {@link EmailExpander} configured that knows
   * how to convert the accountName into an email address, and then locate the
   * user by that email address.
   */
  public Account.Id lookup(String accountName);

  /**
   * Search for matching external groups.
   */
  public Set<AccountGroup.ExternalNameKey> lookupGroups(String name);
}
