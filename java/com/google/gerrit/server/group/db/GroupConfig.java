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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.VersionedMetaData;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;

// TODO(aliceks): Add Javadoc descriptions to this file.
public class GroupConfig extends VersionedMetaData {
  public static final String GROUP_CONFIG_FILE = "group.config";

  static final FooterKey FOOTER_ADD_MEMBER = new FooterKey("Add");
  static final FooterKey FOOTER_REMOVE_MEMBER = new FooterKey("Remove");
  static final FooterKey FOOTER_ADD_GROUP = new FooterKey("Add-group");
  static final FooterKey FOOTER_REMOVE_GROUP = new FooterKey("Remove-group");

  private static final String MEMBERS_FILE = "members";
  private static final String SUBGROUPS_FILE = "subgroups";
  private static final Pattern LINE_SEPARATOR_PATTERN = Pattern.compile("\\R");

  private final AccountGroup.UUID groupUuid;
  private final String ref;

  private Optional<InternalGroup> loadedGroup = Optional.empty();
  private Optional<InternalGroupCreation> groupCreation = Optional.empty();
  private Optional<InternalGroupUpdate> groupUpdate = Optional.empty();
  private Function<Account.Id, String> accountNameEmailRetriever = Account.Id::toString;
  private Function<AccountGroup.UUID, String> groupNameRetriever = AccountGroup.UUID::get;
  private boolean isLoaded = false;

  private GroupConfig(AccountGroup.UUID groupUuid) {
    this.groupUuid = checkNotNull(groupUuid);
    ref = RefNames.refsGroups(groupUuid);
  }

  public static GroupConfig createForNewGroup(
      Repository repository, InternalGroupCreation groupCreation)
      throws IOException, ConfigInvalidException, OrmDuplicateKeyException {
    GroupConfig groupConfig = new GroupConfig(groupCreation.getGroupUUID());
    groupConfig.load(repository);
    groupConfig.setGroupCreation(groupCreation);
    return groupConfig;
  }

  public static GroupConfig loadForGroup(Repository repository, AccountGroup.UUID groupUuid)
      throws IOException, ConfigInvalidException {
    GroupConfig groupConfig = new GroupConfig(groupUuid);
    groupConfig.load(repository);
    return groupConfig;
  }

  public Optional<InternalGroup> getLoadedGroup() {
    checkLoaded();
    return loadedGroup;
  }

  void setGroupCreation(InternalGroupCreation groupCreation) throws OrmDuplicateKeyException {
    checkLoaded();
    if (loadedGroup.isPresent()) {
      throw new OrmDuplicateKeyException(String.format("Group %s already exists", groupUuid.get()));
    }

    this.groupCreation = Optional.of(groupCreation);
  }

  public void setGroupUpdate(
      InternalGroupUpdate groupUpdate,
      Function<Account.Id, String> accountNameEmailRetriever,
      Function<AccountGroup.UUID, String> groupNameRetriever) {
    this.groupUpdate = Optional.of(groupUpdate);
    this.accountNameEmailRetriever = accountNameEmailRetriever;
    this.groupNameRetriever = groupNameRetriever;
  }

  @Override
  protected String getRefName() {
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

    Timestamp createdOn;
    if (groupCreation.isPresent()) {
      createdOn = groupCreation.get().getCreatedOn();
      commit.setAuthor(new PersonIdent(commit.getAuthor(), createdOn));
      commit.setCommitter(new PersonIdent(commit.getCommitter(), createdOn));
    } else {
      checkState(loadedGroup.isPresent(), "Cannot update non-existent group %s", groupUuid.get());
      createdOn = loadedGroup.get().getCreatedOn();
    }

    Config config = updateGroupProperties();

    ImmutableSet<Account.Id> originalMembers =
        loadedGroup.map(InternalGroup::getMembers).orElseGet(ImmutableSet::of);
    Optional<ImmutableSet<Account.Id>> updatedMembers = updateMembers(originalMembers);

    ImmutableSet<AccountGroup.UUID> originalSubgroups =
        loadedGroup.map(InternalGroup::getSubgroups).orElseGet(ImmutableSet::of);
    Optional<ImmutableSet<AccountGroup.UUID>> updatedSubgroups = updateSubgroups(originalSubgroups);

    String commitMessage =
        createCommitMessage(originalMembers, updatedMembers, originalSubgroups, updatedSubgroups);
    commit.setMessage(commitMessage);

    loadedGroup =
        Optional.of(
            createFrom(
                groupUuid,
                config,
                updatedMembers.orElse(originalMembers),
                updatedSubgroups.orElse(originalSubgroups),
                createdOn,
                null));
    groupCreation = Optional.empty();

    return true;
  }

