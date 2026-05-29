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

import com.google.gerrit.common.UsedAt;
import com.google.gerrit.common.UsedAt.Project;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.InternalGroup;
import com.google.gerrit.exceptions.StorageException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;

/** Tracks group objects in memory for efficient access. */
public interface GroupCache {
  /**
   * Looks up an internal group by its ID.
   *
   * @param groupId the ID of the internal group
   * @return an {@code Optional} of the internal group, or an empty {@code Optional} if no internal
   *     group with this ID exists on this server or an error occurred during lookup
   */
  Optional<InternalGroup> get(AccountGroup.Id groupId);

  /**
   * Looks up an internal group by its name.
   *
   * @param name the name of the internal group
   * @return an {@code Optional} of the internal group, or an empty {@code Optional} if no internal
   *     group with this name exists on this server or an error occurred during lookup
   */
  Optional<InternalGroup> get(AccountGroup.NameKey name);

  /**
   * Looks up an internal group by its UUID.
   *
   * @param groupUuid the UUID of the internal group
   * @return an {@code Optional} of the internal group, or an empty {@code Optional} if no internal
   *     group with this UUID exists on this server or an error occurred during lookup
   */
  Optional<InternalGroup> get(AccountGroup.UUID groupUuid);

  /**
   * Returns a {@code Map} of {@code AccountGroup.UUID} to {@code InternalGroup} for the given
   * groups UUIDs. If not cached yet the groups are loaded. If a group can't be loaded (e.g. because
   * it is missing), the entry will be missing from the result.
   *
   * @param groupUuids UUIDs of the groups that should be retrieved
   * @return {@code Map} of {@code AccountGroup.UUID} to {@code InternalGroup} instances for the
   *     given group UUIDs, if a group can't be loaded (e.g. because it is missing), the entry will
   *     be missing from the result.
   */
  Map<AccountGroup.UUID, InternalGroup> get(Collection<AccountGroup.UUID> groupUuids);

  /**
   * Returns an {@code InternalGroup} instance for the given {@code AccountGroup.UUID} at the given
   * {@code metaId} of {@link com.google.gerrit.entities.RefNames#refsGroups} ref.
   *
   * <p>The caller is responsible to ensure the presence of {@code metaId} and the corresponding
   * meta ref.
   *
   * @param groupUuid the UUID of the internal group
   * @param metaId the sha1 of commit in {@link com.google.gerrit.entities.RefNames#refsGroups} ref.
   * @return the internal group at specific sha1 {@code metaId}
   * @throws StorageException if no internal group with this UUID exists on this server at the
   *     specific sha1, or if an error occurred during lookup.
   */
  @UsedAt(Project.GOOGLE)
  InternalGroup getFromMetaId(AccountGroup.UUID groupUuid, ObjectId metaId) throws StorageException;

  /**
   * Removes the association of the given ID with a group.
   *
   * <p>The next call to {@link #get(AccountGroup.Id)} won't provide a cached value.
   *
   * <p>It's safe to call this method if no association exists.
   *
   * <p><strong>Note: </strong>This method doesn't touch any associations between names/UUIDs and
   * groups!
   *
   * @param groupId the ID of a possibly associated group
   */
  void evict(AccountGroup.Id groupId);

  /**
   * Removes the association of the given name with a group.
   *
   * <p>The next call to {@link #get(AccountGroup.NameKey)} won't provide a cached value.
   *
   * <p>It's safe to call this method if no association exists.
   *
   * <p><strong>Note: </strong>This method doesn't touch any associations between IDs/UUIDs and
   * groups!
   *
   * @param groupName the name of a possibly associated group
   */
  void evict(AccountGroup.NameKey groupName);

  /**
   * Removes the association of the given UUID with a group.
   *
   * <p>The next call to {@link #get(AccountGroup.UUID)} won't provide a cached value.
   *
   * <p>It's safe to call this method if no association exists.
   *
   * <p><strong>Note: </strong>This method doesn't touch any associations between names/IDs and
   * groups!
   *
   * @param groupUuid the UUID of a possibly associated group
   */
  void evict(AccountGroup.UUID groupUuid);

  /**
   * Removes the association of the given UUIDs with groups
   *
   * <p>See {@link #evict(AccountGroup.UUID)}
   */
  void evict(Collection<AccountGroup.UUID> groupUuid);
}
