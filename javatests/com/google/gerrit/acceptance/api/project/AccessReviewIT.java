// Copyright (C) 2023 The Android Open Source Project
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
import static com.google.gerrit.truth.ConfigSubject.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.access.AccessSectionInfo;
import com.google.gerrit.extensions.api.access.PermissionInfo;
import com.google.gerrit.extensions.api.access.PermissionRuleInfo;
import com.google.gerrit.extensions.api.access.ProjectAccessInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class AccessReviewIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;

  private Project.NameKey defaultMessageProject;
  private Project.NameKey customMessageProject;

  @Before
  public void setUp() throws Exception {
    defaultMessageProject = projectOperations.newProject().create();
    customMessageProject = projectOperations.newProject().create();
  }

  @Test
  public void createPermissionsChangeWithDefaultMessage() throws Exception {
    ProjectAccessInput in = new ProjectAccessInput();
    in.add = new HashMap<>();

    AccessSectionInfo a = new AccessSectionInfo();
    PermissionInfo p = new PermissionInfo(null, null);
    p.rules =
        ImmutableMap.of(
            SystemGroupBackend.REGISTERED_USERS.get(),
            new PermissionRuleInfo(PermissionRuleInfo.Action.ALLOW, false));
    a.permissions = ImmutableMap.of("read", p);
    in.add = ImmutableMap.of("refs/heads/*", a);

    RestResponse rep =
        adminRestSession.put("/projects/" + defaultMessageProject.get() + "/access:review", in);
    rep.assertCreated();

    List<ChangeInfo> result =
        gApi.changes()
            .query("project:" + defaultMessageProject.get() + " AND ref:refs/meta/config")
            .get();
    assertThat(Iterables.getOnlyElement(result).subject).isEqualTo("Review access change");
  }

  @Test
  public void createPermissionsChangeWithCustomMessage() throws Exception {
    ProjectAccessInput in = new ProjectAccessInput();
    String customMessage = "UNIT-42: Allow registered users to read 'main' branch";
    in.add = new HashMap<>();

    AccessSectionInfo a = new AccessSectionInfo();
    PermissionInfo p = new PermissionInfo(null, null);
    p.rules =
        ImmutableMap.of(
            SystemGroupBackend.REGISTERED_USERS.get(),
            new PermissionRuleInfo(PermissionRuleInfo.Action.ALLOW, false));
    a.permissions = ImmutableMap.of("read", p);
    in.add = ImmutableMap.of("refs/heads/main", a);
    in.message = customMessage;

    RestResponse rep =
        adminRestSession.put("/projects/" + customMessageProject.get() + "/access:review", in);
    rep.assertCreated();

    List<ChangeInfo> result =
        gApi.changes()
            .query("project:" + customMessageProject.get() + " AND ref:refs/meta/config")
            .get();

    assertThat(Iterables.getOnlyElement(result).subject).isEqualTo(customMessage);
  }
}
