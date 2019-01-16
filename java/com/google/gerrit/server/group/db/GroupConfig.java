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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.gerrit.exceptions.DuplicateKeyException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.git.meta.VersionedMetaData;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.util.time.TimeUtil;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;

/**
 * A representation of a group in NoteDb.
 *
 * <p>Groups in NoteDb can be created by following the descriptions of {@link
 * #createForNewGroup(Project.NameKey, Repository, InternalGroupCreation)}. For reading groups from
 * NoteDb or updating them, refer to {@link #loadForGroup(Project.NameKey, Repository,
 * AccountGroup.UUID)} or {@link #loadForGroupSnapshot(Project.NameKey, Repository,
 * AccountGroup.UUID, ObjectId)}.
 *
 * <p><strong>Note:</strong> Any modification (group creation or update) only becomes permanent (and
 * hence written to NoteDb) if {@link #commit(MetaDataUpdate)} is called.
 *
 * <p><strong>Warning:</strong> This class is a low-level API for groups in NoteDb. Most code which
 * deals with internal Gerrit groups should use {@link Groups} or {@link GroupsUpdate} instead.
 *
 * <h2>Internal details</h2>
 *
 * <p>Each group is represented by a commit on a branch as defined by {@link
 * RefNames#refsGroups(AccountGroup.UUID)}. Previous versions of the group exist as older commits on
 * the same branch and can be reached by following along the parent references. New commits for
 * updates are only created if a real modification occurs.
 *
 * <p>The commit messages of all commits on that branch form the audit log for the group. The
 * messages mention any important modifications which happened for the group to avoid costly
 * computations.
 *
 * <p>Within each commit, the properties of a group are spread across three files:
 *
 * <ul>
 *   <li><em>group.config</em>, which holds all basic properties of a group (further specified by
 *       {@link GroupConfigEntry}), formatted as a JGit {@link Config} file
 *   <li><em>members</em>, which lists all members (accounts) of a group, formatted as one numeric
 *       ID per line
 *   <li><em>subgroups</em>, which lists all subgroups of a group, formatted as one UUID per line
 * </ul>
 *
 * <p>The files <em>members</em> and <em>subgroups</em> need not exist, which means that the group
 * doesn't have any members or subgroups.
 */
public class GroupConfig extends VersionedMetaData {
  @VisibleForTesting public static final String GROUP_CONFIG_FILE = "group.config";
  @VisibleForTesting static final String MEMBERS_FILE = "members";
  @VisibleForTesting static final String SUBGROUPS_FILE = "subgroups";
  private static final Pattern LINE_SEPARATOR_PATTERN = Pattern.compile("\\R");

  /**
   * Creates a {@code GroupConfig} for a new group from the {@code InternalGroupCreation} blueprint.
   * Further, optional properties can be specified by setting an {@code InternalGroupUpdate} via
   * {@link #setGroupUpdate(InternalGroupUpdate, AuditLogFormatter)} on the returned {@code
   * GroupConfig}.
   *
   * <p><strong>Note:</strong> The returned {@code GroupConfig} has to be committed via {@link
   * #commit(MetaDataUpdate)} in order to create the group for real.
   *
   * @param projectName the name of the project which holds the NoteDb commits for groups
   * @param repository the repository which holds the NoteDb commits for groups
   * @param groupCreation an {@code InternalGroupCreation} specifying all properties which are
   *     required for a new group
   * @return a {@code GroupConfig} for a group creation
   * @throws IOException if the repository can't be accessed for some reason
   * @throws ConfigInvalidException if a group with the same UUID already exists but can't be read
   *     due to an invalid format
   * @throws DuplicateKeyException if a group with the same UUID already exists
   */
  public static GroupConfig createForNewGroup(
      Project.NameKey projectName, Repository repository, InternalGroupCreation groupCreation)
      throws IOException, ConfigInvalidException, DuplicateKeyException {
    GroupConfig groupConfig = new GroupConfig(groupCreation.getGroupUUID());
    groupConfig.load(projectName, repository);
    groupConfig.setGroupCreation(groupCreation);
    return groupConfig;
  }

