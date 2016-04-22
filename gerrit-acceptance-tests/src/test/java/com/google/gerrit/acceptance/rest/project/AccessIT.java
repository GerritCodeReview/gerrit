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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.access.AccessSectionInfo;
import com.google.gerrit.extensions.api.access.PermissionInfo;
import com.google.gerrit.extensions.api.access.PermissionRuleInfo;
import com.google.gerrit.extensions.api.access.ProjectAccessInfo;
import com.google.gerrit.extensions.api.access.ProjectAccessInput;
import com.google.gerrit.extensions.api.projects.ProjectApi;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.group.SystemGroupBackend;
import org.eclipse.jgit.lib.Constants;

import org.junit.Test;

import java.util.HashMap;

public class AccessIT extends AbstractDaemonTest {

  private final String PROJECT_NAME = "newProject";

  private final String REFS_ALL = Constants.R_REFS + "*";
  private final String REFS_HEADS = "refs/heads/*";

  private final String LABEL_CODE_REVIEW = "Code-Review";

  @Test
  public void getDefaultInheritance() throws Exception {
    String newProjectName = createProject(PROJECT_NAME).get();
    String inheritedName = gApi.projects()
        .name(newProjectName).access().inheritsFrom.name;
    assertThat(inheritedName).isEqualTo(AllProjectsNameProvider.DEFAULT);
  }

  @Test
  public void addAccessSection() throws Exception {
    String newProjectName = createProject(PROJECT_NAME).get();
    ProjectApi pApi = gApi.projects().name(newProjectName);

    ProjectAccessInput accessInfo = newProjectAccessChangeInfo();
    AccessSectionInfo accessSectionInfo = createDefaultAccessSectionInfo();

    accessInfo.addition.put(REFS_HEADS, accessSectionInfo);
    pApi.access(accessInfo);

    assertThat(pApi.access().local).isEqualTo(accessInfo.addition);
  }

  @Test
  public void removePermission() throws Exception {
    String newProjectName = createProject(PROJECT_NAME).get();
    ProjectApi pApi = gApi.projects().name(newProjectName);

    // Add initial permission set
    ProjectAccessInput accessInfo = newProjectAccessChangeInfo();
    AccessSectionInfo accessSectionInfo = createDefaultAccessSectionInfo();

    accessInfo.addition.put(REFS_HEADS, accessSectionInfo);
    pApi.access(accessInfo);

    // Remove specific permission
    AccessSectionInfo accessSectionToRemove = newAccessSectionInfo();
    accessSectionToRemove.permissions
        .put(Permission.LABEL + LABEL_CODE_REVIEW, newPermissionInfo());
    ProjectAccessInput removal = newProjectAccessChangeInfo();
    removal.deduction.put(REFS_HEADS, accessSectionToRemove);
    pApi.access(removal);

    // Remove locally
    accessInfo.addition.get(REFS_HEADS).permissions
        .remove(Permission.LABEL +LABEL_CODE_REVIEW);

    // Check
    assertThat(pApi.access().local).isEqualTo(accessInfo.addition);
  }

  @Test
  public void removePermissionRule() throws Exception {
    String newProjectName = createProject(PROJECT_NAME).get();
    ProjectApi pApi = gApi.projects().name(newProjectName);

    // Add initial permission set
    ProjectAccessInput accessInfo = newProjectAccessChangeInfo();
    AccessSectionInfo accessSectionInfo = createDefaultAccessSectionInfo();

    accessInfo.addition.put(REFS_HEADS, accessSectionInfo);
    pApi.access(accessInfo);

    // Remove specific permission rule
    AccessSectionInfo accessSectionToRemove = newAccessSectionInfo();
    PermissionInfo codeReview = newPermissionInfo();
    codeReview.label = LABEL_CODE_REVIEW;
    PermissionRuleInfo pri = new PermissionRuleInfo(
        PermissionRuleInfo.Action.DENY, false);
    codeReview.rules.put(
        SystemGroupBackend.REGISTERED_USERS.get(), pri);
    accessSectionToRemove.permissions
        .put(Permission.LABEL +LABEL_CODE_REVIEW, codeReview);
    ProjectAccessInput removal = newProjectAccessChangeInfo();
    removal.deduction.put(REFS_HEADS, accessSectionToRemove);
    pApi.access(removal);

    // Remove locally
    accessInfo.addition.get(REFS_HEADS).permissions.get(Permission.LABEL +LABEL_CODE_REVIEW)
        .rules.remove(SystemGroupBackend.REGISTERED_USERS.get());

    // Check
    assertThat(pApi.access().local).isEqualTo(accessInfo.addition);
  }

