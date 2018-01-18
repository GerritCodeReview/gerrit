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

package com.google.gerrit.acceptance.api.project;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.config.AccessCheckInfo;
import com.google.gerrit.extensions.api.config.AccessCheckInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.group.SystemGroupBackend;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class CheckAccessIT extends AbstractDaemonTest {

  private Project.NameKey normalProject;
  private Project.NameKey secretProject;
  private Project.NameKey secretRefProject;
  private TestAccount privilegedUser;
  private InternalGroup privilegedGroup;

  @Before
  public void setUp() throws Exception {
    normalProject = createProject("normal");
    secretProject = createProject("secret");
    secretRefProject = createProject("secretRef");
    privilegedGroup = group(createGroup("privilegedGroup"));

    privilegedUser = accountCreator.create("privilegedUser", "snowden@nsa.gov", "Ed Snowden");
    gApi.groups().id(privilegedGroup.getGroupUUID().get()).addMembers(privilegedUser.username);

    assertThat(gApi.groups().id(privilegedGroup.getGroupUUID().get()).members().get(0).email)
        .contains("snowden");

    grant(secretProject, "refs/*", Permission.READ, false, privilegedGroup.getGroupUUID());
    block(secretProject, "refs/*", Permission.READ, SystemGroupBackend.REGISTERED_USERS);

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

    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();

    // Ref permission
    grant(
        normalProject,
        "refs/*",
        Permission.VIEW_PRIVATE_CHANGES,
        false,
        privilegedGroup.getGroupUUID());
    grant(normalProject, "refs/*", Permission.FORGE_SERVER, false, privilegedGroup.getGroupUUID());
  }

  @Test
  public void emptyInput() throws Exception {
    exception.expect(BadRequestException.class);
    exception.expectMessage("input requires 'account'");
    gApi.projects().name(normalProject.get()).checkAccess(new AccessCheckInput());
  }

  @Test
  public void nonexistentPermission() throws Exception {
    exception.expect(BadRequestException.class);
    exception.expectMessage("not recognized");
    AccessCheckInput in = new AccessCheckInput();

    in.account = user.email;
    in.permission = "notapermission";
    in.ref = "refs/heads/master";
    gApi.projects().name(normalProject.get()).checkAccess(in);
  }

  @Test
  public void permissionLacksRef() throws Exception {
    exception.expect(BadRequestException.class);
    exception.expectMessage("must set 'ref'");
    AccessCheckInput in = new AccessCheckInput();

    in.account = user.email;
    in.permission = "forge_author";
    gApi.projects().name(normalProject.get()).checkAccess(in);
  }

  @Test
  public void changePermission() throws Exception {
    exception.expect(BadRequestException.class);
    exception.expectMessage("recognized as ref permission");
    AccessCheckInput in = new AccessCheckInput();

    in.account = user.email;
    in.permission = "rebase";
    in.ref = "refs/heads/master";
    gApi.projects().name(normalProject.get()).checkAccess(in);
  }

  @Test
  public void nonexistentEmail() throws Exception {
    exception.expect(UnprocessableEntityException.class);
    exception.expectMessage("cannot find account doesnotexist@invalid.com");

    AccessCheckInput in = new AccessCheckInput();
    in.account = "doesnotexist@invalid.com";
    in.permission = "rebase";
    in.ref = "refs/heads/master";
    gApi.projects().name(normalProject.get()).checkAccess(in);
  }

  private static class TestCase {
    AccessCheckInput input;
    String project;
    String permission;
    int want;

    static TestCase project(String mail, String project, int want) {
      TestCase t = new TestCase();
      t.input = new AccessCheckInput();
      t.input.account = mail;
      t.project = project;
      t.want = want;
      return t;
    }

    static TestCase projectRef(String mail, String project, String ref, int want) {
      TestCase t = new TestCase();
      t.input = new AccessCheckInput();
      t.input.account = mail;
      t.input.ref = ref;
      t.project = project;
      t.want = want;
      return t;
    }

    static TestCase projectRefPerm(
        String mail, String project, String ref, String permission, int want) {
      TestCase t = new TestCase();
      t.input = new AccessCheckInput();
      t.input.account = mail;
      t.input.ref = ref;
      t.input.permission = permission;
      t.project = project;
      t.want = want;
      return t;
    }
  }

  @Test
  public void accessible() throws Exception {
    List<TestCase> inputs =
        ImmutableList.of(
            TestCase.projectRefPerm(
                user.email,
                normalProject.get(),
                "refs/heads/master",
                Permission.VIEW_PRIVATE_CHANGES,
                403),
            TestCase.project(user.email, normalProject.get(), 200),
            TestCase.project(user.email, secretProject.get(), 403),
            TestCase.projectRef(
                user.email, secretRefProject.get(), "refs/heads/secret/master", 403),
            TestCase.projectRef(
                privilegedUser.email, secretRefProject.get(), "refs/heads/secret/master", 200),
            TestCase.projectRef(privilegedUser.email, normalProject.get(), null, 200),
            TestCase.projectRef(privilegedUser.email, secretProject.get(), null, 200),
            TestCase.projectRef(privilegedUser.email, secretProject.get(), null, 200),
            TestCase.projectRefPerm(
                privilegedUser.email,
                normalProject.get(),
                "refs/heads/master",
                Permission.VIEW_PRIVATE_CHANGES,
                200),
            TestCase.projectRefPerm(
                privilegedUser.email,
                normalProject.get(),
                "refs/heads/master",
                Permission.FORGE_SERVER,
                200));

    for (TestCase tc : inputs) {
      String in = newGson().toJson(tc.input);
      AccessCheckInfo info = null;

      try {
        info = gApi.projects().name(tc.project).checkAccess(tc.input);
      } catch (RestApiException e) {
        fail(String.format("check.access(%s, %s): exception %s", tc.project, in, e));
      }

      int want = tc.want;
      if (want != info.status) {
        fail(
            String.format("check.access(%s, %s) = %d, want %d", tc.project, in, info.status, want));
      }

      switch (want) {
        case 403:
          if (tc.permission != null) {
            assertThat(info.message).contains("lacks permission " + tc.permission);
          }
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
