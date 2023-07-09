// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.update;

import static java.util.Objects.requireNonNull;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.change.NotifyResolver;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Context for performing a {@link BatchUpdate}.
 *
 * <p>A single update may span multiple changes, but they all belong to a single repo.
 */
public interface Context {
  /**
   * Get the project name this update operates on.
   *
   * @return project.
   */
  Project.NameKey getProject();

  /**
   * Get a read-only view of the open repository for this project.
   *
   * <p>Will be opened lazily if necessary.
   *
   * @return repository instance.
   * @throws IOException if an error occurred opening the repo.
   */
  RepoView getRepoView() throws IOException;

  /**
   * Get a walk for this project.
   *
   * <p>The repository will be opened lazily if necessary; callers should not close the walk.
   *
   * @return walk.
   * @throws IOException if an error occurred opening the repo.
   */
  RevWalk getRevWalk() throws IOException;

  /**
   * Get the timestamp at which this update takes place.
   *
   * @return timestamp.
   */
  Instant getWhen();

  /**
   * Get the time zone ID in which this update takes place.
   *
   * <p>In the current implementation, this is always the time zone ID of the server.
   *
   * @return zone ID.
   */
  ZoneId getZoneId();

  /**
   * Get the user performing the update.
   *
   * <p>In the current implementation, this is always an {@link IdentifiedUser} or {@link
   * com.google.gerrit.server.InternalUser}.
   *
   * @return user.
   */
  CurrentUser getUser();

  /**
   * Get the notification settings configured by the caller.
   *
   * <p>If there are multiple changes in a batch, they may have different settings. For example, WIP
   * changes may have reduced {@code NotifyHandling} levels, and may be in a batch with non-WIP
   * changes.
   *
   * @param changeId change ID
   * @return notification settings.
   */
  NotifyResolver.Result getNotify(Change.Id changeId);

  /**
   * Get the identified user performing the update.
   *
   * <p>Convenience method for {@code getUser().asIdentifiedUser()}.
   *
   * @see CurrentUser#asIdentifiedUser()
   * @return user.
   */
  default IdentifiedUser getIdentifiedUser() {
    return requireNonNull(getUser()).asIdentifiedUser();
  }

  /**
   * Get the account of the user performing the update.
   *
   * <p>Convenience method for {@code getIdentifiedUser().account()}.
   *
   * @see CurrentUser#asIdentifiedUser()
   * @return account.
   */
  default AccountState getAccount() {
    return getIdentifiedUser().state();
  }

  /**
   * Get the account ID of the user performing the update.
   *
   * <p>Convenience method for {@code getUser().getAccountId()}
   *
   * @see CurrentUser#getAccountId()
   * @return account ID.
   */
  default Account.Id getAccountId() {
    return getIdentifiedUser().getAccountId();
  }

  /**
   * Creates a new {@link PersonIdent} with {@link #getWhen()} as timestamp.
   *
   * @param personIdent {@link PersonIdent} to be copied
   * @return copied {@link PersonIdent} with {@link #getWhen()} as timestamp
   */
  default PersonIdent newPersonIdent(PersonIdent personIdent) {
    return new PersonIdent(personIdent, getWhen().toEpochMilli(), personIdent.getTimeZoneOffset());
  }

  /**
   * Creates a committer {@link PersonIdent} for {@link #getIdentifiedUser()}.
   *
   * @return the created committer {@link PersonIdent}
   */
  default PersonIdent newCommitterIdent() {
    return newCommitterIdent(getIdentifiedUser());
  }

  /**
   * Creates a committer {@link PersonIdent} for the given user.
   *
   * @param user user for which a committer {@link PersonIdent} should be created
   * @return the created committer {@link PersonIdent}
   */
  default PersonIdent newCommitterIdent(IdentifiedUser user) {
    return user.newCommitterIdent(getWhen(), getZoneId());
  }

  /**
   * Creates a committer {@link PersonIdent} for the given user. The identity will be created with
   * the given email if the user is allowed to use it, otherwise fallback to preferred email.
   *
   * @param user user for which a committer {@link PersonIdent} should be created
   * @param email committer email of the source commit
   * @return the created committer {@link PersonIdent}
   */
  default PersonIdent newCommitterIdent(String email, IdentifiedUser user) {
    return user.newCommitterIdent(email, getWhen(), getZoneId()).orElseGet(this::newCommitterIdent);
  }
}
