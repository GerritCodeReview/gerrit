// Copyright (C) 2021 The Android Open Source Project
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
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.schema.AclUtil.grant;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.AccessSection;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.access.AccessSectionInfo;
import com.google.gerrit.extensions.api.access.PermissionInfo;
import com.google.gerrit.extensions.api.access.PermissionRuleInfo;
import com.google.gerrit.extensions.api.access.ProjectAccessInfo;
import com.google.gerrit.extensions.api.projects.ProjectApi;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.schema.GrantRevertPermission;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import java.util.Map;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

public class AccessIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private GrantRevertPermission grantRevertPermission;

  private Project.NameKey newProjectName;

  @Before
  public void setUp() throws Exception {
    newProjectName = projectOperations.newProject().create();
  }

  @Test
  public void grantRevertPermission() throws Exception {
    String ref = "refs/*";
    String groupId = "global:Registered-Users";

    grantRevertPermission.execute(newProjectName);

    ProjectAccessInfo info = pApi().access();
    assertThat(info.local.containsKey(ref)).isTrue();
    AccessSectionInfo accessSectionInfo = info.local.get(ref);
    assertThat(accessSectionInfo.permissions.containsKey(Permission.REVERT)).isTrue();
    PermissionInfo permissionInfo = accessSectionInfo.permissions.get(Permission.REVERT);
    assertThat(permissionInfo.rules.containsKey(groupId)).isTrue();
    PermissionRuleInfo permissionRuleInfo = permissionInfo.rules.get(groupId);
    assertThat(permissionRuleInfo.action).isEqualTo(PermissionRuleInfo.Action.ALLOW);
  }

  @Test
  public void grantRevertPermissionByOnNewRefAndDeletingOnOldRef() throws Exception {
    String refsHeads = "refs/heads/*";
    String refsStar = "refs/*";
    String groupId = "global:Registered-Users";
    GroupReference registeredUsers = systemGroupBackend.getGroup(REGISTERED_USERS);

    try (Repository repo = repoManager.openRepository(newProjectName)) {
      MetaDataUpdate md = new MetaDataUpdate(GitReferenceUpdated.DISABLED, newProjectName, repo);
      ProjectConfig projectConfig = projectConfigFactory.read(md);
      projectConfig.upsertAccessSection(
          AccessSection.HEADS,
          heads -> {
            grant(projectConfig, heads, Permission.REVERT, registeredUsers);
          });
      md.getCommitBuilder().setAuthor(admin.newIdent());
      md.getCommitBuilder().setCommitter(admin.newIdent());
      md.setMessage("Add revert permission for all registered users\n");

      projectConfig.commit(md);
    }
    grantRevertPermission.execute(newProjectName);

    ProjectAccessInfo info = pApi().access();

    // Revert permission is removed on refs/heads/*.
    assertThat(info.local.containsKey(refsHeads)).isTrue();
    AccessSectionInfo accessSectionInfo = info.local.get(refsHeads);
    assertThat(accessSectionInfo.permissions.containsKey(Permission.REVERT)).isFalse();

    // new permission is added on refs/* with Registered-Users.
    assertThat(info.local.containsKey(refsStar)).isTrue();
    accessSectionInfo = info.local.get(refsStar);
    assertThat(accessSectionInfo.permissions.containsKey(Permission.REVERT)).isTrue();
    PermissionInfo permissionInfo = accessSectionInfo.permissions.get(Permission.REVERT);
    assertThat(permissionInfo.rules.containsKey(groupId)).isTrue();
    PermissionRuleInfo permissionRuleInfo = permissionInfo.rules.get(groupId);
    assertThat(permissionRuleInfo.action).isEqualTo(PermissionRuleInfo.Action.ALLOW);
  }

  @Test
  public void grantRevertPermissionDoesntDeleteAdminsPreferences() throws Exception {
    GroupReference registeredUsers = systemGroupBackend.getGroup(REGISTERED_USERS);
    GroupReference otherGroup = systemGroupBackend.getGroup(ANONYMOUS_USERS);

    try (Repository repo = repoManager.openRepository(newProjectName)) {
      MetaDataUpdate md = new MetaDataUpdate(GitReferenceUpdated.DISABLED, newProjectName, repo);
      ProjectConfig projectConfig = projectConfigFactory.read(md);
      projectConfig.upsertAccessSection(
          AccessSection.HEADS,
          heads -> {
            grant(projectConfig, heads, Permission.REVERT, registeredUsers);
            grant(projectConfig, heads, Permission.REVERT, otherGroup);
          });
      md.getCommitBuilder().setAuthor(admin.newIdent());
      md.getCommitBuilder().setCommitter(admin.newIdent());
      md.setMessage("Add revert permission for all registered users\n");

      projectConfig.commit(md);
    }
    projectCache.evict(newProjectName);
    ProjectAccessInfo expected = pApi().access();

    grantRevertPermission.execute(newProjectName);
    projectCache.evictAndReindex(newProjectName);
    ProjectAccessInfo actual = pApi().access();
    // Permissions don't change
    assertThat(expected.local).isEqualTo(actual.local);
  }

  @Test
  public void grantRevertPermissionOnlyWorksOnce() throws Exception {
    grantRevertPermission.execute(newProjectName);
    grantRevertPermission.execute(newProjectName);

    try (Repository repo = repoManager.openRepository(newProjectName)) {
      MetaDataUpdate md = new MetaDataUpdate(GitReferenceUpdated.DISABLED, newProjectName, repo);
      ProjectConfig projectConfig = projectConfigFactory.read(md);
      AccessSection all = projectConfig.getAccessSection(AccessSection.ALL);

      Permission permission = all.getPermission(Permission.REVERT);
      assertThat(permission.getRules()).hasSize(1);
    }
  }

  private ProjectApi pApi() throws Exception {
    return gApi.projects().name(newProjectName.get());
  }

  @Test
  public void listAccessWithoutSpecifyingProject() throws Exception {
    RestResponse r = adminRestSession.get("/access/");
    r.assertOK();
    Map<String, ProjectAccessInfo> infoByProject =
        newGson()
            .fromJson(r.getReader(), new TypeToken<Map<String, ProjectAccessInfo>>() {}.getType());
    assertThat(infoByProject).isEmpty();
  }

  @Test
  public void listAccessWithoutSpecifyingAnEmptyProjectName() throws Exception {
    RestResponse r = adminRestSession.get("/access/?p=");
    r.assertOK();
    Map<String, ProjectAccessInfo> infoByProject =
        newGson()
            .fromJson(r.getReader(), new TypeToken<Map<String, ProjectAccessInfo>>() {}.getType());
    assertThat(infoByProject).isEmpty();
  }

  @Test
  public void listAccessForNonExistingProject() throws Exception {
    RestResponse r = adminRestSession.get("/access/?project=non-existing");
    r.assertNotFound();
    assertThat(r.getEntityContent()).isEqualTo("non-existing");
  }

  @Test
  public void listAccessForNonVisibleProject() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref("refs/*").group(REGISTERED_USERS))
        .update();

    RestResponse r = userRestSession.get("/access/?project=" + project.get());
    r.assertNotFound();
    assertThat(r.getEntityContent()).isEqualTo(project.get());
  }

  @Test
  public void listAccess() throws Exception {
    RestResponse r = adminRestSession.get("/access/?project=" + project.get());
    r.assertOK();
    Map<String, ProjectAccessInfo> infoByProject =
        newGson()
            .fromJson(r.getReader(), new TypeToken<Map<String, ProjectAccessInfo>>() {}.getType());
    assertThat(infoByProject.keySet()).containsExactly(project.get());
  }

  @Test
  public void listAccess_withUrlEncodedProjectName() throws Exception {
    String fooBarBazProjectName = name("foo/bar/baz");
    ProjectInput in = new ProjectInput();
    in.name = fooBarBazProjectName;
    gApi.projects().create(in);

    RestResponse r =
        adminRestSession.get("/access/?project=" + IdString.fromDecoded(fooBarBazProjectName));
    r.assertOK();
    Map<String, ProjectAccessInfo> infoByProject =
        newGson()
            .fromJson(r.getReader(), new TypeToken<Map<String, ProjectAccessInfo>>() {}.getType());
    assertThat(infoByProject.keySet()).containsExactly(fooBarBazProjectName);
  }

  @Test
  public void listAccess_projectNameAreTrimmed() throws Exception {
    RestResponse r =
        adminRestSession.get("/access/?project=" + IdString.fromDecoded(" " + project.get() + " "));
    r.assertOK();
    Map<String, ProjectAccessInfo> infoByProject =
        newGson()
            .fromJson(r.getReader(), new TypeToken<Map<String, ProjectAccessInfo>>() {}.getType());
    assertThat(infoByProject.keySet()).containsExactly(project.get());
  }
}
