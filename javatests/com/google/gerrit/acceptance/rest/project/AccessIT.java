// Copyright (C) 2016 The Android Open Source Project
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
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.extensions.client.ListChangesOption.MESSAGES;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.schema.AclUtil.grant;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static com.google.gerrit.truth.ConfigSubject.assertThat;
import static com.google.gerrit.truth.MapSubject.assertThatMap;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.AccessSection;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.access.AccessSectionInfo;
import com.google.gerrit.extensions.api.access.PermissionInfo;
import com.google.gerrit.extensions.api.access.PermissionRuleInfo;
import com.google.gerrit.extensions.api.access.ProjectAccessInfo;
import com.google.gerrit.extensions.api.access.ProjectAccessInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.projects.ProjectApi;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.common.WebLinkInfo;
import com.google.gerrit.extensions.config.CapabilityDefinition;
import com.google.gerrit.extensions.config.PluginProjectPermissionDefinition;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.webui.FileHistoryWebLink;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.schema.GrantRevertPermission;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

public class AccessIT extends AbstractDaemonTest {

  private static final String REFS_ALL = Constants.R_REFS + "*";
  private static final String REFS_HEADS = Constants.R_HEADS + "*";

  private static final String LABEL_CODE_REVIEW = "Code-Review";

  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ExtensionRegistry extensionRegistry;
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
    projectCache.evict(newProjectName);
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

  @Test
  public void getDefaultInheritance() throws Exception {
    String inheritedName = pApi().access().inheritsFrom.name;
    assertThat(inheritedName).isEqualTo(AllProjectsNameProvider.DEFAULT);
  }

  private Registration newFileHistoryWebLink() {
    FileHistoryWebLink weblink =
        new FileHistoryWebLink() {
          @Override
          public WebLinkInfo getFileHistoryWebLink(
              String projectName, String revision, String fileName) {
            return new WebLinkInfo(
                "name", "imageURL", "http://view/" + projectName + "/" + fileName);
          }
        };
    return extensionRegistry.newRegistration().add(weblink);
  }

  @Test
  public void webLink() throws Exception {
    try (Registration registration = newFileHistoryWebLink()) {
      ProjectAccessInfo info = pApi().access();
      assertThat(info.configWebLinks).hasSize(1);
      assertThat(info.configWebLinks.get(0).url)
          .isEqualTo("http://view/" + newProjectName + "/project.config");
    }
  }

  @Test
  public void webLinkNoRefsMetaConfig() throws Exception {
    try (Repository repo = repoManager.openRepository(newProjectName);
        Registration registration = newFileHistoryWebLink()) {
      RefUpdate u = repo.updateRef(RefNames.REFS_CONFIG);
      u.setForceUpdate(true);
      assertThat(u.delete()).isEqualTo(Result.FORCED);

      // This should not crash.
      pApi().access();
    }
  }

  @Test
  public void addAccessSection() throws Exception {
    RevCommit initialHead = projectOperations.project(newProjectName).getHead(RefNames.REFS_CONFIG);

    ProjectAccessInput accessInput = newProjectAccessInput();
    AccessSectionInfo accessSectionInfo = createDefaultAccessSectionInfo();

    accessInput.add.put(REFS_HEADS, accessSectionInfo);
    pApi().access(accessInput);

    assertThat(pApi().access().local).isEqualTo(accessInput.add);

    RevCommit updatedHead = projectOperations.project(newProjectName).getHead(RefNames.REFS_CONFIG);
    eventRecorder.assertRefUpdatedEvents(
        newProjectName.get(), RefNames.REFS_CONFIG, null, initialHead, initialHead, updatedHead);
  }

  @Test
  public void addAccessSectionForPluginPermission() throws Exception {
    try (Registration registration =
        extensionRegistry
            .newRegistration()
            .add(
                new PluginProjectPermissionDefinition() {
                  @Override
                  public String getDescription() {
                    return "A Plugin Project Permission";
                  }
                },
                "fooPermission")) {
      ProjectAccessInput accessInput = newProjectAccessInput();
      AccessSectionInfo accessSectionInfo = newAccessSectionInfo();

      PermissionInfo foo = newPermissionInfo();
      PermissionRuleInfo pri = new PermissionRuleInfo(PermissionRuleInfo.Action.ALLOW, false);
      foo.rules.put(SystemGroupBackend.REGISTERED_USERS.get(), pri);
      accessSectionInfo.permissions.put(
          "plugin-" + ExtensionRegistry.PLUGIN_NAME + "-fooPermission", foo);

      accessInput.add.put(REFS_HEADS, accessSectionInfo);
      ProjectAccessInfo updatedAccessSectionInfo = pApi().access(accessInput);
      assertThat(updatedAccessSectionInfo.local).isEqualTo(accessInput.add);

      assertThat(pApi().access().local).isEqualTo(accessInput.add);
    }
  }

