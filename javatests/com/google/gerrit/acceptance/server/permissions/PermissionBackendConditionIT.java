// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.permissions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.extensions.conditions.BooleanCondition;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import org.junit.Test;

public class PermissionBackendConditionIT extends AbstractDaemonTest {

  @Inject PermissionBackend pb;
  @Inject ProjectOperations projectOperations;

  @Test
  public void globalPermissions_sameUserAndPermissionEquals() throws Exception {
    BooleanCondition cond1 = pb.user(user()).testCond(GlobalPermission.CREATE_GROUP);
    BooleanCondition cond2 = pb.user(user()).testCond(GlobalPermission.CREATE_GROUP);
    assertEquals(cond1, cond2);
    assertEquals(cond1.hashCode(), cond2.hashCode());
  }

  @Test
  public void globalPermissions_differentPermissionDoesNotEquals() throws Exception {
    BooleanCondition cond1 = pb.user(user()).testCond(GlobalPermission.CREATE_GROUP);
    BooleanCondition cond2 = pb.user(user()).testCond(GlobalPermission.ACCESS_DATABASE);
    assertNotEquals(cond1, cond2);
    assertNotEquals(cond1.hashCode(), cond2.hashCode());
  }

  @Test
  public void globalPermissions_differentUserDoesNotEqual() throws Exception {
    BooleanCondition cond1 = pb.user(user()).testCond(GlobalPermission.CREATE_GROUP);
    BooleanCondition cond2 = pb.user(admin()).testCond(GlobalPermission.CREATE_GROUP);
    assertNotEquals(cond1, cond2);
    assertNotEquals(cond1.hashCode(), cond2.hashCode());
  }

  @Test
  public void changePermissions_sameResourceAndUserEquals() throws Exception {
    ChangeData change = createChange().getChange();
    BooleanCondition cond1 = pb.user(user()).change(change).testCond(ChangePermission.READ);
    BooleanCondition cond2 = pb.user(user()).change(change).testCond(ChangePermission.READ);

    assertEquals(cond1, cond2);
    assertEquals(cond1.hashCode(), cond2.hashCode());
  }

  @Test
  public void changePermissions_sameResourceDifferentUserDoesNotEqual() throws Exception {
    ChangeData change = createChange().getChange();
    BooleanCondition cond1 = pb.user(user()).change(change).testCond(ChangePermission.READ);
    BooleanCondition cond2 = pb.user(admin()).change(change).testCond(ChangePermission.READ);

    assertNotEquals(cond1, cond2);
    assertNotEquals(cond1.hashCode(), cond2.hashCode());
  }

  @Test
  public void changePermissions_differentResourceSameUserDoesNotEqual() throws Exception {
    ChangeData change1 = createChange().getChange();
    ChangeData change2 = createChange().getChange();
    BooleanCondition cond1 = pb.user(user()).change(change1).testCond(ChangePermission.READ);
    BooleanCondition cond2 = pb.user(user()).change(change2).testCond(ChangePermission.READ);

    assertNotEquals(cond1, cond2);
    assertNotEquals(cond1.hashCode(), cond2.hashCode());
  }

  @Test
  public void projectPermissions_sameResourceAndUserEquals() throws Exception {
    BooleanCondition cond1 = pb.user(user()).project(project).testCond(ProjectPermission.READ);
    BooleanCondition cond2 = pb.user(user()).project(project).testCond(ProjectPermission.READ);

    assertEquals(cond1, cond2);
    assertEquals(cond1.hashCode(), cond2.hashCode());
  }

  @Test
  public void projectPermissions_sameResourceDifferentUserDoesNotEqual() throws Exception {
    BooleanCondition cond1 = pb.user(user()).project(project).testCond(ProjectPermission.READ);
    BooleanCondition cond2 = pb.user(admin()).project(project).testCond(ProjectPermission.READ);

    assertNotEquals(cond1, cond2);
    assertNotEquals(cond1.hashCode(), cond2.hashCode());
  }

  @Test
  public void projectPermissions_differentResourceSameUserDoesNotEqual() throws Exception {
    Project.NameKey project2 = projectOperations.newProject().create();
    BooleanCondition cond1 = pb.user(user()).project(project).testCond(ProjectPermission.READ);
    BooleanCondition cond2 = pb.user(user()).project(project2).testCond(ProjectPermission.READ);

    assertNotEquals(cond1, cond2);
    assertNotEquals(cond1.hashCode(), cond2.hashCode());
  }

  @Test
  public void refPermissions_sameResourceAndUserEquals() throws Exception {
    Branch.NameKey branch = Branch.nameKey(project, "branch");
    BooleanCondition cond1 = pb.user(user()).ref(branch).testCond(RefPermission.READ);
    BooleanCondition cond2 = pb.user(user()).ref(branch).testCond(RefPermission.READ);

    assertEquals(cond1, cond2);
    assertEquals(cond1.hashCode(), cond2.hashCode());
  }

  @Test
  public void refPermissions_sameResourceAndDifferentUserDoesNotEqual() throws Exception {
    Branch.NameKey branch = Branch.nameKey(project, "branch");
    BooleanCondition cond1 = pb.user(user()).ref(branch).testCond(RefPermission.READ);
    BooleanCondition cond2 = pb.user(admin()).ref(branch).testCond(RefPermission.READ);

    assertNotEquals(cond1, cond2);
    assertNotEquals(cond1.hashCode(), cond2.hashCode());
  }

  @Test
  public void refPermissions_differentResourceAndSameUserDoesNotEqual() throws Exception {
    Branch.NameKey branch1 = Branch.nameKey(project, "branch");
    Branch.NameKey branch2 = Branch.nameKey(project, "branch2");
    BooleanCondition cond1 = pb.user(user()).ref(branch1).testCond(RefPermission.READ);
    BooleanCondition cond2 = pb.user(user()).ref(branch2).testCond(RefPermission.READ);

    assertNotEquals(cond1, cond2);
    assertNotEquals(cond1.hashCode(), cond2.hashCode());
  }

  @Test
  public void refPermissions_differentResourceAndSameUserDoesNotEqual2() throws Exception {
    Branch.NameKey branch1 = Branch.nameKey(project, "branch");
    Branch.NameKey branch2 = Branch.nameKey(projectOperations.newProject().create(), "branch");
    BooleanCondition cond1 = pb.user(user()).ref(branch1).testCond(RefPermission.READ);
    BooleanCondition cond2 = pb.user(user()).ref(branch2).testCond(RefPermission.READ);

    assertNotEquals(cond1, cond2);
    assertNotEquals(cond1.hashCode(), cond2.hashCode());
  }

  private CurrentUser user() {
    return identifiedUserFactory.create(user.id());
  }

  private CurrentUser admin() {
    return identifiedUserFactory.create(admin.id());
  }
}