  @Test
  public void removePermissionRulesAndCleanupEmptyEntries() throws Exception {
    String newProjectName = createProject(PROJECT_NAME).get();
    ProjectApi pApi = gApi.projects().name(newProjectName);

    // Add initial permission set
    ProjectAccessInput accessInfo = newProjectAccessChangeInfo();
    AccessSectionInfo accessSectionInfo = createDefaultAccessSectionInfo();

    accessInfo.addition.put(REFS_HEADS, accessSectionInfo);
    pApi.access(accessInfo);

    // Remove specific permission rules
    AccessSectionInfo accessSectionToRemove = newAccessSectionInfo();
    PermissionInfo codeReview = newPermissionInfo();
    codeReview.label = LABEL_CODE_REVIEW;
    PermissionRuleInfo pri = new PermissionRuleInfo(
        PermissionRuleInfo.Action.DENY, false);
    codeReview.rules.put(
        SystemGroupBackend.REGISTERED_USERS.get(), pri);
    pri = new PermissionRuleInfo(
        PermissionRuleInfo.Action.DENY, false);
    codeReview.rules.put(
        SystemGroupBackend.PROJECT_OWNERS.get(), pri);
    accessSectionToRemove.permissions
        .put(Permission.LABEL +LABEL_CODE_REVIEW, codeReview);
    ProjectAccessInput removal = newProjectAccessChangeInfo();
    removal.deduction.put(REFS_HEADS, accessSectionToRemove);
    pApi.access(removal);

    // Remove locally
    accessInfo.addition.get(REFS_HEADS)
        .permissions.remove(Permission.LABEL +LABEL_CODE_REVIEW);

    // Check
    assertThat(pApi.access().local).isEqualTo(accessInfo.addition);
  }

  @Test
  public void getPermissionsWithDisallowedUser() throws Exception {
    String newProjectName = createProject(PROJECT_NAME).get();
    ProjectApi pApi = gApi.projects().name(newProjectName);

    // Add initial permission set
    ProjectAccessInput accessInfo = newProjectAccessChangeInfo();
    AccessSectionInfo accessSectionInfo = createAccessSectionInfoDenyAll();

    // Disallow READ
    accessInfo.addition.put(REFS_ALL, accessSectionInfo);
    pApi.access(accessInfo);

    setApiUser(user);
    exception.expect(ResourceNotFoundException.class);
    gApi.projects().name(newProjectName).access();
  }

  @Test
  public void setPermissionsWithDisallowedUser() throws Exception {
    String newProjectName = createProject(PROJECT_NAME).get();
    ProjectApi pApi = gApi.projects().name(newProjectName);

    // Add initial permission set
    ProjectAccessInput accessInfo = newProjectAccessChangeInfo();
    AccessSectionInfo accessSectionInfo = createAccessSectionInfoDenyAll();

    // Disallow READ
    accessInfo.addition.put(REFS_ALL, accessSectionInfo);
    pApi.access(accessInfo);

    // Create a change to apply
    ProjectAccessInput accessInfoToApply = newProjectAccessChangeInfo();
    AccessSectionInfo accessSectionInfoToApply = createDefaultAccessSectionInfo();
    accessInfoToApply.addition.put(REFS_HEADS, accessSectionInfoToApply);

    setApiUser(user);
    exception.expect(ResourceNotFoundException.class);
    gApi.projects().name(newProjectName).access();
  }

  @Test
  public void updateParentAsUser() throws Exception {
    // Create parent and child
    String newProjectName = createProject(PROJECT_NAME).get();
    String newParentProjectName = createProject(PROJECT_NAME + "PA").get();

    // Set new parent
    ProjectAccessInput accessInfo = newProjectAccessChangeInfo();
    accessInfo.parent = newParentProjectName;

    setApiUser(user);
    exception.expect(AuthException.class);
    gApi.projects().name(newProjectName).access(accessInfo);
  }

  @Test
  public void updateParentAsAdministrator() throws Exception {
    // Create parent and child
    String newProjectName = createProject(PROJECT_NAME).get();
    ProjectApi pApi = gApi.projects().name(newProjectName);

    String newParentProjectName = createProject(PROJECT_NAME + "PA").get();
    gApi.projects().name(newProjectName);

    // Set new parent
    ProjectAccessInput accessInfo = newProjectAccessChangeInfo();
    accessInfo.parent = newParentProjectName;

    setApiUser(admin);
    gApi.projects().name(newProjectName).access(accessInfo);

    assertThat(pApi.access().inheritsFrom.name).isEqualTo(newParentProjectName);
  }

  @Test
  public void addGlobalCapabilityAsUser() throws Exception {
    ProjectAccessInput accessInfo = newProjectAccessChangeInfo();
    AccessSectionInfo accessSectionInfo = createDefaultAccessSectionInfo();

    accessInfo.addition.put(AccessSection.GLOBAL_CAPABILITIES,
        accessSectionInfo);

    setApiUser(user);
    exception.expect(BadRequestException.class);
    gApi.projects().name(allProjects.get()).access(accessInfo);
  }

