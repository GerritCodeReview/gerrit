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

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.git.VersionedMetaData;
import com.google.gerrit.server.group.InternalGroup;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;

// TODO(aliceks): Add Javadoc descriptions to this file.
public class GroupConfig extends VersionedMetaData {
  private static final String GROUP_CONFIG_FILE = "group.config";

  private final AccountGroup.UUID groupUuid;
  private final String ref;

  private Optional<InternalGroup> loadedGroup = Optional.empty();
  private Optional<InternalGroupUpdate> groupUpdate = Optional.empty();
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

  public void setGroupUpdate(InternalGroupUpdate groupUpdate) {
    this.groupUpdate = Optional.of(groupUpdate);
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
      ImmutableSet<Account.Id> members = ImmutableSet.of();
      ImmutableSet<AccountGroup.UUID> subgroups = ImmutableSet.of();
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

    ImmutableSet<Account.Id> originalMembers = loadedGroup.get().getMembers();
    ImmutableSet<AccountGroup.UUID> originalSubgroups = loadedGroup.get().getSubgroups();

    commit.setMessage("Update group");

    loadedGroup =
        Optional.of(createFrom(groupUuid, config, originalMembers, originalSubgroups, createdOn));

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
}
