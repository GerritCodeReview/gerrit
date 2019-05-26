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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.inject.servlet.RequestScoped;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Information about the currently logged in user.
 *
 * <p>This is a {@link RequestScoped} property managed by Guice.
 *
 * @see AnonymousUser
 * @see IdentifiedUser
 */
public abstract class CurrentUser {
  /** Unique key for plugin/extension specific data on a CurrentUser. */
  public static final class PropertyKey<T> {
    public static <T> PropertyKey<T> create() {
      return new PropertyKey<>();
    }

    private PropertyKey() {}
  }

  private AccessPath accessPath = AccessPath.UNKNOWN;
  private PropertyKey<ExternalId.Key> lastLoginExternalIdPropertyKey = PropertyKey.create();

  /** How this user is accessing the Gerrit Code Review application. */
  public final AccessPath getAccessPath() {
    return accessPath;
  }

  public void setAccessPath(AccessPath path) {
    accessPath = path;
  }

  /**
   * Identity of the authenticated user.
   *
   * <p>In the normal case where a user authenticates as themselves {@code getRealUser() == this}.
   *
   * <p>If {@code X-Gerrit-RunAs} or {@code suexec} was used this method returns the identity of the
   * account that has permission to act on behalf of this user.
   */
  public CurrentUser getRealUser() {
    return this;
  }

  public boolean isImpersonating() {
    return false;
  }

  /**
   * If the {@link #getRealUser()} has an account ID associated with it, call the given setter with
   * that ID.
   */
  public void updateRealAccountId(Consumer<Account.Id> setter) {
    if (getRealUser().isIdentifiedUser()) {
      setter.accept(getRealUser().getAccountId());
    }
  }

  /**
   * Get the set of groups the user is currently a member of.
   *
   * <p>The returned set may be a subset of the user's actual groups; if the user's account is
   * currently deemed to be untrusted then the effective group set is only the anonymous and
   * registered user groups. To enable additional groups (and gain their granted permissions) the
   * user must update their account to use only trusted authentication providers.
   *
   * @return active groups for this user.
   */
  public abstract GroupMembership getEffectiveGroups();

  /**
   * Returns a unique identifier for this user that is intended to be used as a cache key. Returned
   * object should to implement {@code equals()} and {@code hashCode()} for effective caching.
   */
  public abstract Object getCacheKey();

  /** Unique name of the user on this server, if one has been assigned. */
  public Optional<String> getUserName() {
    return Optional.empty();
  }

  /** @return unique name of the user for logging, never {@code null} */
  public String getLoggableName() {
    return getUserName().orElseGet(() -> getClass().getSimpleName());
  }

  /** Check if user is the IdentifiedUser */
  public boolean isIdentifiedUser() {
    return false;
  }

  /** Cast to IdentifiedUser if possible. */
  public IdentifiedUser asIdentifiedUser() {
    throw new UnsupportedOperationException(
        getClass().getSimpleName() + " is not an IdentifiedUser");
  }

  /**
   * Return account ID if {@link #isIdentifiedUser} is true.
   *
   * @throws UnsupportedOperationException if the user is not logged in.
   */
  public Account.Id getAccountId() {
    throw new UnsupportedOperationException(
        getClass().getSimpleName() + " is not an IdentifiedUser");
  }

  /** Check if the CurrentUser is an InternalUser. */
  public boolean isInternalUser() {
    return false;
  }

  /**
   * Lookup a previously stored property.
   *
   * @param key unique property key.
   * @return previously stored value, or {@code Optional#empty()}.
   */
  public <T> Optional<T> get(PropertyKey<T> key) {
    return Optional.empty();
  }

  /**
   * Store a property for later retrieval.
   *
   * @param key unique property key.
   * @param value value to store; or {@code null} to clear the value.
   */
  public <T> void put(PropertyKey<T> key, @Nullable T value) {}

  public void setLastLoginExternalIdKey(ExternalId.Key externalIdKey) {
    put(lastLoginExternalIdPropertyKey, externalIdKey);
  }

  public Optional<ExternalId.Key> getLastLoginExternalIdKey() {
    return get(lastLoginExternalIdPropertyKey);
  }

  /**
   * Checks if the current user has the same account id of another.
   *
   * <p>Provide a generic interface for allowing subclasses to define whether two accounts represent
   * the same account id.
   *
   * @param other user to compare
   * @return true if the two users have the same account id
   */
  public boolean hasSameAccountId(CurrentUser other) {
    return false;
  }
}