  private void checkLoaded() {
    checkState(isLoaded, "Group %s not loaded yet", groupUuid.get());
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
    String fileContent = elements.stream().map(toStringFunction).collect(Collectors.joining("\n"));
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

  private String createCommitMessage(
      ImmutableSet<Account.Id> originalMembers,
      Optional<ImmutableSet<Account.Id>> updatedMembers,
      ImmutableSet<AccountGroup.UUID> originalSubgroups,
      Optional<ImmutableSet<AccountGroup.UUID>> updatedSubgroups) {
    String summaryLine = groupCreation.isPresent() ? "Create group" : "Update group";

    StringJoiner footerJoiner = new StringJoiner("\n", "\n\n", "");
    footerJoiner.setEmptyValue("");
    getCommitFooterForRename().ifPresent(footerJoiner::add);
    updatedMembers.ifPresent(
        newMembers ->
            getCommitFootersForMemberModifications(originalMembers, newMembers)
                .forEach(footerJoiner::add));
    updatedSubgroups.ifPresent(
        newSubgroups ->
            getCommitFootersForSubgroupModifications(originalSubgroups, newSubgroups)
                .forEach(footerJoiner::add));
    String footer = footerJoiner.toString();

    return summaryLine + footer;
  }

  private Optional<String> getCommitFooterForRename() {
    if (!loadedGroup.isPresent()
        || !groupUpdate.isPresent()
        || !groupUpdate.get().getName().isPresent()) {
      return Optional.empty();
    }

    String originalName = loadedGroup.get().getName();
    String newName = groupUpdate.get().getName().get().get();
    if (originalName.equals(newName)) {
      return Optional.empty();
    }
    return Optional.of("Rename from " + originalName + " to " + newName);
  }

  private Stream<String> getCommitFootersForMemberModifications(
      ImmutableSet<Account.Id> oldMembers, ImmutableSet<Account.Id> newMembers) {
    Stream<String> removedMembers =
        Sets.difference(oldMembers, newMembers)
            .stream()
            .map(accountNameEmailRetriever)
            .map((FOOTER_REMOVE_MEMBER.getName() + ": ")::concat);
    Stream<String> addedMembers =
        Sets.difference(newMembers, oldMembers)
            .stream()
            .map(accountNameEmailRetriever)
            .map((FOOTER_ADD_MEMBER.getName() + ": ")::concat);
    return Stream.concat(removedMembers, addedMembers);
  }

  private Stream<String> getCommitFootersForSubgroupModifications(
      ImmutableSet<AccountGroup.UUID> oldSubgroups, ImmutableSet<AccountGroup.UUID> newSubgroups) {
    Stream<String> removedMembers =
        Sets.difference(oldSubgroups, newSubgroups)
            .stream()
            .map(groupNameRetriever)
            .map((FOOTER_REMOVE_GROUP.getName() + ": ")::concat);
    Stream<String> addedMembers =
        Sets.difference(newSubgroups, oldSubgroups)
            .stream()
            .map(groupNameRetriever)
            .map((FOOTER_ADD_GROUP.getName() + ": ")::concat);
    return Stream.concat(removedMembers, addedMembers);
  }
}
