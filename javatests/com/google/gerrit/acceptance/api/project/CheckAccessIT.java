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
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.deny;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.permissionKey;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static com.google.gerrit.testing.TestActionRefUpdateContext.testRefAction;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.AccessSection;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.config.AccessCheckInfo;
import com.google.gerrit.extensions.api.config.AccessCheckInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.inject.Inject;
import java.util.List;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

public class CheckAccessIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private GroupOperations groupOperations;
  @Inject private AllProjectsName allProjectsName;

  private Project.NameKey normalProject;
  private Project.NameKey secretProject;
  private Project.NameKey secretRefProject;
  private AccountGroup.UUID privilegedGroupUuid;
  private TestAccount privilegedUser;

  @Before
  public void setUp() throws Exception {
    normalProject = projectOperations.newProject().create();
    secretProject = projectOperations.newProject().create();
    secretRefProject = projectOperations.newProject().create();
    privilegedGroupUuid = groupOperations.newGroup().name(name("privilegedGroup")).create();

    privilegedUser = accountCreator.create("privilegedUser", "snowden@nsa.gov", "Ed Snowden", null);
    groupOperations.group(privilegedGroupUuid).forUpdate().addMember(privilegedUser.id()).update();

    projectOperations
        .project(secretProject)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/*").group(privilegedGroupUuid))
        .add(block(Permission.READ).ref("refs/*").group(SystemGroupBackend.REGISTERED_USERS))
        .update();

    projectOperations
        .project(secretRefProject)
        .forUpdate()
        .add(deny(Permission.READ).ref("refs/*").group(SystemGroupBackend.ANONYMOUS_USERS))
        .add(allow(Permission.READ).ref("refs/heads/secret/*").group(privilegedGroupUuid))
        .add(
            block(Permission.READ)
                .ref("refs/heads/secret/*")
                .group(SystemGroupBackend.REGISTERED_USERS))
        .add(allow(Permission.READ).ref("refs/heads/*").group(SystemGroupBackend.REGISTERED_USERS))
        .update();

    // Ref permission
    projectOperations
        .project(normalProject)
        .forUpdate()
        .add(allow(Permission.VIEW_PRIVATE_CHANGES).ref("refs/*").group(privilegedGroupUuid))
        .add(allow(Permission.FORGE_SERVER).ref("refs/*").group(privilegedGroupUuid))
        .update();
  }

  @Test
  public void emptyInput() throws Exception {
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(normalProject.get()).checkAccess(new AccessCheckInput()));
    assertThat(thrown).hasMessageThat().contains("input requires 'account'");
  }

  @Test
  public void nonexistentPermission() throws Exception {
    AccessCheckInput in = new AccessCheckInput();
    in.account = user.email();
    in.permission = "notapermission";
    in.ref = "refs/heads/master";

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(normalProject.get()).checkAccess(in));
    assertThat(thrown).hasMessageThat().contains("not recognized");
  }

  @Test
  public void permissionLacksRef() throws Exception {
    AccessCheckInput in = new AccessCheckInput();
    in.account = user.email();
    in.permission = "forge_author";

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(normalProject.get()).checkAccess(in));
    assertThat(thrown).hasMessageThat().contains("must set 'ref'");
  }

  @Test
  public void changePermission() throws Exception {
    AccessCheckInput in = new AccessCheckInput();
    in.account = user.email();
    in.permission = "rebase";
    in.ref = "refs/heads/master";

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(normalProject.get()).checkAccess(in));
    assertThat(thrown).hasMessageThat().contains("recognized as ref permission");
  }

  @Test
  public void nonexistentEmail() throws Exception {
    AccessCheckInput in = new AccessCheckInput();
    in.account = "doesnotexist@invalid.com";
    in.permission = "rebase";
    in.ref = "refs/heads/master";

    UnprocessableEntityException thrown =
        assertThrows(
            UnprocessableEntityException.class,
            () -> gApi.projects().name(normalProject.get()).checkAccess(in));
    assertThat(thrown).hasMessageThat().contains("Account 'doesnotexist@invalid.com' not found");
  }

  private static class TestCase {
    AccessCheckInput input;
    String project;
    String permission;
    int want;
    List<String> expectedDebugLogs;

    static TestCase project(String mail, String project, int want, List<String> expectedDebugLogs) {
      TestCase t = new TestCase();
      t.input = new AccessCheckInput();
      t.input.account = mail;
      t.project = project;
      t.want = want;
      t.expectedDebugLogs = expectedDebugLogs;
      return t;
    }

    static TestCase projectRef(
        String mail, String project, String ref, int want, List<String> expectedDebugLogs) {
      TestCase t = new TestCase();
      t.input = new AccessCheckInput();
      t.input.account = mail;
      t.input.ref = ref;
      t.project = project;
      t.want = want;
      t.expectedDebugLogs = expectedDebugLogs;
      return t;
    }

    static TestCase projectRefPerm(
        String mail,
        String project,
        String ref,
        String permission,
        int want,
        List<String> expectedDebugLogs) {
      TestCase t = new TestCase();
      t.input = new AccessCheckInput();
      t.input.account = mail;
      t.input.ref = ref;
      t.input.permission = permission;
      t.project = project;
      t.want = want;
      t.expectedDebugLogs = expectedDebugLogs;
      return t;
    }
  }

  @Test
  public void httpGet() throws Exception {
    RestResponse rep =
        adminRestSession.get(
            "/projects/"
                + normalProject.get()
                + "/check.access"
                + "?ref=refs/heads/master&perm=viewPrivateChanges&account="
                + user.email());
    rep.assertOK();
    assertThat(rep.getEntityContent()).contains("403");
  }

  @Test
  public void accessible() throws Exception {
    ImmutableList<TestCase> inputs =
        ImmutableList.of(
            // Test 1
            TestCase.projectRefPerm(
                user.email(),
                normalProject.get(),
                "refs/heads/master",
                Permission.VIEW_PRIVATE_CHANGES,
                403,
                ImmutableList.of(
                    "'user1' can perform 'read' with force=false on project '"
                        + normalProject.get()
                        + "' for ref 'refs/heads/*' (allowed for group '"
                        + SystemGroupBackend.ANONYMOUS_USERS.get()
                        + "' by rule 'group Anonymous Users')",
                    "'user1' cannot perform 'viewPrivateChanges' with force=false on project '"
                        + normalProject.get()
                        + "' for ref 'refs/heads/master'")),
            // Test 2
            TestCase.project(
                user.email(),
                normalProject.get(),
                200,
                ImmutableList.of(
                    "'user1' can perform 'read' with force=false on project '"
                        + normalProject.get()
                        + "' for ref 'refs/heads/*' (allowed for group '"
                        + SystemGroupBackend.ANONYMOUS_USERS.get()
                        + "' by rule 'group Anonymous Users')")),
            // Test 3
            TestCase.project(
                user.email(),
                secretProject.get(),
                403,
                ImmutableList.of(
                    "'user1' cannot perform 'read' with force=false on project '"
                        + secretProject.get()
                        + "' for ref 'refs/heads/*' because this permission is blocked",
                    "'user1' cannot perform 'read' with force=false on project '"
                        + secretProject.get()
                        + "' for ref 'refs/meta/version' because this permission is blocked")),
            // Test 4
            TestCase.projectRef(
                user.email(),
                secretRefProject.get(),
                "refs/heads/secret/master",
                403,
                ImmutableList.of(
                    "'user1' can perform 'read' with force=false on project '"
                        + secretRefProject.get()
                        + "' for ref 'refs/heads/*' (allowed for group '"
                        + SystemGroupBackend.REGISTERED_USERS.get()
                        // if the permission was assigned through ProjectOperations the local group
                        // name is set to the UUID
                        + "' by rule 'group "
                        + SystemGroupBackend.REGISTERED_USERS.get()
                        + "')",
                    "'user1' cannot perform 'read' with force=false on project '"
                        + secretRefProject.get()
                        + "' for ref 'refs/heads/secret/master' because this permission is blocked")),
            // Test 5
            TestCase.projectRef(
                privilegedUser.email(),
                secretRefProject.get(),
                "refs/heads/secret/master",
                200,
                ImmutableList.of(
                    "'privilegedUser' can perform 'read' with force=false on project '"
                        + secretRefProject.get()
                        + "' for ref 'refs/heads/*' (allowed for group '"
                        + SystemGroupBackend.REGISTERED_USERS.get()
                        // if the permission was assigned through ProjectOperations the local group
                        // name is set to the UUID
                        + "' by rule 'group "
                        + SystemGroupBackend.REGISTERED_USERS.get()
                        + "')",
                    "'privilegedUser' can perform 'read' with force=false on project '"
                        + secretRefProject.get()
                        + "' for ref 'refs/heads/secret/master' (allowed for group '"
                        + privilegedGroupUuid.get()
                        // if the permission was assigned through ProjectOperations the local group
                        // name is set to the UUID
                        + "' by rule 'group "
                        + privilegedGroupUuid.get()
                        + "')")),
            // Test 6
            TestCase.projectRef(
                privilegedUser.email(),
                normalProject.get(),
                null,
                200,
                ImmutableList.of(
                    "'privilegedUser' can perform 'read' with force=false on project '"
                        + normalProject.get()
                        + "' for ref 'refs/heads/*' (allowed for group '"
                        + SystemGroupBackend.ANONYMOUS_USERS.get()
                        + "' by rule 'group Anonymous Users')")),
            // Test 7
            TestCase.projectRef(
                privilegedUser.email(),
                secretProject.get(),
                null,
                200,
                ImmutableList.of(
                    "'privilegedUser' can perform 'read' with force=false on project '"
                        + secretProject.get()
                        + "' for ref 'refs/*' (allowed for group '"
                        + privilegedGroupUuid.get()
                        // if the permission was assigned through ProjectOperations the local group
                        // name is set to the UUID
                        + "' by rule 'group "
                        + privilegedGroupUuid.get()
                        + "')")),
            // Test 8
            TestCase.projectRefPerm(
                privilegedUser.email(),
                normalProject.get(),
                "refs/heads/master",
                Permission.VIEW_PRIVATE_CHANGES,
                200,
                ImmutableList.of(
                    "'privilegedUser' can perform 'read' with force=false on project '"
                        + normalProject.get()
                        + "' for ref 'refs/heads/*' (allowed for group '"
                        + SystemGroupBackend.ANONYMOUS_USERS.get()
                        + "' by rule 'group Anonymous Users')",
                    "'privilegedUser' can perform 'viewPrivateChanges' with force=false on project '"
                        + normalProject.get()
                        + "' for ref 'refs/heads/master' (allowed for group '"
                        + privilegedGroupUuid.get()
                        // if the permission was assigned through ProjectOperations the local group
                        // name is set to the UUID
                        + "' by rule 'group "
                        + privilegedGroupUuid.get()
                        + "')")),
            // Test 9
            TestCase.projectRefPerm(
                privilegedUser.email(),
                normalProject.get(),
                "refs/heads/master",
                Permission.FORGE_SERVER,
                200,
                ImmutableList.of(
                    "'privilegedUser' can perform 'read' with force=false on project '"
                        + normalProject.get()
                        + "' for ref 'refs/heads/*' (allowed for group '"
                        + SystemGroupBackend.ANONYMOUS_USERS.get()
                        + "' by rule 'group Anonymous Users')",
                    "'privilegedUser' can perform 'forgeServerAsCommitter' with force=false on project '"
                        + normalProject.get()
                        + "' for ref 'refs/heads/master' (allowed for group '"
                        + privilegedGroupUuid.get()
                        // if the permission was assigned through ProjectOperations the local group
                        // name is set to the UUID
                        + "' by rule 'group "
                        + privilegedGroupUuid.get()
                        + "')")));

    for (TestCase tc : inputs) {
      String in = newGson().toJson(tc.input);
      AccessCheckInfo info = null;

      try {
        info = gApi.projects().name(tc.project).checkAccess(tc.input);
      } catch (RestApiException e) {
        assertWithMessage(String.format("check.access(%s, %s): exception %s", tc.project, in, e))
            .fail();
      }

      int want = tc.want;
      if (want != info.status) {
        assertWithMessage(
                String.format(
                    "check.access(%s, %s) = %d, want %d", tc.project, in, info.status, want))
            .fail();
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
          assertWithMessage(String.format("unknown code %d", want)).fail();
      }

      if (!info.debugLogs.equals(tc.expectedDebugLogs)) {
        assertWithMessage(
                String.format(
                    "check.access(%s, %s) = %s, want %s",
                    tc.project, in, info.debugLogs, tc.expectedDebugLogs))
            .fail();
      }
    }
  }

  @Test
  public void noBranches() throws Exception {
    try (Repository repo = repoManager.openRepository(normalProject)) {
      RefUpdate u = repo.updateRef(RefNames.REFS_HEADS + "master");
      u.setForceUpdate(true);
      testRefAction(() -> assertThat(u.delete()).isEqualTo(Result.FORCED));
    }
    AccessCheckInput input = new AccessCheckInput();
    input.account = privilegedUser.email();

    AccessCheckInfo info = gApi.projects().name(normalProject.get()).checkAccess(input);
    assertThat(info.status).isEqualTo(200);
    assertThat(info.message).contains("no branches");
  }

  @Test
  @Sandboxed
  public void noRules() throws Exception {
    normalProject = projectOperations.newProject().create();

    for (AccessSection section :
        projectOperations.project(allProjectsName).getProjectConfig().getAccessSections()) {
      if (!section.getName().startsWith(Constants.R_REFS)) {
        continue;
      }
      for (Permission permission : section.getPermissions()) {
        projectOperations
            .project(allProjectsName)
            .forUpdate()
            .remove(permissionKey(permission.getName()).ref(section.getName()).build())
            .update();
      }
    }
    AccessCheckInput input = new AccessCheckInput();
    input.account = privilegedUser.email();
    input.permission = Permission.READ;
    input.ref = "refs/heads/main";

    AccessCheckInfo info = gApi.projects().name(normalProject.get()).checkAccess(input);
    assertThat(info.status).isEqualTo(403);

    assertThat(info.debugLogs).isNotEmpty();
    assertThat(info.debugLogs.get(0)).contains("Found no rules");
  }
}