  /**
   * Creates a {@code GroupConfig} for an existing group.
   *
   * <p>The group is automatically loaded within this method and can be accessed via {@link
   * #getLoadedGroup()}.
   *
   * <p>It's safe to call this method for non-existing groups. In that case, {@link
   * #getLoadedGroup()} won't return any group. Thus, the existence of a group can be easily tested.
   *
   * <p>The group represented by the returned {@code GroupConfig} can be updated by setting an
   * {@code InternalGroupUpdate} via {@link #setGroupUpdate(InternalGroupUpdate, AuditLogFormatter)}
   * and committing the {@code GroupConfig} via {@link #commit(MetaDataUpdate)}.
   *
   * @param projectName the name of the project which holds the NoteDb commits for groups
   * @param repository the repository which holds the NoteDb commits for groups
   * @param groupUuid the UUID of the group
   * @return a {@code GroupConfig} for the group with the specified UUID
   * @throws IOException if the repository can't be accessed for some reason
   * @throws ConfigInvalidException if the group exists but can't be read due to an invalid format
   */
  public static GroupConfig loadForGroup(
      Project.NameKey projectName, Repository repository, AccountGroup.UUID groupUuid)
      throws IOException, ConfigInvalidException {
    GroupConfig groupConfig = new GroupConfig(groupUuid);
    groupConfig.load(projectName, repository);
    return groupConfig;
  }

  /**
   * Creates a {@code GroupConfig} for an existing group at a specific revision of the repository.
   *
   * <p>This method behaves nearly the same as {@link #loadForGroup(Project.NameKey, Repository,
   * AccountGroup.UUID)}. The only difference is that {@link #loadForGroup(Project.NameKey,
   * Repository, AccountGroup.UUID)} loads the group from the current state of the repository
   * whereas this method loads the group at a specific (maybe past) revision.
   *
   * @param projectName the name of the project which holds the NoteDb commits for groups
   * @param repository the repository which holds the NoteDb commits for groups
   * @param groupUuid the UUID of the group
   * @param commitId the revision of the repository at which the group should be loaded
   * @return a {@code GroupConfig} for the group with the specified UUID
   * @throws IOException if the repository can't be accessed for some reason
   * @throws ConfigInvalidException if the group exists but can't be read due to an invalid format
   */
  public static GroupConfig loadForGroupSnapshot(
      Project.NameKey projectName,
      Repository repository,
      AccountGroup.UUID groupUuid,
      ObjectId commitId)
      throws IOException, ConfigInvalidException {
    GroupConfig groupConfig = new GroupConfig(groupUuid);
    groupConfig.load(projectName, repository, commitId);
    return groupConfig;
  }

  private final AccountGroup.UUID groupUuid;
  private final String ref;

  private Optional<InternalGroup> loadedGroup = Optional.empty();
  private Optional<InternalGroupCreation> groupCreation = Optional.empty();
  private Optional<InternalGroupUpdate> groupUpdate = Optional.empty();
  private AuditLogFormatter auditLogFormatter = AuditLogFormatter.createPartiallyWorkingFallBack();
  private boolean isLoaded = false;
  private boolean allowSaveEmptyName;

  private GroupConfig(AccountGroup.UUID groupUuid) {
    this.groupUuid = requireNonNull(groupUuid);
    ref = RefNames.refsGroups(groupUuid);
  }

  /**
   * Returns the group loaded from NoteDb.
   *
   * <p>If not any NoteDb commits exist for the group represented by this {@code GroupConfig}, no
   * group is returned.
   *
   * <p>After {@link #commit(MetaDataUpdate)} was called on this {@code GroupConfig}, this method
   * returns a group which is in line with the latest NoteDb commit for this group. So, after
   * creating a {@code GroupConfig} for a new group and committing it, this method can be used to
   * retrieve a representation of the created group. The same holds for the representation of an
   * updated group.
   *
   * @return the loaded group, or an empty {@code Optional} if the group doesn't exist
   */
  public Optional<InternalGroup> getLoadedGroup() {
    checkLoaded();
    return loadedGroup;
  }

  /**
   * Specifies how the current group should be updated.
   *
   * <p>If the group is newly created, the {@code InternalGroupUpdate} can be used to specify
   * optional properties.
   *
   * <p><strong>Note:</strong> This method doesn't perform the update. It only contains the
   * instructions for the update. To apply the update for real and write the result back to NoteDb,
   * call {@link #commit(MetaDataUpdate)} on this {@code GroupConfig}.
   *
   * @param groupUpdate an {@code InternalGroupUpdate} outlining the modifications which should be
   *     applied
   * @param auditLogFormatter an {@code AuditLogFormatter} for formatting the commit message in a
   *     parsable way
   */
  public void setGroupUpdate(InternalGroupUpdate groupUpdate, AuditLogFormatter auditLogFormatter) {
    this.groupUpdate = Optional.of(groupUpdate);
    this.auditLogFormatter = auditLogFormatter;
  }

