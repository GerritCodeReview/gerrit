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
import com.google.gerrit.extensions.api.access.AccessSectionInfo;
import com.google.gerrit.extensions.api.access.PermissionInfo;
import com.google.gerrit.extensions.api.access.PermissionRuleInfo;
import com.google.gerrit.extensions.api.access.ProjectAccessChangeInfo;
import com.google.gerrit.extensions.api.projects.ProjectApi;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.group.SystemGroupBackend;

import org.junit.Test;

import java.util.HashMap;

public class AccessIT extends AbstractDaemonTest {

  private final String PROJECT_NAME = "newProject";

  private final String REFS_ALL = "refs/*";
  private final String REFS_HEADS = "refs/heads/*";

  private final String PERMISSION_PUSH = "push";
  private final String PERMISSION_READ = "read";
  private final String PERMISSION_CODE_REVIEW = "label-Code-Review";

  private final String LABEL_CODE_REVIEW = "Code-Review";

  @Test
  public void testGetDefaultInheritance() throws Exception {
    String newProjectName = createProject(PROJECT_NAME).get();
    String inheritedName = gApi.projects()
        .name(newProjectName).access().inheritsFrom.name;
    assertThat(inheritedName).isEqualTo(AllProjectsNameProvider.DEFAULT);
  }

  @Test
  public void testAddAccessSection() throws Exception {
    String newProjectName = createProject(PROJECT_NAME).get();
    ProjectApi pApi = gApi.projects().name(newProjectName);

    ProjectAccessChangeInfo changeInfo = newProjectAccessChangeInfo();
    AccessSectionInfo accessSectionInfo = createDefaultAccessSectionInfo();

    changeInfo.addition.put(REFS_HEADS, accessSectionInfo);
    pApi.access(changeInfo);

    assertThat(pApi.access().local).isEqualTo(changeInfo.addition);
  }

  @Test
  public void testRemovePermission() throws Exception {
    String newProjectName = createProject(PROJECT_NAME).get();
    ProjectApi pApi = gApi.projects().name(newProjectName);

    // Add initial permission set
    ProjectAccessChangeInfo changeInfo = newProjectAccessChangeInfo();
    AccessSectionInfo accessSectionInfo = createDefaultAccessSectionInfo();

    changeInfo.addition.put(REFS_HEADS, accessSectionInfo);
    pApi.access(changeInfo);

    // Remove specific permission
    AccessSectionInfo accessSectionToRemove = newAccessSection();
    accessSectionToRemove.permissions
        .put(PERMISSION_CODE_REVIEW, newPermissionInfo());
    ProjectAccessChangeInfo removal = newProjectAccessChangeInfo();
    removal.deduction.put(REFS_HEADS, accessSectionToRemove);
    pApi.access(removal);

    // Remove locally
    changeInfo.addition.get(REFS_HEADS).permissions
        .remove(PERMISSION_CODE_REVIEW);

    // Check
    assertThat(pApi.access().local).isEqualTo(changeInfo.addition);
  }

  @Test
  public void testRemovePermissionRule() throws Exception {
    String newProjectName = createProject(PROJECT_NAME).get();
    ProjectApi pApi = gApi.projects().name(newProjectName);

    // Add initial permission set
    ProjectAccessChangeInfo changeInfo = newProjectAccessChangeInfo();
    AccessSectionInfo accessSectionInfo = createDefaultAccessSectionInfo();

    changeInfo.addition.put(REFS_HEADS, accessSectionInfo);
    pApi.access(changeInfo);

    // Remove specific permission rule
    AccessSectionInfo accessSectionToRemove = newAccessSection();
    PermissionInfo codeReview = newPermissionInfo();
    codeReview.label = LABEL_CODE_REVIEW;
    PermissionRuleInfo pri = new PermissionRuleInfo(
        PermissionRuleInfo.Action.DENY, false);
    codeReview.rules.put(
        SystemGroupBackend.REGISTERED_USERS.get(), pri);
    accessSectionToRemove.permissions
        .put(PERMISSION_CODE_REVIEW, codeReview);
    ProjectAccessChangeInfo removal = newProjectAccessChangeInfo();
    removal.deduction.put(REFS_HEADS, accessSectionToRemove);
    pApi.access(removal);

    // Remove locally
    changeInfo.addition.get(REFS_HEADS).permissions.get(PERMISSION_CODE_REVIEW)
        .rules.remove(SystemGroupBackend.REGISTERED_USERS.get());

    // Check
    assertThat(pApi.access().local).isEqualTo(changeInfo.addition);
  }

  @Test
  public void testRemovePermissionRuleAndGarbageCollect() throws Exception {
    String newProjectName = createProject(PROJECT_NAME).get();
    ProjectApi pApi = gApi.projects().name(newProjectName);

    // Add initial permission set
    ProjectAccessChangeInfo changeInfo = newProjectAccessChangeInfo();
    AccessSectionInfo accessSectionInfo = createDefaultAccessSectionInfo();

    changeInfo.addition.put(REFS_HEADS, accessSectionInfo);
    pApi.access(changeInfo);

    // Remove specific permission rules
    AccessSectionInfo accessSectionToRemove = newAccessSection();
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
        .put(PERMISSION_CODE_REVIEW, codeReview);
    ProjectAccessChangeInfo removal = newProjectAccessChangeInfo();
    removal.deduction.put(REFS_HEADS, accessSectionToRemove);
    pApi.access(removal);

    // Remove locally
    changeInfo.addition.get(REFS_HEADS)
        .permissions.remove(PERMISSION_CODE_REVIEW);

    // Check
    assertThat(pApi.access().local).isEqualTo(changeInfo.addition);
  }

