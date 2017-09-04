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

package com.google.gerrit.acceptance.rest.project;

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
            new AccessCheckInput(user.email, null),
            new AccessCheckInput(null, null),
            new AccessCheckInput("doesnotexist@invalid.com", null));
    for (AccessCheckInput input : inputs) {
      try {
        gApi.config().server().checkAccess(input);
        fail(String.format("want RestApiException for %s", newGson().toJson(input)));
      } catch (RestApiException e) {

      }
    }
  }

  static class TestCase {
    AccessCheckInput input;
    String project;
    int want;
  }

  static TestCase newTestInput(String mail, String project, String ref, int want) {
    TestCase tc = new TestCase();

    tc.input = new AccessCheckInput(mail, ref);
    tc.project = project;
    tc.want = want;
    return tc;
  }

  @Test
  public void accessible() {
    List<TestCase> inputs =
        ImmutableList.of(
            newTestInput(user.email, normalProject.get(), null, 200),
            newTestInput(user.email, secretProject.get(), null, 403),
            newTestInput(user.email, "nonexistent", null, 404),
            newTestInput(privilegedUser.email, normalProject.get(), null, 200),
            newTestInput(privilegedUser.email, secretProject.get(), null, 200));

    for (TestCase tc : inputs) {
      String in = newGson().toJson(tc.input);
      AccessCheckInfo info = null;

      try {
        info = gApi.projects().name(tc.project).checkAccess(tc.input);
      } catch (RestApiException e) {
        fail(String.format("check.check(%s): exception %s", in, e));
      }

      int want = tc.want;
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