  /**
   * Allows the new name of a group to be empty during creation or update.
   *
   * <p><strong>Note:</strong> This method exists only to support the migration of legacy groups
   * which don't always necessarily have a name. Nowadays, we enforce that groups always have names.
   * When we remove the migration code, we can probably remove this method as well.
   */
  public void setAllowSaveEmptyName() {
    this.allowSaveEmptyName = true;
  }

  private void setGroupCreation(InternalGroupCreation groupCreation) throws DuplicateKeyException {
    checkLoaded();
    if (loadedGroup.isPresent()) {
      throw new DuplicateKeyException(String.format("Group %s already exists", groupUuid.get()));
    }

    this.groupCreation = Optional.of(groupCreation);
  }

  @Override
  public String getRefName() {
    return ref;
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    if (revision != null) {
      rw.reset();
      rw.markStart(revision);
      rw.sort(RevSort.REVERSE);
      RevCommit earliestCommit = rw.next();
      Timestamp createdOn = new Timestamp(earliestCommit.getCommitTime() * 1000L);

      Config config = readConfig(GROUP_CONFIG_FILE);
      ImmutableSet<Account.Id> members = readMembers();
      ImmutableSet<AccountGroup.UUID> subgroups = readSubgroups();
      loadedGroup =
          Optional.of(
              createFrom(groupUuid, config, members, subgroups, createdOn, revision.toObjectId()));
    }

    isLoaded = true;
  }

  @Override
  public RevCommit commit(MetaDataUpdate update) throws IOException {
    RevCommit c = super.commit(update);
    loadedGroup = Optional.of(loadedGroup.get().toBuilder().setRefState(c.toObjectId()).build());
    return c;
  }

  @Override
  protected boolean onSave(CommitBuilder commit) throws IOException, ConfigInvalidException {
    checkLoaded();
    if (!groupCreation.isPresent() && !groupUpdate.isPresent()) {
      // Group was neither created nor changed. -> A new commit isn't necessary.
      return false;
    }

    if (!allowSaveEmptyName && getNewName().equals(Optional.of(""))) {
      throw new ConfigInvalidException(
          String.format("Name of the group %s must be defined", groupUuid.get()));
    }

    // Commit timestamps are internally truncated to seconds. To return the correct 'createdOn' time
    // for new groups, we explicitly need to truncate the timestamp here.
    Timestamp commitTimestamp =
        TimeUtil.truncateToSecond(
            groupUpdate.flatMap(InternalGroupUpdate::getUpdatedOn).orElseGet(TimeUtil::nowTs));
    commit.setAuthor(new PersonIdent(commit.getAuthor(), commitTimestamp));
    commit.setCommitter(new PersonIdent(commit.getCommitter(), commitTimestamp));

    InternalGroup updatedGroup = updateGroup(commitTimestamp);

    String commitMessage = createCommitMessage(loadedGroup, updatedGroup);
    commit.setMessage(commitMessage);

    loadedGroup = Optional.of(updatedGroup);
    groupCreation = Optional.empty();
    groupUpdate = Optional.empty();

    return true;
  }

  private void checkLoaded() {
    checkState(isLoaded, "Group %s not loaded yet", groupUuid.get());
  }

  private Optional<String> getNewName() {
    if (groupUpdate.isPresent()) {
      return groupUpdate.get().getName().map(n -> Strings.nullToEmpty(n.get()));
    }
    if (groupCreation.isPresent()) {
      return Optional.of(Strings.nullToEmpty(groupCreation.get().getNameKey().get()));
    }
    return Optional.empty();
  }

  private InternalGroup updateGroup(Timestamp commitTimestamp)
      throws IOException, ConfigInvalidException {
    Config config = updateGroupProperties();

    ImmutableSet<Account.Id> originalMembers =
        loadedGroup.map(InternalGroup::getMembers).orElseGet(ImmutableSet::of);
    Optional<ImmutableSet<Account.Id>> updatedMembers = updateMembers(originalMembers);

    ImmutableSet<AccountGroup.UUID> originalSubgroups =
        loadedGroup.map(InternalGroup::getSubgroups).orElseGet(ImmutableSet::of);
    Optional<ImmutableSet<AccountGroup.UUID>> updatedSubgroups = updateSubgroups(originalSubgroups);

    Timestamp createdOn = loadedGroup.map(InternalGroup::getCreatedOn).orElse(commitTimestamp);

    return createFrom(
        groupUuid,
        config,
        updatedMembers.orElse(originalMembers),
        updatedSubgroups.orElse(originalSubgroups),
        createdOn,
        null);
  }

