// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.config;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.InternalGroup;
import com.google.gerrit.server.StartupCheck;
import com.google.gerrit.server.StartupException;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.group.db.Groups;
import com.google.gerrit.server.util.time.TimeUtil;
import java.io.IOException;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class RegexAllowedGroupsCheckTest {
  private static final String ALLOWED_NAME = "Allowed Group";
  private static final AccountGroup.UUID ALLOWED_UUID = AccountGroup.uuid("allowed-group");

  private final Groups groups = mock(Groups.class);
  private final SystemGroupBackend systemGroupBackend = new SystemGroupBackend(new Config());

  @Test
  public void noConfiguredGroupPasses() {
    createCheck(new Config()).check();
  }

  @Test
  public void allConfiguredGroupsResolvePasses() throws Exception {
    Config config = configWithGroups(ALLOWED_NAME);
    groupResolves(ALLOWED_NAME, ALLOWED_UUID);

    createCheck(config).check();
  }

  @Test
  public void systemGroupResolves() {
    createCheck(configWithGroups("Registered Users")).check();
  }

  @Test
  public void changeOwnerDoesNotResolve() {
    assertThrows(
        StartupException.class, () -> createCheck(configWithGroups("Change Owner")).check());
  }

  @Test
  public void configuredGroupDoesNotResolveFailsStartup() {
    StartupException thrown =
        assertThrows(
            StartupException.class, () -> createCheck(configWithGroups("Missing Group")).check());

    assertThat(thrown)
        .hasMessageThat()
        .contains("Groups configured in regex.allowedGroup could not be resolved: [Missing Group]");
  }

  @Test
  public void oneConfiguredGroupDoesNotResolveFailsStartup() throws Exception {
    Config config = configWithGroups(ALLOWED_NAME, "Missing Group");
    groupResolves(ALLOWED_NAME, ALLOWED_UUID);

    StartupException thrown =
        assertThrows(StartupException.class, () -> createCheck(config).check());

    assertThat(thrown).hasMessageThat().contains("[Missing Group]");
    assertThat(thrown).hasMessageThat().doesNotContain(ALLOWED_NAME);
  }

  @Test
  public void internalGroupsCannotBeLoadedFailsStartup() throws Exception {
    IOException failure = new IOException("cannot read All-Users");
    when(groups.getAllGroupReferences()).thenThrow(failure);

    StartupException thrown =
        assertThrows(
            StartupException.class, () -> createCheck(configWithGroups(ALLOWED_NAME)).check());

    assertThat(thrown).hasMessageThat().contains("Could not load internal groups");
    assertThat(thrown).hasCauseThat().isSameInstanceAs(failure);
  }

  private StartupCheck createCheck(Config config) {
    return new RegexAllowedGroupsProvider(groups, systemGroupBackend, config);
  }

  private static Config configWithGroups(String... names) {
    Config config = new Config();
    config.setStringList(
        RegexAllowedGroupsProvider.SECTION,
        null,
        RegexAllowedGroupsProvider.KEY,
        ImmutableList.copyOf(names));
    return config;
  }

  private void groupResolves(String name, AccountGroup.UUID uuid) throws Exception {
    InternalGroup group =
        InternalGroup.builder()
            .setId(AccountGroup.id(123456))
            .setNameKey(AccountGroup.nameKey(name))
            .setOwnerGroupUUID(uuid)
            .setVisibleToAll(false)
            .setGroupUUID(uuid)
            .setCreatedOn(TimeUtil.now())
            .setMembers(ImmutableSet.of())
            .setSubgroups(ImmutableSet.of())
            .build();
    when(groups.getGroup(uuid)).thenReturn(Optional.of(group));
    when(groups.getAllGroupReferences()).thenReturn(Stream.of(GroupReference.create(uuid, name)));
  }
}
