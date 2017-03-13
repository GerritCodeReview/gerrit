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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.TimeZone;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Context for performing a {@link BatchUpdate}.
 *
 * <p>A single update may span multiple changes, but they all belong to a single repo.
 */
public interface Context {
  /** @return the project name this update operates on. */
  Project.NameKey getProject();

  /**
   * Get an open repository instance for this project.
   *
   * <p>Will be opened lazily if necessary; callers should not close the repo. In some phases of the
   * update, the repository might be read-only; see {@link BatchUpdateOp} for details.
   *
   * @return repository instance.
   * @throws IOException if an error occurred opening the repo.
   */
  Repository getRepository() throws IOException;

  /**
   * Get a walk for this project.
   *
   * <p>The repository will be opened lazily if necessary; callers should not close the walk.
   *
   * @return walk.
   * @throws IOException if an error occurred opening the repo.
   */
  RevWalk getRevWalk() throws IOException;

  /** @return the timestamp at which this update takes place. */
  Timestamp getWhen();

  /**
   * @return the time zone in which this update takes place. In the current implementation, this is
   *     always the time zone of the server.
   */
  TimeZone getTimeZone();

  /**
   * @return an open ReviewDb database. Callers should not manage transactions or call mutating
   *     methods on the Changes table. Mutations on other tables (including other entities in the
   *     change entity group) are fine.
   */
  ReviewDb getDb();

  /**
   * @return user performing the update. In the current implementation, this is always an {@link
   *     IdentifiedUser} or {@link com.google.gerrit.server.InternalUser}.
   */
  CurrentUser getUser();

  /** @return order in which operations are executed in this update. */
  Order getOrder();

  /**
   * @return identified user performing the update; throws an unchecked exception if the user is not
   *     an {@link IdentifiedUser}
   */
  default IdentifiedUser getIdentifiedUser() {
    return checkNotNull(getUser()).asIdentifiedUser();
  }

  /**
   * @return account of the user performing the update; throws if the user is not an {@link
   *     IdentifiedUser}
   */
  default Account getAccount() {
    return getIdentifiedUser().getAccount();
  }

  /**
   * @return account ID of the user performing the update; throws if the user is not an {@link
   *     IdentifiedUser}
   */
  default Account.Id getAccountId() {
    return getIdentifiedUser().getAccountId();
  }
}
