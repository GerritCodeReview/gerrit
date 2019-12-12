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

import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.client.AccountFieldName;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.externalids.ExternalId;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import javax.naming.NamingException;
import javax.security.auth.login.LoginException;

/**
 * Interface between Gerrit and an account system.
 *
 * <p>This interface provides the glue layer between the Gerrit and external account/authentication
 * systems (eg. LDAP, OpenID).
 */
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

  /**
   * @return true if the account is active.
   * @throws NamingException
   * @throws LoginException
   * @throws AccountException
   * @throws IOException
   */
  default boolean isActive(@SuppressWarnings("unused") String username)
      throws LoginException, NamingException, AccountException, IOException {
    return true;
  }

  /** @return true if the account is backed by the realm, false otherwise. */
  default boolean accountBelongsToRealm(
      @SuppressWarnings("unused") Collection<ExternalId> externalIds) {
    return false;
  }
}
