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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.git.VersionedMetaData;
import com.google.gerrit.server.group.InternalGroup;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;

// TODO(aliceks): Add Javadoc descriptions to this file.
public class GroupConfig extends VersionedMetaData {
  private static final String GROUP_CONFIG_FILE = "group.config";
  private static final String MEMBERS_FILE = "members";
  private static final String SUBGROUPS_FILE = "subgroups";

  private final AccountGroup.UUID groupUuid;
  private final String ref;

  private Optional<InternalGroup> loadedGroup = Optional.empty();
  private Optional<InternalGroupUpdate> groupUpdate = Optional.empty();
  private Function<Account.Id, String> accountNameEmailRetriever = Account.Id::toString;
  private Function<AccountGroup.UUID, String> groupNameRetriever = AccountGroup.UUID::get;
  private boolean isLoaded = false;

  private GroupConfig(AccountGroup.UUID groupUuid) {
    this.groupUuid = checkNotNull(groupUuid);
    ref = RefNames.refsGroups(groupUuid);
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
      rw.markStart(revision);
      rw.sort(RevSort.REVERSE);
      RevCommit earliestCommit = rw.next();
      Timestamp createdOn = new Timestamp(earliestCommit.getCommitTime() * 1000L);

      Config config = readConfig(GROUP_CONFIG_FILE);
      ImmutableSet<Account.Id> members = readMembers();
      ImmutableSet<AccountGroup.UUID> subgroups = readSubgroups();
      loadedGroup = Optional.of(createFrom(groupUuid, config, members, subgroups, createdOn));
    }

    isLoaded = true;
  }

  private static InternalGroup createFrom(
      AccountGroup.UUID groupUuid,
      Config config,
      ImmutableSet<Account.Id> members,
      ImmutableSet<AccountGroup.UUID> subgroups,
      Timestamp createdOn) {
    InternalGroup.Builder group = InternalGroup.builder();
    group.setGroupUUID(groupUuid);
    Arrays.stream(GroupConfigEntry.values())
        .forEach(configEntry -> configEntry.readFromConfig(group, config));
    group.setMembers(members);
    group.setSubgroups(subgroups);
    group.setCreatedOn(createdOn);
    return group.build();
  }

  @Override
  protected boolean onSave(CommitBuilder commit) throws IOException, ConfigInvalidException {
    checkLoaded();
    if (!groupUpdate.isPresent()) {
      // Group was not changed. -> A new commit isn't necessary.
      return false;
    }

    checkState(
        loadedGroup.isPresent(),
        String.format("Cannot update non-existent group %s", groupUuid.get()));
    Timestamp createdOn = loadedGroup.get().getCreatedOn();

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
                createdOn));

    return true;
  }

  private void checkLoaded() {
    checkState(isLoaded, String.format("Group %s not loaded yet", groupUuid.get()));
  }

  private Config updateGroupProperties() throws IOException, ConfigInvalidException {
    Config config = readConfig(GROUP_CONFIG_FILE);
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
            .map(ImmutableSet::copyOf);
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
            .map(ImmutableSet::copyOf);
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
    try (StringReader stringReader = new StringReader(fileContent);
        BufferedReader reader = new BufferedReader(stringReader)) {
      return reader
          .lines()
          .map(String::trim)
          .filter(line -> !line.isEmpty())
          .map(fromStringFunction)
          .collect(toImmutableSet());
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
    String summaryLine = "Update group";

    StringJoiner footerJoiner = new StringJoiner("\n", "\n\n", "");
    footerJoiner.setEmptyValue("");
    updatedMembers
        .map(newMembers -> getCommitFooterForMemberModifications(originalMembers, newMembers))
        .ifPresent(footerJoiner::add);
    updatedSubgroups
        .map(
            newSubgroups ->
                getCommitFooterForSubgroupModifications(originalSubgroups, newSubgroups))
        .ifPresent(footerJoiner::add);
    String footer = footerJoiner.toString();

    return summaryLine + footer;
  }

  private String getCommitFooterForMemberModifications(
      ImmutableSet<Account.Id> oldMembers, ImmutableSet<Account.Id> newMembers) {
    Stream<String> removedMembers =
        Sets.difference(oldMembers, newMembers)
            .stream()
            .map(accountNameEmailRetriever)
            .map("Remove: "::concat);
    Stream<String> addedMembers =
        Sets.difference(newMembers, oldMembers)
            .stream()
            .map(accountNameEmailRetriever)
            .map("Add: "::concat);
    return Stream.concat(removedMembers, addedMembers).collect(Collectors.joining("\n"));
  }

  private String getCommitFooterForSubgroupModifications(
      ImmutableSet<AccountGroup.UUID> oldSubgroups, ImmutableSet<AccountGroup.UUID> newSubgroups) {
    Stream<String> removedMembers =
        Sets.difference(oldSubgroups, newSubgroups)
            .stream()
            .map(groupNameRetriever)
            .map("Remove: group "::concat);
    Stream<String> addedMembers =
        Sets.difference(newSubgroups, oldSubgroups)
            .stream()
            .map(groupNameRetriever)
            .map("Add: group "::concat);
    return Stream.concat(removedMembers, addedMembers).collect(Collectors.joining("\n"));
  }
}