  @Test
  public void addAccessSectionWithInvalidPermission() throws Exception {
    ProjectAccessInput accessInput = newProjectAccessInput();
    AccessSectionInfo accessSectionInfo = createDefaultAccessSectionInfo();

    PermissionInfo push = newPermissionInfo();
    PermissionRuleInfo pri = new PermissionRuleInfo(PermissionRuleInfo.Action.ALLOW, false);
    push.rules.put(SystemGroupBackend.REGISTERED_USERS.get(), pri);
    accessSectionInfo.permissions.put("Invalid Permission", push);

    accessInput.add.put(REFS_HEADS, accessSectionInfo);
    BadRequestException ex =
        assertThrows(BadRequestException.class, () -> pApi().access(accessInput));
    assertThat(ex).hasMessageThat().isEqualTo("Unknown permission: Invalid Permission");
  }

  @Test
  public void addAccessSectionWithInvalidLabelPermission() throws Exception {
    ProjectAccessInput accessInput = newProjectAccessInput();
    AccessSectionInfo accessSectionInfo = createDefaultAccessSectionInfo();

    PermissionInfo push = newPermissionInfo();
    PermissionRuleInfo pri = new PermissionRuleInfo(PermissionRuleInfo.Action.ALLOW, false);
    push.rules.put(SystemGroupBackend.REGISTERED_USERS.get(), pri);
    accessSectionInfo.permissions.put("label-Invalid Permission", push);

    accessInput.add.put(REFS_HEADS, accessSectionInfo);
    BadRequestException ex =
        assertThrows(BadRequestException.class, () -> pApi().access(accessInput));
    assertThat(ex).hasMessageThat().isEqualTo("Unknown permission: label-Invalid Permission");
  }

  @Test
  public void createAccessChangeNop() throws Exception {
    ProjectAccessInput accessInput = newProjectAccessInput();
    assertThrows(BadRequestException.class, () -> pApi().accessChange(accessInput));
  }

  @Test
  public void createAccessChangeEmptyConfig() throws Exception {
    try (Repository repo = repoManager.openRepository(newProjectName)) {
      RefUpdate ru = repo.updateRef(RefNames.REFS_CONFIG);
      ru.setForceUpdate(true);
      assertThat(ru.delete()).isEqualTo(Result.FORCED);

      ProjectAccessInput accessInput = newProjectAccessInput();
      AccessSectionInfo accessSection = newAccessSectionInfo();
      PermissionInfo read = newPermissionInfo();
      PermissionRuleInfo pri = new PermissionRuleInfo(PermissionRuleInfo.Action.BLOCK, false);
      read.rules.put(SystemGroupBackend.REGISTERED_USERS.get(), pri);
      accessSection.permissions.put(Permission.READ, read);
      accessInput.add.put(REFS_HEADS, accessSection);

      ChangeInfo out = pApi().accessChange(accessInput);
      assertThat(out.status).isEqualTo(ChangeStatus.NEW);
    }
  }

  @Test
  public void createAccessChange() throws Exception {
    projectOperations
        .project(newProjectName)
        .forUpdate()
        .add(allow(Permission.READ).ref(RefNames.REFS_CONFIG).group(REGISTERED_USERS))
        .update();
    // User can see the branch
    requestScopeOperations.setApiUser(user.id());
    pApi().branch("refs/heads/master").get();

    ProjectAccessInput accessInput = newProjectAccessInput();

    AccessSectionInfo accessSection = newAccessSectionInfo();

    // Deny read to registered users.
    PermissionInfo read = newPermissionInfo();
    PermissionRuleInfo pri = new PermissionRuleInfo(PermissionRuleInfo.Action.DENY, false);
    read.rules.put(SystemGroupBackend.REGISTERED_USERS.get(), pri);
    read.exclusive = true;
    accessSection.permissions.put(Permission.READ, read);
    accessInput.add.put(REFS_HEADS, accessSection);

    requestScopeOperations.setApiUser(user.id());
    ChangeInfo out = pApi().accessChange(accessInput);

    assertThat(out.project).isEqualTo(newProjectName.get());
    assertThat(out.branch).isEqualTo(RefNames.REFS_CONFIG);
    assertThat(out.status).isEqualTo(ChangeStatus.NEW);
    assertThat(out.submitted).isNull();

    requestScopeOperations.setApiUser(admin.id());

    ChangeInfo c = gApi.changes().id(out._number).get(MESSAGES);
    assertThat(c.messages.stream().map(m -> m.message)).containsExactly("Uploaded patch set 1");

    ReviewInput reviewIn = new ReviewInput();
    reviewIn.label("Code-Review", (short) 2);
    gApi.changes().id(out._number).current().review(reviewIn);
    gApi.changes().id(out._number).current().submit();

    // check that the change took effect.
    requestScopeOperations.setApiUser(user.id());
    assertThrows(ResourceNotFoundException.class, () -> pApi().branch("refs/heads/master").get());

    // Restore.
    accessInput.add.clear();
    accessInput.remove.put(REFS_HEADS, accessSection);
    requestScopeOperations.setApiUser(user.id());

    requestScopeOperations.setApiUser(admin.id());
    out = pApi().accessChange(accessInput);

    gApi.changes().id(out._number).current().review(reviewIn);
    gApi.changes().id(out._number).current().submit();

    // Now it works again.
    requestScopeOperations.setApiUser(user.id());
    pApi().branch("refs/heads/master").get();
  }

