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

package com.google.gerrit.acceptance.rest.account;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.config.AccessCheckInfo;
import com.google.gerrit.extensions.api.config.AccessCheckInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.group.SystemGroupBackend;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class CheckAccessIT extends AbstractDaemonTest {

  private Project.NameKey normalProject;
  private Project.NameKey secretProject;
  private Project.NameKey secretRefProject;
  private TestAccount privilegedUser;
  private AccountGroup privilegedGroup;

  @Before
  public void setUp() throws Exception {
    normalProject = createProject("normal");
    secretProject = createProject("secret");
    secretRefProject = createProject("secretRef");
    privilegedGroup = groupCache.get(new AccountGroup.NameKey(createGroup("privilegedGroup")));

    privilegedUser = accountCreator.create("privilegedUser", "snowden@nsa.gov", "Ed Snowden");
    gApi.groups().id(privilegedGroup.getGroupUUID().get()).addMembers(privilegedUser.username);

    assertThat(gApi.groups().id(privilegedGroup.getGroupUUID().get()).members().get(0).email)
        .contains("snowden");

    grant(secretProject, "refs/*", Permission.READ, false, privilegedGroup.getGroupUUID());
    block(secretProject, "refs/*", Permission.READ, SystemGroupBackend.REGISTERED_USERS);

    // deny/grant/block arg ordering is screwy.
    deny(secretRefProject, "refs/*", Permission.READ, SystemGroupBackend.ANONYMOUS_USERS);
    grant(
        secretRefProject,
        "refs/heads/secret/*",
        Permission.READ,
        false,
        privilegedGroup.getGroupUUID());
    block(
        secretRefProject,
        "refs/heads/secret/*",
        Permission.READ,
        SystemGroupBackend.REGISTERED_USERS);
    grant(
        secretRefProject,
        "refs/heads/*",
        Permission.READ,
        false,
        SystemGroupBackend.REGISTERED_USERS);
  }

  @Test
  public void invalidInputs() {
    List<AccessCheckInput> inputs =
        ImmutableList.of(
            new AccessCheckInput(),
            new AccessCheckInput(user.email, null, null),
            new AccessCheckInput(null, normalProject.toString(), null),
            new AccessCheckInput("doesnotexist@invalid.com", normalProject.toString(), null));
    for (AccessCheckInput input : inputs) {
      try {
        gApi.config().server().checkAccess(input);
        fail(String.format("want RestApiException for %s", newGson().toJson(input)));
      } catch (RestApiException e) {

      }
    }
  }

  @Test
  public void accessible() {
    Map<AccessCheckInput, Integer> inputs =
        ImmutableMap.of(
            new AccessCheckInput(user.email, normalProject.get(), null), 200,
            new AccessCheckInput(user.email, secretProject.get(), null), 403,
            new AccessCheckInput(user.email, "nonexistent", null), 404,
            new AccessCheckInput(privilegedUser.email, normalProject.get(), null), 200,
            new AccessCheckInput(privilegedUser.email, secretProject.get(), null), 200);

    for (Map.Entry<AccessCheckInput, Integer> entry : inputs.entrySet()) {
      String in = newGson().toJson(entry.getKey());
      AccessCheckInfo info = null;

      try {
        info = gApi.config().server().checkAccess(entry.getKey());
      } catch (RestApiException e) {
        fail(String.format("check.check(%s): exception %s", in, e));
      }

      int want = entry.getValue();
      if (want != info.status) {
        fail(String.format("check.access(%s) = %d, want %d", in, info.status, want));
      }

      switch (want) {
        case 403:
          assertThat(info.message).contains("cannot see");
          break;
        case 404:
          assertThat(info.message).contains("does not exist");
          break;
        case 200:
          assertThat(info.message).isNull();
          break;
        default:
          fail(String.format("unknown code %d", want));
      }
    }
  }
}
