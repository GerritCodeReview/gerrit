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

package com.google.gerrit.acceptance.config;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.server.StartupException;
import com.google.gerrit.server.config.RegexAllowedGroups;
import com.google.gerrit.server.config.RegexAllowedGroupsProvider;
import com.google.inject.Inject;
import java.util.Set;
import org.junit.Test;

public class RegexAllowedGroupsIT extends AbstractDaemonTest {
  public static final String REGISTERED_USERS = "Registered Users";

  @Inject @RegexAllowedGroups private Set<AccountGroup.UUID> allowedGroups;

  @Test
  @Sandboxed
  public void privateGroupResolvesAtStartup() throws Exception {
    String groupName = name("private-group");
    gApi.groups().create(groupName);
    cfg.setString(
        RegexAllowedGroupsProvider.SECTION, null, RegexAllowedGroupsProvider.KEY, groupName);

    restart();

    assertThat(gApi.groups().id(groupName).get().name).isEqualTo(groupName);
  }

  @Test
  @GerritConfig(
      name = RegexAllowedGroupsProvider.SECTION + "." + RegexAllowedGroupsProvider.KEY,
      value = REGISTERED_USERS)
  public void systemGroupResolvesAtStartup() throws Exception {
    assertThat(allowedGroups).hasSize(1);
    AccountGroup.UUID allowedGroupUUID = allowedGroups.iterator().next();
    assertThat(systemGroupBackend.getGroup(allowedGroupUUID).getName()).isEqualTo(REGISTERED_USERS);
  }

  @Test
  @Sandboxed
  public void invalidGroupPreventsRestart() throws Exception {
    cfg.setString(
        RegexAllowedGroupsProvider.SECTION, null, RegexAllowedGroupsProvider.KEY, "missing-group");

    StartupException thrown = assertThrows(StartupException.class, this::restart);
    assertThat(thrown).hasMessageThat().contains("[missing-group]");
  }
}