  @Test
  public void removePermission() throws Exception {
    // Add initial permission set
    ProjectAccessInput accessInput = newProjectAccessInput();
    AccessSectionInfo accessSectionInfo = createDefaultAccessSectionInfo();

    accessInput.add.put(REFS_HEADS, accessSectionInfo);
    pApi().access(accessInput);

    // Remove specific permission
    AccessSectionInfo accessSectionToRemove = newAccessSectionInfo();
    accessSectionToRemove.permissions.put(
        Permission.LABEL + LABEL_CODE_REVIEW, newPermissionInfo());
    ProjectAccessInput removal = newProjectAccessInput();
    removal.remove.put(REFS_HEADS, accessSectionToRemove);
    pApi().access(removal);

    // Remove locally
    accessInput.add.get(REFS_HEADS).permissions.remove(Permission.LABEL + LABEL_CODE_REVIEW);

    // Check
    assertThat(pApi().access().local).isEqualTo(accessInput.add);
  }

  @Test
  public void removePermissionRule() throws Exception {
    // Add initial permission set
    ProjectAccessInput accessInput = newProjectAccessInput();
    AccessSectionInfo accessSectionInfo = createDefaultAccessSectionInfo();

    accessInput.add.put(REFS_HEADS, accessSectionInfo);
    pApi().access(accessInput);

    // Remove specific permission rule
    AccessSectionInfo accessSectionToRemove = newAccessSectionInfo();
    PermissionInfo codeReview = newPermissionInfo();
    codeReview.label = LABEL_CODE_REVIEW;
    PermissionRuleInfo pri = new PermissionRuleInfo(PermissionRuleInfo.Action.DENY, false);
    codeReview.rules.put(SystemGroupBackend.REGISTERED_USERS.get(), pri);
    accessSectionToRemove.permissions.put(Permission.LABEL + LABEL_CODE_REVIEW, codeReview);
    ProjectAccessInput removal = newProjectAccessInput();
    removal.remove.put(REFS_HEADS, accessSectionToRemove);
    pApi().access(removal);

    // Remove locally
    accessInput
        .add
        .get(REFS_HEADS)
        .permissions
        .get(Permission.LABEL + LABEL_CODE_REVIEW)
        .rules
        .remove(SystemGroupBackend.REGISTERED_USERS.get());

    // Check
    assertThat(pApi().access().local).isEqualTo(accessInput.add);
  }

  @Test
  public void removePermissionRulesAndCleanupEmptyEntries() throws Exception {
    // Add initial permission set
    ProjectAccessInput accessInput = newProjectAccessInput();
    AccessSectionInfo accessSectionInfo = createDefaultAccessSectionInfo();

    accessInput.add.put(REFS_HEADS, accessSectionInfo);
    pApi().access(accessInput);

    // Remove specific permission rules
    AccessSectionInfo accessSectionToRemove = newAccessSectionInfo();
    PermissionInfo codeReview = newPermissionInfo();
    codeReview.label = LABEL_CODE_REVIEW;
    PermissionRuleInfo pri = new PermissionRuleInfo(PermissionRuleInfo.Action.DENY, false);
    codeReview.rules.put(SystemGroupBackend.REGISTERED_USERS.get(), pri);
    pri = new PermissionRuleInfo(PermissionRuleInfo.Action.DENY, false);
    codeReview.rules.put(SystemGroupBackend.PROJECT_OWNERS.get(), pri);
    accessSectionToRemove.permissions.put(Permission.LABEL + LABEL_CODE_REVIEW, codeReview);
    ProjectAccessInput removal = newProjectAccessInput();
    removal.remove.put(REFS_HEADS, accessSectionToRemove);
    pApi().access(removal);

    // Remove locally
    accessInput.add.get(REFS_HEADS).permissions.remove(Permission.LABEL + LABEL_CODE_REVIEW);

    // Check
    assertThat(pApi().access().local).isEqualTo(accessInput.add);
  }