  private Config updateGroupProperties() throws IOException, ConfigInvalidException {
    Config config = readConfig(GROUP_CONFIG_FILE);
    groupCreation.ifPresent(
        internalGroupCreation ->
            Arrays.stream(GroupConfigEntry.values())
                .forEach(configEntry -> configEntry.initNewConfig(config, internalGroupCreation)));
    groupUpdate.ifPresent(
        internalGroupUpdate ->
            Arrays.stream(GroupConfigEntry.values())
                .forEach(
                    configEntry -> configEntry.updateConfigValue(config, internalGroupUpdate)));
    saveConfig(GROUP_CONFIG_FILE, config);
    return config;
  }

  private Optional<ImmutableSet<Account.Id>> updateMembers(ImmutableSet<Account.Id> originalMembers)
      throws IOException {
    Optional<ImmutableSet<Account.Id>> updatedMembers =
        groupUpdate
            .map(InternalGroupUpdate::getMemberModification)
            .map(memberModification -> memberModification.apply(originalMembers))
            .map(ImmutableSet::copyOf)
            .filter(members -> !originalMembers.equals(members));
    if (updatedMembers.isPresent()) {
      saveMembers(updatedMembers.get());
    }
    return updatedMembers;
  }

  private Optional<ImmutableSet<AccountGroup.UUID>> updateSubgroups(
      ImmutableSet<AccountGroup.UUID> originalSubgroups) throws IOException {
    Optional<ImmutableSet<AccountGroup.UUID>> updatedSubgroups =
        groupUpdate
            .map(InternalGroupUpdate::getSubgroupModification)
            .map(subgroupModification -> subgroupModification.apply(originalSubgroups))
            .map(ImmutableSet::copyOf)
            .filter(subgroups -> !originalSubgroups.equals(subgroups));
    if (updatedSubgroups.isPresent()) {
      saveSubgroups(updatedSubgroups.get());
    }
    return updatedSubgroups;
  }

  private void saveMembers(ImmutableSet<Account.Id> members) throws IOException {
    saveToFile(MEMBERS_FILE, members, member -> String.valueOf(member.get()));
  }

  private void saveSubgroups(ImmutableSet<AccountGroup.UUID> subgroups) throws IOException {
    saveToFile(SUBGROUPS_FILE, subgroups, AccountGroup.UUID::get);
  }

  private <E> void saveToFile(
      String filePath, ImmutableSet<E> elements, Function<E, String> toStringFunction)
      throws IOException {
    String fileContent = elements.stream().map(toStringFunction).collect(joining("\n"));
    saveUTF8(filePath, fileContent);
  }

  private ImmutableSet<Account.Id> readMembers() throws IOException, ConfigInvalidException {
    return readFromFile(MEMBERS_FILE, entry -> new Account.Id(Integer.parseInt(entry)));
  }

  private ImmutableSet<AccountGroup.UUID> readSubgroups()
      throws IOException, ConfigInvalidException {
    return readFromFile(SUBGROUPS_FILE, AccountGroup.UUID::new);
  }

  private <E> ImmutableSet<E> readFromFile(String filePath, Function<String, E> fromStringFunction)
      throws IOException, ConfigInvalidException {
    String fileContent = readUTF8(filePath);
    try {
      Iterable<String> lines =
          Splitter.on(LINE_SEPARATOR_PATTERN).trimResults().omitEmptyStrings().split(fileContent);
      return Streams.stream(lines).map(fromStringFunction).collect(toImmutableSet());
    } catch (NumberFormatException e) {
      throw new ConfigInvalidException(
          String.format("Invalid file %s for commit %s", filePath, revision.name()), e);
    }
  }

  private static InternalGroup createFrom(
      AccountGroup.UUID groupUuid,
      Config config,
      ImmutableSet<Account.Id> members,
      ImmutableSet<AccountGroup.UUID> subgroups,
      Timestamp createdOn,
      ObjectId refState)
      throws ConfigInvalidException {
    InternalGroup.Builder group = InternalGroup.builder();
    group.setGroupUUID(groupUuid);
    for (GroupConfigEntry configEntry : GroupConfigEntry.values()) {
      configEntry.readFromConfig(groupUuid, group, config);
    }
    group.setMembers(members);
    group.setSubgroups(subgroups);
    group.setCreatedOn(createdOn);
    group.setRefState(refState);
    return group.build();
  }

  private String createCommitMessage(
      Optional<InternalGroup> originalGroup, InternalGroup updatedGroup) {
    GroupConfigCommitMessage commitMessage =
        new GroupConfigCommitMessage(auditLogFormatter, updatedGroup);
    originalGroup.ifPresent(commitMessage::setOriginalGroup);
    return commitMessage.create();
  }
}