  @Test
  public void testGetPermissionsWithDisallowedUser() throws Exception {
    String newProjectName = createProject(PROJECT_NAME).get();
    ProjectApi pApi = gApi.projects().name(newProjectName);

    // Add initial permission set
    ProjectAccessChangeInfo changeInfo = newProjectAccessChangeInfo();
    AccessSectionInfo accessSectionInfo = createAccessSectionInfoDenyAll();

    // Disallow READ
    changeInfo.addition.put(REFS_ALL, accessSectionInfo);
    pApi.access(changeInfo);

    userSession.get("/projects/" + newProjectName + "/access").assertNotFound();
  }

  @Test
  public void testSetPermissionsWithDisallowedUser() throws Exception {
    String newProjectName = createProject(PROJECT_NAME).get();
    ProjectApi pApi = gApi.projects().name(newProjectName);

    // Add initial permission set
    ProjectAccessChangeInfo changeInfo = newProjectAccessChangeInfo();
    AccessSectionInfo accessSectionInfo = createAccessSectionInfoDenyAll();

    // Disallow READ
    changeInfo.addition.put(REFS_ALL, accessSectionInfo);
    pApi.access(changeInfo);

    // Create a change to apply
    ProjectAccessChangeInfo changeInfoToApply = newProjectAccessChangeInfo();
    AccessSectionInfo accessSectionInfoToApply = createDefaultAccessSectionInfo();
    changeInfoToApply.addition.put(REFS_HEADS, accessSectionInfoToApply);

    userSession.post("/projects/" + newProjectName + "/access",
        changeInfoToApply).assertNotFound();
  }

  @Test
  public void testUpdateParentAsUser() throws Exception {
    // Create parent and child
    String newProjectName = createProject(PROJECT_NAME).get();
    gApi.projects().name(newProjectName);

    String newParentProjectName = createProject(PROJECT_NAME + "PA").get();
    gApi.projects().name(newProjectName);

    // Set new parent
    ProjectAccessChangeInfo changeInfo = newProjectAccessChangeInfo();
    changeInfo.parent = newParentProjectName;

    userSession.post("/projects/" + newProjectName + "/access",
        changeInfo).assertNotFound();
  }

  @Test
  public void testUpdateParentAsAdministrator() throws Exception {
    // Create parent and child
    String newProjectName = createProject(PROJECT_NAME).get();
    ProjectApi pApi = gApi.projects().name(newProjectName);

    String newParentProjectName = createProject(PROJECT_NAME + "PA").get();
    gApi.projects().name(newProjectName);

    // Set new parent
    ProjectAccessChangeInfo changeInfo = newProjectAccessChangeInfo();
    changeInfo.parent = newParentProjectName;

    adminSession.post("/projects/" + newProjectName + "/access",
        changeInfo);

    assertThat(pApi.access().inheritsFrom.name).isEqualTo(newParentProjectName);
  }

  private ProjectAccessChangeInfo newProjectAccessChangeInfo() {
    ProjectAccessChangeInfo p = new ProjectAccessChangeInfo();
    p.addition = new HashMap<>();
    p.deduction = new HashMap<>();
    return p;
  }

  private PermissionInfo newPermissionInfo() {
    PermissionInfo p = new PermissionInfo(null, null);
    p.rules = new HashMap<>();
    return p;
  }

  private AccessSectionInfo newAccessSection() {
    AccessSectionInfo a = new AccessSectionInfo();
    a.permissions = new HashMap<>();
    return a;
  }

  private AccessSectionInfo createDefaultAccessSectionInfo() {
    AccessSectionInfo accessSection = newAccessSection();

    PermissionInfo push = newPermissionInfo();
    PermissionRuleInfo pri = new PermissionRuleInfo(
        PermissionRuleInfo.Action.ALLOW, false);
    push.rules.put(
        SystemGroupBackend.REGISTERED_USERS.get(), pri);
    accessSection.permissions.put(PERMISSION_PUSH, push);

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
    accessSection.permissions.put(PERMISSION_CODE_REVIEW, codeReview);

    return accessSection;
  }

  private AccessSectionInfo createAccessSectionInfoDenyAll() {
    AccessSectionInfo accessSection = newAccessSection();

    PermissionInfo read = newPermissionInfo();
    PermissionRuleInfo pri = new PermissionRuleInfo(
        PermissionRuleInfo.Action.DENY, false);
    read.rules.put(
        SystemGroupBackend.ANONYMOUS_USERS.get(), pri);
    accessSection.permissions.put(PERMISSION_READ, read);

    return accessSection;
  }
}
