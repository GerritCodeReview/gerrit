// Copyright (C) 2018 The Android Open Source Project
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

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * Extension point to automatically create missing accounts in Gerrit.
 *
 * <p>If in a request a user is specified for which Gerrit doens't find an account the request is
 * rejected. By implementing the AutoAccountCreator interface it is possible to automatically create
 * an account in this case so that the request can succeed.
 *
 * <p>This extension point is intended to be implemented if users are stored in an external backend
 * (e.g. LDAP server). In this case Gerrit accounts can be created automatically for users that
 * don't have an account in Gerrit yet, but which exist in the user backend.
 */
@ExtensionPoint
public interface AutoAccountCreator {
  /**
   * Invoked by Gerrit if a user ID cannot be resolved to an account to possibly create an account
   * for this user automatically.
   *
   * @param userId The user ID.
   * @return The created account wrapped in an {@link Optional}, {@link Optional#empty()} if no
   *     account was created.
   */
  Optional<Account.Id> createAccount(String userId)
      throws IOException, OrmException, ConfigInvalidException;
}