  @Test
  public void getPermissionsWithDisallowedUser() throws Exception {
    // Add initial permission set
    ProjectAccessInput accessInput = newProjectAccessInput();
    AccessSectionInfo accessSectionInfo = createAccessSectionInfoDenyAll();

    // Disallow READ
    accessInput.add.put(REFS_ALL, accessSectionInfo);
    pApi().access(accessInput);

    requestScopeOperations.setApiUser(user.id());
    assertThrows(ResourceNotFoundException.class, () -> pApi().access());
  }

  @Test
  public void setPermissionsWithDisallowedUser() throws Exception {
    // Add initial permission set
    ProjectAccessInput accessInput = newProjectAccessInput();
    AccessSectionInfo accessSectionInfo = createAccessSectionInfoDenyAll();

    // Disallow READ
    accessInput.add.put(REFS_ALL, accessSectionInfo);
    pApi().access(accessInput);

    // Create a change to apply
    ProjectAccessInput accessInfoToApply = newProjectAccessInput();
    AccessSectionInfo accessSectionInfoToApply = createDefaultAccessSectionInfo();
    accessInfoToApply.add.put(REFS_HEADS, accessSectionInfoToApply);

    requestScopeOperations.setApiUser(user.id());
    assertThrows(ResourceNotFoundException.class, () -> pApi().access());
  }

  @Test
  public void permissionsGroupMap() throws Exception {
    // Add initial permission set
    ProjectAccessInput accessInput = newProjectAccessInput();
    AccessSectionInfo accessSection = newAccessSectionInfo();

    PermissionInfo push = newPermissionInfo();
    PermissionRuleInfo pri = new PermissionRuleInfo(PermissionRuleInfo.Action.ALLOW, false);
    push.rules.put(SystemGroupBackend.PROJECT_OWNERS.get(), pri);
    accessSection.permissions.put(Permission.PUSH, push);

    PermissionInfo read = newPermissionInfo();
    pri = new PermissionRuleInfo(PermissionRuleInfo.Action.ALLOW, false);
    read.rules.put(SystemGroupBackend.ANONYMOUS_USERS.get(), pri);
    accessSection.permissions.put(Permission.READ, read);

    accessInput.add.put(REFS_ALL, accessSection);
    ProjectAccessInfo result = pApi().access(accessInput);
    assertThatMap(result.groups)
        .keys()
        .containsExactly(
            SystemGroupBackend.PROJECT_OWNERS.get(), SystemGroupBackend.ANONYMOUS_USERS.get());

    // Check the name, which is what the UI cares about; exhaustive
    // coverage of GroupInfo should be in groups REST API tests.
    assertThat(result.groups.get(SystemGroupBackend.PROJECT_OWNERS.get()).name)
        .isEqualTo("Project Owners");
    // Strip the ID, since it is in the key.
    assertThat(result.groups.get(SystemGroupBackend.PROJECT_OWNERS.get()).id).isNull();

    // Get call returns groups too.
    ProjectAccessInfo loggedInResult = pApi().access();
    assertThatMap(loggedInResult.groups)
        .keys()
        .containsExactly(
            SystemGroupBackend.PROJECT_OWNERS.get(), SystemGroupBackend.ANONYMOUS_USERS.get());

    GroupInfo owners = loggedInResult.groups.get(SystemGroupBackend.PROJECT_OWNERS.get());
    assertThat(owners.name).isEqualTo("Project Owners");
    assertThat(owners.id).isNull();
    assertThat(owners.members).isNull();
    assertThat(owners.includes).isNull();

    // PROJECT_OWNERS is invisible to anonymous user, but GetAccess disregards visibility.
    requestScopeOperations.setApiUserAnonymous();
    ProjectAccessInfo anonResult = pApi().access();
    assertThatMap(anonResult.groups)
        .keys()
        .containsExactly(
            SystemGroupBackend.PROJECT_OWNERS.get(), SystemGroupBackend.ANONYMOUS_USERS.get());
  }

  @Test
  public void updateParentAsUser() throws Exception {
    // Create child
    String newParentProjectName = projectOperations.newProject().create().get();

    // Set new parent
    ProjectAccessInput accessInput = newProjectAccessInput();
    accessInput.parent = newParentProjectName;

    requestScopeOperations.setApiUser(user.id());
    AuthException thrown = assertThrows(AuthException.class, () -> pApi().access(accessInput));
    assertThat(thrown).hasMessageThat().contains("administrate server not permitted");
  }

  @Test
  public void updateParentAsAdministrator() throws Exception {
    // Create parent
    String newParentProjectName = projectOperations.newProject().create().get();

    // Set new parent
    ProjectAccessInput accessInput = newProjectAccessInput();
    accessInput.parent = newParentProjectName;

    pApi().access(accessInput);

    assertThat(pApi().access().inheritsFrom.name).isEqualTo(newParentProjectName);
  }