  @Test
  public void addGlobalCapabilityAsAdmin() throws Exception {
    ProjectAccessInput accessInfo = newProjectAccessChangeInfo();
    AccessSectionInfo accessSectionInfo = createDefaultAccessSectionInfo();

    accessInfo.addition.put(AccessSection.GLOBAL_CAPABILITIES,
        accessSectionInfo);

    setApiUser(admin);
    ProjectAccessInfo updatedAccessSectionInfo =
        gApi.projects().name(allProjects.get()).access(accessInfo);
    assertThat(updatedAccessSectionInfo.local.get(
        AccessSection.GLOBAL_CAPABILITIES).permissions.keySet())
        .containsAllIn(accessSectionInfo.permissions.keySet());
  }

  @Test
  public void addGlobalCapabilityForNonRootProject() throws Exception {
    String newProjectName = createProject(PROJECT_NAME).get();
    ProjectApi pApi = gApi.projects().name(newProjectName);

    ProjectAccessInput accessInfo = newProjectAccessChangeInfo();
    AccessSectionInfo accessSectionInfo = createDefaultAccessSectionInfo();

    accessInfo.addition.put(AccessSection.GLOBAL_CAPABILITIES,
        accessSectionInfo);

    setApiUser(admin);
    exception.expect(BadRequestException.class);
    pApi.access(accessInfo);
  }

  @Test
  public void removeGlobalCapabilityAsUser() throws Exception {
    ProjectAccessInput accessInfo = newProjectAccessChangeInfo();
    AccessSectionInfo accessSectionInfo = createDefaultAccessSectionInfo();

    accessInfo.deduction.put(AccessSection.GLOBAL_CAPABILITIES,
        accessSectionInfo);

    setApiUser(user);
    exception.expect(BadRequestException.class);
    gApi.projects().name(allProjects.get()).access(accessInfo);
  }

  @Test
  public void removeGlobalCapabilityAsAdmin() throws Exception {
    AccountGroup adminGroup =
        groupCache.get(new AccountGroup.NameKey("Administrators"));

    ProjectAccessInput accessInfo = newProjectAccessChangeInfo();
    AccessSectionInfo accessSectionInfo = newAccessSectionInfo();

    PermissionInfo read = newPermissionInfo();
    read.rules.put(
        adminGroup.getGroupUUID().get(), null);
    accessSectionInfo.permissions.put(Permission.PUSH_SIGNED_TAG,
        read);

    // Add and validate first as removing existing privileges such as
    // administrateServer would break upcoming tests
    accessInfo.addition.put(AccessSection.GLOBAL_CAPABILITIES,
        accessSectionInfo);

    setApiUser(admin);
    ProjectAccessInfo updatedAccessSectionInfo =
        gApi.projects().name(allProjects.get()).access(accessInfo);
    assertThat(updatedAccessSectionInfo.local.get(
        AccessSection.GLOBAL_CAPABILITIES).permissions.keySet())
        .containsAllIn(accessSectionInfo.permissions.keySet());

    // Remove
    accessInfo.addition.clear();
    accessInfo.deduction.put(AccessSection.GLOBAL_CAPABILITIES,
        accessSectionInfo);

    updatedAccessSectionInfo =
        gApi.projects().name(allProjects.get()).access(accessInfo);
    assertThat(updatedAccessSectionInfo.local.get(
        AccessSection.GLOBAL_CAPABILITIES).permissions.keySet())
        .containsNoneIn(accessSectionInfo.permissions.keySet());
  }

  private ProjectAccessInput newProjectAccessChangeInfo() {
    ProjectAccessInput p = new ProjectAccessInput();
    p.addition = new HashMap<>();
    p.deduction = new HashMap<>();
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
    PermissionRuleInfo pri = new PermissionRuleInfo(
        PermissionRuleInfo.Action.ALLOW, false);
    push.rules.put(
        SystemGroupBackend.REGISTERED_USERS.get(), pri);
    accessSection.permissions.put(Permission.PUSH, push);

    PermissionInfo codeReview = newPermissionInfo();
    codeReview.label = LABEL_CODE_REVIEW;
    pri = new PermissionRuleInfo(
        PermissionRuleInfo.Action.DENY, false);
    codeReview.rules.put(
        SystemGroupBackend.REGISTERED_USERS.get(), pri);

    pri = new PermissionRuleInfo(
        PermissionRuleInfo.Action.ALLOW, false);
    pri.max = 1;
    pri.min = -1;
    codeReview.rules.put(
        SystemGroupBackend.PROJECT_OWNERS.get(), pri);
    accessSection.permissions.put(Permission.LABEL + LABEL_CODE_REVIEW, codeReview);

    return accessSection;
  }

  private AccessSectionInfo createAccessSectionInfoDenyAll() {
    AccessSectionInfo accessSection = newAccessSectionInfo();

    PermissionInfo read = newPermissionInfo();
    PermissionRuleInfo pri = new PermissionRuleInfo(
        PermissionRuleInfo.Action.DENY, false);
    read.rules.put(
        SystemGroupBackend.ANONYMOUS_USERS.get(), pri);
    accessSection.permissions.put(Permission.READ, read);

    return accessSection;
  }
}
