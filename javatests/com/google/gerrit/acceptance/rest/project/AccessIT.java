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
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.extensions.api.access.ProjectAccessInfo;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import java.util.Map;
import org.junit.Test;

public class AccessIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;

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

  @Test
  public void listAccess_invalidProject() throws Exception {
    String invalidProject = "<%=FOO%>";
    RestResponse r =
        adminRestSession.get("/access/?project=" + IdString.fromDecoded(invalidProject));
    r.assertNotFound();
    assertThat(r.getEntityContent()).isEqualTo(invalidProject);
  }
}
