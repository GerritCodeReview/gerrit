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

package com.google.gerrit.server;

import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountProjectWatch;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.server.config.AuthConfig;
import com.google.inject.servlet.RequestScoped;

import java.util.Collection;
import java.util.Set;

/**
 * Information about the currently logged in user.
 * <p>
 * This is a {@link RequestScoped} property managed by Guice.
 *
 * @see AnonymousUser
 * @see IdentifiedUser
 */
public abstract class CurrentUser {
  private final AccessPath accessPath;
  protected final AuthConfig authConfig;

  protected CurrentUser(final AccessPath accessPath, final AuthConfig authConfig) {
    this.accessPath = accessPath;
    this.authConfig = authConfig;
  }

  /** How this user is accessing the Gerrit Code Review application. */
  public final AccessPath getAccessPath() {
    return accessPath;
  }

  /**
   * Get the set of groups the user is currently a member of.
   * <p>
   * The returned set may be a subset of the user's actual groups; if the user's
   * account is currently deemed to be untrusted then the effective group set is
   * only the anonymous and registered user groups. To enable additional groups
   * (and gain their granted permissions) the user must update their account to
   * use only trusted authentication providers.
   *
   * @return active groups for this user.
   */
  public abstract Set<AccountGroup.UUID> getEffectiveGroups();

  /** Set of changes starred by this user. */
  public abstract Set<Change.Id> getStarredChanges();

  /** Filters selecting changes the user wants to monitor. */
  public abstract Collection<AccountProjectWatch> getNotificationFilters();

  /** Is the user a non-interactive user? */
  public boolean isBatchUser() {
    return getEffectiveGroups().contains(authConfig.getBatchUsersGroup());
  }

  public final boolean isAdministrator() {
    return getEffectiveGroups().contains(authConfig.getAdministratorsGroup());
  }
}
