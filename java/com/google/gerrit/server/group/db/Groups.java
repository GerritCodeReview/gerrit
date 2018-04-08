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

package com.google.gerrit.server.group.db;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupByIdAud;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.group.InternalGroup;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;

/**
 * A database accessor for read calls related to groups.
 *
 * <p>All calls which read group related details from the database are gathered here. Other classes
 * should always use this class instead of accessing the database directly. There are a few
 * exceptions though: schema classes, wrapper classes, and classes executed during init. The latter
 * ones should use {@code GroupsOnInit} instead.
 *
 * <p>Most callers should not need to read groups directly from the database; they should use the
 * {@link com.google.gerrit.server.account.GroupCache GroupCache} instead.
 *
 * <p>If not explicitly stated, all methods of this class refer to <em>internal</em> groups.
 */
@Singleton
public class Groups {
  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;
  private final AuditLogReader auditLogReader;

  @Inject
  public Groups(
      GitRepositoryManager repoManager, AllUsersName allUsersName, AuditLogReader auditLogReader) {
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.auditLogReader = auditLogReader;
  }

  /**
   * Returns the {@code InternalGroup} for the specified UUID if it exists.
   *
   * @param groupUuid the UUID of the group
   * @return the found {@code InternalGroup} if it exists, or else an empty {@code Optional}
   * @throws IOException if the group couldn't be retrieved from NoteDb
   * @throws ConfigInvalidException if the group couldn't be retrieved from NoteDb
   */
  public Optional<InternalGroup> getGroup(AccountGroup.UUID groupUuid)
      throws IOException, ConfigInvalidException {
    try (Repository allUsersRepo = repoManager.openRepository(allUsersName)) {
      return getGroupFromNoteDb(allUsersRepo, groupUuid);
    }
  }

  private static Optional<InternalGroup> getGroupFromNoteDb(
      Repository allUsersRepository, AccountGroup.UUID groupUuid)
      throws IOException, ConfigInvalidException {
    GroupConfig groupConfig = GroupConfig.loadForGroup(allUsersRepository, groupUuid);
    Optional<InternalGroup> loadedGroup = groupConfig.getLoadedGroup();
    if (loadedGroup.isPresent()) {
      // Check consistency with group name notes.
      GroupsNoteDbConsistencyChecker.ensureConsistentWithGroupNameNotes(
          allUsersRepository, loadedGroup.get());
    }
    return loadedGroup;
  }

  /**
   * Returns {@code GroupReference}s for all internal groups.
   *
   * @return a stream of the {@code GroupReference}s of all internal groups
   * @throws IOException if an error occurs while reading from NoteDb
   * @throws ConfigInvalidException if the data in NoteDb is in an incorrect format
   */
  public Stream<GroupReference> getAllGroupReferences() throws IOException, ConfigInvalidException {
    try (Repository allUsersRepo = repoManager.openRepository(allUsersName)) {
      return GroupNameNotes.loadAllGroups(allUsersRepo).stream();
    }
  }

  /**
   * Returns all known external groups. External groups are 'known' when they are specified as a
   * subgroup of an internal group.
   *
   * @return a stream of the UUIDs of the known external groups
   * @throws IOException if an error occurs while reading from NoteDb
   * @throws ConfigInvalidException if the data in NoteDb is in an incorrect format
   */
  public Stream<AccountGroup.UUID> getExternalGroups() throws IOException, ConfigInvalidException {
    try (Repository allUsersRepo = repoManager.openRepository(allUsersName)) {
      return getExternalGroupsFromNoteDb(allUsersRepo);
    }
  }

  private static Stream<AccountGroup.UUID> getExternalGroupsFromNoteDb(Repository allUsersRepo)
      throws IOException, ConfigInvalidException {
    ImmutableList<GroupReference> allInternalGroups = GroupNameNotes.loadAllGroups(allUsersRepo);
    ImmutableSet.Builder<AccountGroup.UUID> allSubgroups = ImmutableSet.builder();
    for (GroupReference internalGroup : allInternalGroups) {
      Optional<InternalGroup> group = getGroupFromNoteDb(allUsersRepo, internalGroup.getUUID());
      group.map(InternalGroup::getSubgroups).ifPresent(allSubgroups::addAll);
    }
    return allSubgroups
        .build()
        .stream()
        .filter(groupUuid -> !AccountGroup.isInternalGroup(groupUuid));
  }

  /**
   * Returns the membership audit records for a given group.
   *
   * @param repo All-Users repository.
   * @param groupUuid the UUID of the group
   * @return the audit records, in arbitrary order; empty if the group does not exist
   * @throws IOException if an error occurs while reading from NoteDb
   * @throws ConfigInvalidException if the group couldn't be retrieved from NoteDb
   */
  public List<AccountGroupMemberAudit> getMembersAudit(Repository repo, AccountGroup.UUID groupUuid)
      throws IOException, ConfigInvalidException {
    return auditLogReader.getMembersAudit(repo, groupUuid);
  }

  /**
   * Returns the subgroup audit records for a given group.
   *
   * @param repo All-Users repository.
   * @param groupUuid the UUID of the group
   * @return the audit records, in arbitrary order; empty if the group does not exist
   * @throws IOException if an error occurs while reading from NoteDb
   * @throws ConfigInvalidException if the group couldn't be retrieved from NoteDb
   */
  public List<AccountGroupByIdAud> getSubgroupsAudit(Repository repo, AccountGroup.UUID groupUuid)
      throws IOException, ConfigInvalidException {
    return auditLogReader.getSubgroupsAudit(repo, groupUuid);
  }
}
