// Copyright (C) 2023 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkState;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Account;
import java.time.Instant;
import java.time.ZoneId;
import org.eclipse.jgit.lib.PersonIdent;

/**
 * Extension point that allows to control which identity should be recorded in the reflog for ref
 * updates done by a user or done on behalf of a user.
 */
public interface RefLogIdentityProvider {
  /**
   * Creates a {@link PersonIdent} for the given user that should be used as the user identity in
   * the reflog for ref updates done by this user or done on behalf of this user.
   *
   * <p>The returned {@link PersonIdent} is created with the current timestamp and the system
   * default timezone.
   *
   * @param user the user for which a reflog identity should be created
   */
  default PersonIdent newRefLogIdent(IdentifiedUser user) {
    return newRefLogIdent(user, Instant.now(), ZoneId.systemDefault());
  }

  /**
   * Creates a {@link PersonIdent} for the given user that should be used as the user identity in
   * the reflog for ref updates done by this user or done on behalf of this user.
   *
   * @param user the user for which a reflog identity should be created
   * @param when the timestamp that should be used to create the {@link PersonIdent}
   * @param zoneId the zone ID identifying the timezone that should be used to create the {@link
   *     PersonIdent}
   */
  PersonIdent newRefLogIdent(IdentifiedUser user, Instant when, ZoneId zoneId);

  /**
   * Creates a {@link PersonIdent} for the given users that should be used as the user identity in
   * the reflog for ref updates done by these users or done on behalf of these users.
   *
   * <p>Usually ref updates are done by a single user or on behalf of a single user, but with {@link
   * com.google.gerrit.server.update.BatchUpdate} it's possible that updates of different users are
   * batched together into a single ref update.
   *
   * <p>If a single user is provided or all provided users reference the same account a reflog
   * identity for that user/account is created and returned.
   *
   * <p>If multiple users (that reference different accounts) are provided a shared reflog identity
   * is created and returned. The shared reflog identity lists all involved accounts. How the shared
   * reflog identity looks like doesn't matter much, as long as it's not the reflog identity of a
   * real user (e.g. if impersonated updates of multiple users are batched together it must not be
   * the reflog identity of the real user).
   *
   * @param users the users for which a reflog identity should be created
   * @param when the timestamp that should be used to create the {@link PersonIdent}
   * @param zoneId the zone ID identifying the timezone that should be used to create the {@link
   *     PersonIdent}
   */
  default PersonIdent newRefLogIdent(
      ImmutableList<IdentifiedUser> users, Instant when, ZoneId zoneId) {
    checkState(!users.isEmpty(), "expected at least one user");

    // If it's a single user create a reflog ident for that user.
    // Use IdentifiedUser.newReflogIdent(Instant, ZoneId) rather than invoking
    // #newRefLogIdent(IdentifiedUser, Instant ZoneId) directly, so that we can benefit from the
    // reflog ident caching in IdentifiedUser.
    if (users.size() == 1 || users.stream().allMatch(user -> user.hasSameAccountId(users.get(0)))) {
      return users.get(0).newRefLogIdent(when, zoneId);
    }

    // Multiple users (for different accounts) have been provided. Create a shared relog identity
    // that lists all involved accounts.
    String accounts =
        users.stream()
            .map(IdentifiedUser::getAccountId)
            .map(Account.Id::get)
            .distinct()
            .sorted()
            .map(id -> "account-" + id)
            .collect(joining("|"));
    return new PersonIdent(
        accounts, String.format("%s@%s", accounts, getDefaultDomain()), when, zoneId);
  }

  /**
   * Returns the default domain for constructing email addresses if guessing the correct host is not
   * possible.
   */
  default String getDefaultDomain() {
    return "unknown";
  }
}
