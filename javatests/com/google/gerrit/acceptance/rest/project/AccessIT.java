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
import static com.google.gerrit.extensions.client.ListChangesOption.MESSAGES;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.config.AllProjectsNameProvider;
import com.google.gerrit.extensions.api.access.AccessSectionInfo;
import com.google.gerrit.extensions.api.access.PermissionInfo;
import com.google.gerrit.extensions.api.access.PermissionRuleInfo;
import com.google.gerrit.extensions.api.access.ProjectAccessInfo;
import com.google.gerrit.extensions.api.access.ProjectAccessInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.projects.BranchInfo;
import com.google.gerrit.extensions.api.projects.ProjectApi;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.common.WebLinkInfo;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.webui.FileHistoryWebLink;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.ProjectConfig;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
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

  private static final String PROJECT_NAME = "newProject";

  private static final String REFS_ALL = Constants.R_REFS + "*";
  private static final String REFS_HEADS = Constants.R_HEADS + "*";

  private static final String LABEL_CODE_REVIEW = "Code-Review";

  private Project.NameKey newProjectName;

  @Inject private DynamicSet<FileHistoryWebLink> fileHistoryWebLinkDynamicSet;

  @Before
  public void setUp() throws Exception {
    newProjectName = createProject(PROJECT_NAME);
  }

  @Test
  public void getDefaultInheritance() throws Exception {
    String inheritedName = pApi().access().inheritsFrom.name;
    assertThat(inheritedName).isEqualTo(AllProjectsNameProvider.DEFAULT);
  }

  @Test
  public void webLink() throws Exception {
    RegistrationHandle handle =
        fileHistoryWebLinkDynamicSet.add(
            new FileHistoryWebLink() {
              @Override
              public WebLinkInfo getFileHistoryWebLink(
                  String projectName, String revision, String fileName) {
                return new WebLinkInfo(
                    "name", "imageURL", "http://view/" + projectName + "/" + fileName);
              }
            });
    try {
      ProjectAccessInfo info = pApi().access();
      assertThat(info.configWebLinks).hasSize(1);
      assertThat(info.configWebLinks.get(0).url)
          .isEqualTo("http://view/" + newProjectName + "/project.config");
    } finally {
      handle.remove();
    }
  }

  @Test
  public void webLinkNoRefsMetaConfig() throws Exception {
    RegistrationHandle handle =
        fileHistoryWebLinkDynamicSet.add(
            new FileHistoryWebLink() {
              @Override
              public WebLinkInfo getFileHistoryWebLink(
                  String projectName, String revision, String fileName) {
                return new WebLinkInfo(
                    "name", "imageURL", "http://view/" + projectName + "/" + fileName);
              }
            });
    try (Repository repo = repoManager.openRepository(newProjectName)) {
      RefUpdate u = repo.updateRef(RefNames.REFS_CONFIG);
      u.setForceUpdate(true);
      assertThat(u.delete()).isEqualTo(Result.FORCED);

      // This should not crash.
      pApi().access();
    } finally {
      handle.remove();
    }
  }

  @Test
  public void addAccessSection() throws Exception {
    RevCommit initialHead = getRemoteHead(newProjectName, RefNames.REFS_CONFIG);

    ProjectAccessInput accessInput = newProjectAccessInput();
    AccessSectionInfo accessSectionInfo = createDefaultAccessSectionInfo();

    accessInput.add.put(REFS_HEADS, accessSectionInfo);
    pApi().access(accessInput);

    assertThat(pApi().access().local).isEqualTo(accessInput.add);

    RevCommit updatedHead = getRemoteHead(newProjectName, RefNames.REFS_CONFIG);
    eventRecorder.assertRefUpdatedEvents(
        newProjectName.get(), RefNames.REFS_CONFIG, null, initialHead, initialHead, updatedHead);
  }

  @Test
  public void createAccessChangeNop() throws Exception {
    ProjectAccessInput accessInput = newProjectAccessInput();
    exception.expect(BadRequestException.class);
    pApi().accessChange(accessInput);
  }

  @Test
  public void createAccessChange() throws Exception {
    allow(newProjectName, RefNames.REFS_CONFIG, Permission.READ, REGISTERED_USERS);
    // User can see the branch
    setApiUser(user);
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

    setApiUser(user);
    ChangeInfo out = pApi().accessChange(accessInput);

    assertThat(out.project).isEqualTo(newProjectName.get());
    assertThat(out.branch).isEqualTo(RefNames.REFS_CONFIG);
    assertThat(out.status).isEqualTo(ChangeStatus.NEW);
    assertThat(out.submitted).isNull();

    setApiUser(admin);

    ChangeInfo c = gApi.changes().id(out._number).get(MESSAGES);
    assertThat(c.messages.stream().map(m -> m.message)).containsExactly("Uploaded patch set 1");

    ReviewInput reviewIn = new ReviewInput();
    reviewIn.label("Code-Review", (short) 2);
    gApi.changes().id(out._number).current().review(reviewIn);
    gApi.changes().id(out._number).current().submit();

    // check that the change took effect.
    setApiUser(user);
    try {
      BranchInfo info = pApi().branch("refs/heads/master").get();
      fail("wanted failure, got " + newGson().toJson(info));
    } catch (ResourceNotFoundException e) {
      // OK.
    }

    // Restore.
    accessInput.add.clear();
    accessInput.remove.put(REFS_HEADS, accessSection);
    setApiUser(user);

    setApiUser(admin);
    out = pApi().accessChange(accessInput);

    gApi.changes().id(out._number).current().review(reviewIn);
    gApi.changes().id(out._number).current().submit();

    // Now it works again.
    setApiUser(user);
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

    setApiUser(user);
    exception.expect(ResourceNotFoundException.class);
    pApi().access();
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

    setApiUser(user);
    exception.expect(ResourceNotFoundException.class);
    pApi().access();
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
    assertThat(result.groups.keySet())
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
    assertThat(loggedInResult.groups.keySet())
        .containsExactly(
            SystemGroupBackend.PROJECT_OWNERS.get(), SystemGroupBackend.ANONYMOUS_USERS.get());

    GroupInfo owners = loggedInResult.groups.get(SystemGroupBackend.PROJECT_OWNERS.get());
    assertThat(owners.name).isEqualTo("Project Owners");
    assertThat(owners.id).isNull();
    assertThat(owners.members).isNull();
    assertThat(owners.includes).isNull();

    // PROJECT_OWNERS is invisible to anonymous user, but GetAccess disregards visibility.
    setApiUserAnonymous();
    ProjectAccessInfo anonResult = pApi().access();
    assertThat(anonResult.groups.keySet())
        .containsExactly(
            SystemGroupBackend.PROJECT_OWNERS.get(), SystemGroupBackend.ANONYMOUS_USERS.get());
  }

  @Test
  public void updateParentAsUser() throws Exception {
    // Create child
    String newParentProjectName = createProject(PROJECT_NAME + "PA").get();

    // Set new parent
    ProjectAccessInput accessInput = newProjectAccessInput();
    accessInput.parent = newParentProjectName;

    setApiUser(user);
    exception.expect(AuthException.class);
    exception.expectMessage("administrate server not permitted");
    pApi().access(accessInput);
  }

  @Test
  public void updateParentAsAdministrator() throws Exception {
    // Create parent
    String newParentProjectName = createProject(PROJECT_NAME + "PA").get();

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

    setApiUser(user);
    exception.expect(AuthException.class);
    gApi.projects().name(allProjects.get()).access(accessInput);
  }

  @Test
  public void addGlobalCapabilityAsAdmin() throws Exception {
    ProjectAccessInput accessInput = newProjectAccessInput();
    AccessSectionInfo accessSectionInfo = createDefaultGlobalCapabilitiesAccessSectionInfo();

    accessInput.add.put(AccessSection.GLOBAL_CAPABILITIES, accessSectionInfo);

    ProjectAccessInfo updatedAccessSectionInfo =
        gApi.projects().name(allProjects.get()).access(accessInput);
    assertThat(
            updatedAccessSectionInfo
                .local
                .get(AccessSection.GLOBAL_CAPABILITIES)
                .permissions
                .keySet())
        .containsAllIn(accessSectionInfo.permissions.keySet());
  }

  @Test
  public void addGlobalCapabilityForNonRootProject() throws Exception {
    ProjectAccessInput accessInput = newProjectAccessInput();
    AccessSectionInfo accessSectionInfo = createDefaultGlobalCapabilitiesAccessSectionInfo();

    accessInput.add.put(AccessSection.GLOBAL_CAPABILITIES, accessSectionInfo);

    exception.expect(BadRequestException.class);
    pApi().access(accessInput);
  }

  @Test
  public void addNonGlobalCapabilityToGlobalCapabilities() throws Exception {
    ProjectAccessInput accessInput = newProjectAccessInput();
    AccessSectionInfo accessSectionInfo = newAccessSectionInfo();

    PermissionInfo permissionInfo = newPermissionInfo();
    permissionInfo.rules.put(adminGroupUuid().get(), null);
    accessSectionInfo.permissions.put(Permission.PUSH, permissionInfo);

    accessInput.add.put(AccessSection.GLOBAL_CAPABILITIES, accessSectionInfo);

    exception.expect(BadRequestException.class);
    gApi.projects().name(allProjects.get()).access(accessInput);
  }

  @Test
  public void removeGlobalCapabilityAsUser() throws Exception {
    ProjectAccessInput accessInput = newProjectAccessInput();
    AccessSectionInfo accessSectionInfo = createDefaultGlobalCapabilitiesAccessSectionInfo();

    accessInput.remove.put(AccessSection.GLOBAL_CAPABILITIES, accessSectionInfo);

    setApiUser(user);
    exception.expect(AuthException.class);
    gApi.projects().name(allProjects.get()).access(accessInput);
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
    assertThat(
            updatedProjectAccessInfo
                .local
                .get(AccessSection.GLOBAL_CAPABILITIES)
                .permissions
                .keySet())
        .containsAllIn(accessSectionInfo.permissions.keySet());

    // Remove
    accessInput.add.clear();
    accessInput.remove.put(AccessSection.GLOBAL_CAPABILITIES, accessSectionInfo);

    updatedProjectAccessInfo = gApi.projects().name(allProjects.get()).access(accessInput);
    assertThat(
            updatedProjectAccessInfo
                .local
                .get(AccessSection.GLOBAL_CAPABILITIES)
                .permissions
                .keySet())
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
            db, admin.getIdent(), allProjectsRepo, "Subject", ProjectConfig.PROJECT_CONFIG, config);
    push.to(RefNames.REFS_CONFIG).assertOkStatus();

    // Verify that unknownPermission is present
    config =
        gApi.projects()
            .name(allProjects.get())
            .branch(RefNames.REFS_CONFIG)
            .file(ProjectConfig.PROJECT_CONFIG)
            .asString();
    cfg.fromText(config);
    assertThat(cfg.getString(access, refsFor, unknownPermission)).isEqualTo(registeredUsers);

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
    assertThat(cfg.getString(access, refsFor, unknownPermission)).isEqualTo(registeredUsers);
  }

  @Test
  public void allUsersCanOnlyInheritFromAllProjects() throws Exception {
    ProjectAccessInput accessInput = newProjectAccessInput();
    accessInput.parent = project.get();
    exception.expect(BadRequestException.class);
    exception.expectMessage(allUsers.get() + " must inherit from " + allProjects.get());
    gApi.projects().name(allUsers.get()).access(accessInput);
  }

  @Test
  public void syncCreateGroupPermission() throws Exception {
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
    assertThat(local).isNotNull();
    assertThat(local).containsKey(RefNames.REFS_GROUPS + "*");
    Map<String, PermissionInfo> permissions = local.get(RefNames.REFS_GROUPS + "*").permissions;
    assertThat(permissions).hasSize(2);
    // READ is the default permission and should be preserved by the syncer
    assertThat(permissions.keySet()).containsExactly(Permission.READ, Permission.CREATE);
    Map<String, PermissionRuleInfo> rules = permissions.get(Permission.CREATE).rules;
    assertThat(rules.values()).containsExactly(pri);

    // Revoke the permission
    accessInput.add.clear();
    accessInput.remove.put(AccessSection.GLOBAL_CAPABILITIES, accessSection);
    gApi.projects().name(allProjects.get()).access(accessInput);

    // Assert that the permission was synced from All-Projects (global) to All-Users (ref)
    Map<String, AccessSectionInfo> local2 = gApi.projects().name("All-Users").access().local;
    assertThat(local2).isNotNull();
    assertThat(local2).containsKey(RefNames.REFS_GROUPS + "*");
    Map<String, PermissionInfo> permissions2 = local2.get(RefNames.REFS_GROUPS + "*").permissions;
    assertThat(permissions2).hasSize(1);
    // READ is the default permission and should be preserved by the syncer
    assertThat(permissions2.keySet()).containsExactly(Permission.READ);
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
