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

import com.google.gerrit.extensions.client.AccountFieldName;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.IdentifiedUser;
import java.io.IOException;
import java.util.Set;

public interface Realm {
  /** Can the end-user modify this field of their own account? */
  boolean allowsEdit(AccountFieldName field);

  /** Returns the account fields that the end-user can modify. */
  Set<AccountFieldName> getEditableFields();

  AuthRequest authenticate(AuthRequest who) throws AccountException;

  void onCreateAccount(AuthRequest who, Account account);

  /** @return true if the user has the given email address. */
  boolean hasEmailAddress(IdentifiedUser who, String email);

  /** @return all known email addresses for the identified user. */
  Set<String> getEmailAddresses(IdentifiedUser who);

  /**
   * Locate an account whose local username is the given account name.
   *
   * <p>Generally this only works for local realms, such as one backed by an LDAP directory, or
   * where there is an {@link EmailExpander} configured that knows how to convert the accountName
   * into an email address, and then locate the user by that email address.
   */
  Account.Id lookup(String accountName) throws IOException;
}