  @Test
  public void addGlobalCapabilityAsUser() throws Exception {
    ProjectAccessInput accessInput = newProjectAccessInput();
    AccessSectionInfo accessSectionInfo = createDefaultGlobalCapabilitiesAccessSectionInfo();

    accessInput.add.put(AccessSection.GLOBAL_CAPABILITIES, accessSectionInfo);

    requestScopeOperations.setApiUser(user.id());
    assertThrows(
        AuthException.class, () -> gApi.projects().name(allProjects.get()).access(accessInput));
  }

  @Test
  public void addGlobalCapabilityAsAdmin() throws Exception {
    ProjectAccessInput accessInput = newProjectAccessInput();
    AccessSectionInfo accessSectionInfo = createDefaultGlobalCapabilitiesAccessSectionInfo();

    accessInput.add.put(AccessSection.GLOBAL_CAPABILITIES, accessSectionInfo);

    ProjectAccessInfo updatedAccessSectionInfo =
        gApi.projects().name(allProjects.get()).access(accessInput);
    assertThatMap(updatedAccessSectionInfo.local.get(AccessSection.GLOBAL_CAPABILITIES).permissions)
        .keys()
        .containsAtLeastElementsIn(accessSectionInfo.permissions.keySet());
  }

  @Test
  public void addPluginGlobalCapability() throws Exception {
    try (Registration registration =
        extensionRegistry
            .newRegistration()
            .add(
                new CapabilityDefinition() {
                  @Override
                  public String getDescription() {
                    return "A Plugin Global Capability";
                  }
                },
                "fooCapability")) {
      ProjectAccessInput accessInput = newProjectAccessInput();
      AccessSectionInfo accessSectionInfo = newAccessSectionInfo();

      PermissionInfo foo = newPermissionInfo();
      PermissionRuleInfo pri = new PermissionRuleInfo(PermissionRuleInfo.Action.ALLOW, false);
      foo.rules.put(SystemGroupBackend.REGISTERED_USERS.get(), pri);
      accessSectionInfo.permissions.put(ExtensionRegistry.PLUGIN_NAME + "-fooCapability", foo);

      accessInput.add.put(AccessSection.GLOBAL_CAPABILITIES, accessSectionInfo);

      ProjectAccessInfo updatedAccessSectionInfo =
          gApi.projects().name(allProjects.get()).access(accessInput);
      assertThatMap(
              updatedAccessSectionInfo.local.get(AccessSection.GLOBAL_CAPABILITIES).permissions)
          .keys()
          .containsAtLeastElementsIn(accessSectionInfo.permissions.keySet());
    }
  }

  @Test
  public void addPermissionAsGlobalCapability() throws Exception {
    ProjectAccessInput accessInput = newProjectAccessInput();
    AccessSectionInfo accessSectionInfo = newAccessSectionInfo();

    PermissionInfo push = newPermissionInfo();
    PermissionRuleInfo pri = new PermissionRuleInfo(PermissionRuleInfo.Action.ALLOW, false);
    push.rules.put(SystemGroupBackend.REGISTERED_USERS.get(), pri);
    accessSectionInfo.permissions.put(Permission.PUSH, push);

    accessInput.add.put(AccessSection.GLOBAL_CAPABILITIES, accessSectionInfo);
    BadRequestException ex =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(allProjects.get()).access(accessInput));
    assertThat(ex).hasMessageThat().isEqualTo("Unknown global capability: " + Permission.PUSH);
  }

  @Test
  public void addInvalidGlobalCapability() throws Exception {
    ProjectAccessInput accessInput = newProjectAccessInput();
    AccessSectionInfo accessSectionInfo = createDefaultGlobalCapabilitiesAccessSectionInfo();

    PermissionInfo permissionInfo = newPermissionInfo();
    PermissionRuleInfo pri = new PermissionRuleInfo(PermissionRuleInfo.Action.ALLOW, false);
    permissionInfo.rules.put(SystemGroupBackend.REGISTERED_USERS.get(), pri);
    accessSectionInfo.permissions.put("Invalid Global Capability", permissionInfo);

    accessInput.add.put(AccessSection.GLOBAL_CAPABILITIES, accessSectionInfo);
    BadRequestException ex =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(allProjects.get()).access(accessInput));
    assertThat(ex)
        .hasMessageThat()
        .isEqualTo("Unknown global capability: Invalid Global Capability");
  }

  @Test
  public void addGlobalCapabilityForNonRootProject() throws Exception {
    ProjectAccessInput accessInput = newProjectAccessInput();
    AccessSectionInfo accessSectionInfo = createDefaultGlobalCapabilitiesAccessSectionInfo();

    accessInput.add.put(AccessSection.GLOBAL_CAPABILITIES, accessSectionInfo);

    assertThrows(BadRequestException.class, () -> pApi().access(accessInput));
  }

  @Test
  public void addNonGlobalCapabilityToGlobalCapabilities() throws Exception {
    ProjectAccessInput accessInput = newProjectAccessInput();
    AccessSectionInfo accessSectionInfo = newAccessSectionInfo();

    PermissionInfo permissionInfo = newPermissionInfo();
    permissionInfo.rules.put(adminGroupUuid().get(), null);
    accessSectionInfo.permissions.put(Permission.PUSH, permissionInfo);

    accessInput.add.put(AccessSection.GLOBAL_CAPABILITIES, accessSectionInfo);
    assertThrows(
        BadRequestException.class,
        () -> gApi.projects().name(allProjects.get()).access(accessInput));
  }

  @Test
  public void removeGlobalCapabilityAsUser() throws Exception {
    ProjectAccessInput accessInput = newProjectAccessInput();
    AccessSectionInfo accessSectionInfo = createDefaultGlobalCapabilitiesAccessSectionInfo();

    accessInput.remove.put(AccessSection.GLOBAL_CAPABILITIES, accessSectionInfo);

    requestScopeOperations.setApiUser(user.id());
    assertThrows(
        AuthException.class, () -> gApi.projects().name(allProjects.get()).access(accessInput));
  }

  @Test
  public void removeGlobalCapabilityAsAdmin() throws Exception {
    ProjectAccessInput accessInput = newProjectAccessInput();
    AccessSectionInfo accessSectionInfo = newAccessSectionInfo();

    PermissionInfo permissionInfo = newPermissionInfo();
    permissionInfo.rules.put(adminGroupUuid().get(), null);
    accessSectionInfo.permissions.put(GlobalCapability.ACCESS_DATABASE, permissionInfo);

    // Add and validate first as removing existing privileges such as
    // administrateServer would break upcoming tests
    accessInput.add.put(AccessSection.GLOBAL_CAPABILITIES, accessSectionInfo);

    ProjectAccessInfo updatedProjectAccessInfo =
        gApi.projects().name(allProjects.get()).access(accessInput);
    assertThatMap(updatedProjectAccessInfo.local.get(AccessSection.GLOBAL_CAPABILITIES).permissions)
        .keys()
        .containsAtLeastElementsIn(accessSectionInfo.permissions.keySet());

    // Remove
    accessInput.add.clear();
    accessInput.remove.put(AccessSection.GLOBAL_CAPABILITIES, accessSectionInfo);

    updatedProjectAccessInfo = gApi.projects().name(allProjects.get()).access(accessInput);
    assertThatMap(updatedProjectAccessInfo.local.get(AccessSection.GLOBAL_CAPABILITIES).permissions)
        .keys()
        .containsNoneIn(accessSectionInfo.permissions.keySet());
  }

  @Test
  public void unknownPermissionRemainsUnchanged() throws Exception {
    String access = "access";
    String unknownPermission = "unknownPermission";
    String registeredUsers = "group Registered Users";
    String refsFor = "refs/for/*";
    // Clone repository to forcefully add permission
    TestRepository<InMemoryRepository> allProjectsRepo = cloneProject(allProjects, admin);

    // Fetch permission ref
    GitUtil.fetch(allProjectsRepo, "refs/meta/config:cfg");
    allProjectsRepo.reset("cfg");

    // Load current permissions
    String config =
        gApi.projects()
            .name(allProjects.get())
            .branch(RefNames.REFS_CONFIG)
            .file(ProjectConfig.PROJECT_CONFIG)
            .asString();

    // Append and push unknown permission
    Config cfg = new Config();
    cfg.fromText(config);
    cfg.setString(access, refsFor, unknownPermission, registeredUsers);
    config = cfg.toText();
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(), allProjectsRepo, "Subject", ProjectConfig.PROJECT_CONFIG, config);
    push.to(RefNames.REFS_CONFIG).assertOkStatus();

    // Verify that unknownPermission is present
    config =
        gApi.projects()
            .name(allProjects.get())
            .branch(RefNames.REFS_CONFIG)
            .file(ProjectConfig.PROJECT_CONFIG)
            .asString();
    cfg.fromText(config);
    assertThat(cfg).stringValue(access, refsFor, unknownPermission).isEqualTo(registeredUsers);

    // Make permission change through API
    ProjectAccessInput accessInput = newProjectAccessInput();
    AccessSectionInfo accessSectionInfo = createDefaultAccessSectionInfo();
    accessInput.add.put(refsFor, accessSectionInfo);
    gApi.projects().name(allProjects.get()).access(accessInput);
    accessInput.add.clear();
    accessInput.remove.put(refsFor, accessSectionInfo);
    gApi.projects().name(allProjects.get()).access(accessInput);

    // Verify that unknownPermission is still present
    config =
        gApi.projects()
            .name(allProjects.get())
            .branch(RefNames.REFS_CONFIG)
            .file(ProjectConfig.PROJECT_CONFIG)
            .asString();
    cfg.fromText(config);
    assertThat(cfg).stringValue(access, refsFor, unknownPermission).isEqualTo(registeredUsers);
  }

  @Test
  public void allUsersCanOnlyInheritFromAllProjects() throws Exception {
    ProjectAccessInput accessInput = newProjectAccessInput();
    accessInput.parent = project.get();
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(allUsers.get()).access(accessInput));
    assertThat(thrown)
        .hasMessageThat()
        .contains(allUsers.get() + " must inherit from " + allProjects.get());
  }

  @Test
  public void syncCreateGroupPermission_addAndRemoveCreateGroupCapability() throws Exception {
    // Grant CREATE_GROUP to Registered Users
    ProjectAccessInput accessInput = newProjectAccessInput();
    AccessSectionInfo accessSection = newAccessSectionInfo();
    PermissionInfo createGroup = newPermissionInfo();
    PermissionRuleInfo pri = new PermissionRuleInfo(PermissionRuleInfo.Action.ALLOW, false);
    createGroup.rules.put(SystemGroupBackend.REGISTERED_USERS.get(), pri);
    accessSection.permissions.put(GlobalCapability.CREATE_GROUP, createGroup);
    accessInput.add.put(AccessSection.GLOBAL_CAPABILITIES, accessSection);
    gApi.projects().name(allProjects.get()).access(accessInput);

    // Assert that the permission was synced from All-Projects (global) to All-Users (ref)
    Map<String, AccessSectionInfo> local = gApi.projects().name("All-Users").access().local;
    assertThatMap(local).keys().contains(RefNames.REFS_GROUPS + "*");
    Map<String, PermissionInfo> permissions = local.get(RefNames.REFS_GROUPS + "*").permissions;
    // READ is the default permission and should be preserved by the syncer
    assertThatMap(permissions).keys().containsExactly(Permission.READ, Permission.CREATE);
    Map<String, PermissionRuleInfo> rules = permissions.get(Permission.CREATE).rules;
    assertThatMap(rules).values().containsExactly(pri);

    // Revoke the permission
    accessInput.add.clear();
    accessInput.remove.put(AccessSection.GLOBAL_CAPABILITIES, accessSection);
    gApi.projects().name(allProjects.get()).access(accessInput);

    // Assert that the permission was synced from All-Projects (global) to All-Users (ref)
    Map<String, AccessSectionInfo> local2 = gApi.projects().name("All-Users").access().local;
    assertThatMap(local2).keys().contains(RefNames.REFS_GROUPS + "*");
    Map<String, PermissionInfo> permissions2 = local2.get(RefNames.REFS_GROUPS + "*").permissions;
    // READ is the default permission and should be preserved by the syncer
    assertThatMap(permissions2).keys().containsExactly(Permission.READ);
  }

  @Test
  public void syncCreateGroupPermission_addCreateGroupCapabilityToMultipleGroups()
      throws Exception {
    PermissionRuleInfo pri = new PermissionRuleInfo(PermissionRuleInfo.Action.ALLOW, false);

    // Grant CREATE_GROUP to Registered Users
    ProjectAccessInput accessInput = newProjectAccessInput();
    AccessSectionInfo accessSection = newAccessSectionInfo();
    PermissionInfo createGroup = newPermissionInfo();
    createGroup.rules.put(SystemGroupBackend.REGISTERED_USERS.get(), pri);
    accessSection.permissions.put(GlobalCapability.CREATE_GROUP, createGroup);
    accessInput.add.put(AccessSection.GLOBAL_CAPABILITIES, accessSection);
    gApi.projects().name(allProjects.get()).access(accessInput);

    // Grant CREATE_GROUP to Administrators
    accessInput = newProjectAccessInput();
    accessSection = newAccessSectionInfo();
    createGroup = newPermissionInfo();
    createGroup.rules.put(adminGroupUuid().get(), pri);
    accessSection.permissions.put(GlobalCapability.CREATE_GROUP, createGroup);
    accessInput.add.put(AccessSection.GLOBAL_CAPABILITIES, accessSection);
    gApi.projects().name(allProjects.get()).access(accessInput);

    // Assert that the permissions were synced from All-Projects (global) to All-Users (ref)
    Map<String, AccessSectionInfo> local = gApi.projects().name("All-Users").access().local;
    assertThatMap(local).keys().contains(RefNames.REFS_GROUPS + "*");
    Map<String, PermissionInfo> permissions = local.get(RefNames.REFS_GROUPS + "*").permissions;
    // READ is the default permission and should be preserved by the syncer
    assertThatMap(permissions).keys().containsExactly(Permission.READ, Permission.CREATE);
    Map<String, PermissionRuleInfo> rules = permissions.get(Permission.CREATE).rules;
    assertThatMap(rules)
        .keys()
        .containsExactly(SystemGroupBackend.REGISTERED_USERS.get(), adminGroupUuid().get());
    assertThat(rules.get(SystemGroupBackend.REGISTERED_USERS.get())).isEqualTo(pri);
    assertThat(rules.get(adminGroupUuid().get())).isEqualTo(pri);
  }

  @Test
  public void addAccessSectionForInvalidRef() throws Exception {
    ProjectAccessInput accessInput = newProjectAccessInput();
    AccessSectionInfo accessSectionInfo = createDefaultAccessSectionInfo();

    // 'refs/heads/stable_*' is invalid, correct would be '^refs/heads/stable_.*'
    String invalidRef = Constants.R_HEADS + "stable_*";
    accessInput.add.put(invalidRef, accessSectionInfo);

    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> pApi().access(accessInput));
    assertThat(thrown).hasMessageThat().contains("Invalid Name: " + invalidRef);
  }

  @Test
  public void createAccessChangeWithAccessSectionForInvalidRef() throws Exception {
    ProjectAccessInput accessInput = newProjectAccessInput();
    AccessSectionInfo accessSectionInfo = createDefaultAccessSectionInfo();

    // 'refs/heads/stable_*' is invalid, correct would be '^refs/heads/stable_.*'
    String invalidRef = Constants.R_HEADS + "stable_*";
    accessInput.add.put(invalidRef, accessSectionInfo);

    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> pApi().accessChange(accessInput));
    assertThat(thrown).hasMessageThat().contains("Invalid Name: " + invalidRef);
  }

  private ProjectApi pApi() throws Exception {
    return gApi.projects().name(newProjectName.get());
  }

  private ProjectAccessInput newProjectAccessInput() {
    ProjectAccessInput p = new ProjectAccessInput();
    p.add = new HashMap<>();
    p.remove = new HashMap<>();
    return p;
  }

  private PermissionInfo newPermissionInfo() {
    PermissionInfo p = new PermissionInfo(null, null);
    p.rules = new HashMap<>();
    return p;
  }

  private AccessSectionInfo newAccessSectionInfo() {
    AccessSectionInfo a = new AccessSectionInfo();
    a.permissions = new HashMap<>();
    return a;
  }

  private AccessSectionInfo createDefaultAccessSectionInfo() {
    AccessSectionInfo accessSection = newAccessSectionInfo();

    PermissionInfo push = newPermissionInfo();
    PermissionRuleInfo pri = new PermissionRuleInfo(PermissionRuleInfo.Action.ALLOW, false);
    push.rules.put(SystemGroupBackend.REGISTERED_USERS.get(), pri);
    accessSection.permissions.put(Permission.PUSH, push);

    PermissionInfo codeReview = newPermissionInfo();
    codeReview.label = LABEL_CODE_REVIEW;
    pri = new PermissionRuleInfo(PermissionRuleInfo.Action.DENY, false);
    codeReview.rules.put(SystemGroupBackend.REGISTERED_USERS.get(), pri);

    pri = new PermissionRuleInfo(PermissionRuleInfo.Action.ALLOW, false);
    pri.max = 1;
    pri.min = -1;
    codeReview.rules.put(SystemGroupBackend.PROJECT_OWNERS.get(), pri);
    accessSection.permissions.put(Permission.LABEL + LABEL_CODE_REVIEW, codeReview);

    return accessSection;
  }

  private AccessSectionInfo createDefaultGlobalCapabilitiesAccessSectionInfo() {
    AccessSectionInfo accessSection = newAccessSectionInfo();

    PermissionInfo email = newPermissionInfo();
    PermissionRuleInfo pri = new PermissionRuleInfo(PermissionRuleInfo.Action.ALLOW, false);
    email.rules.put(SystemGroupBackend.REGISTERED_USERS.get(), pri);
    accessSection.permissions.put(GlobalCapability.EMAIL_REVIEWERS, email);

    return accessSection;
  }

  private AccessSectionInfo createAccessSectionInfoDenyAll() {
    AccessSectionInfo accessSection = newAccessSectionInfo();

    PermissionInfo read = newPermissionInfo();
    PermissionRuleInfo pri = new PermissionRuleInfo(PermissionRuleInfo.Action.DENY, false);
    read.rules.put(SystemGroupBackend.ANONYMOUS_USERS.get(), pri);
    accessSection.permissions.put(Permission.READ, read);

    return accessSection;
  }
}
