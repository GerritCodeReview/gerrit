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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.api.access.ProjectAccessInfo;
import com.google.gson.reflect.TypeToken;
import java.util.Map;
import org.junit.Test;

public class AccessIT extends AbstractDaemonTest {

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
  public void listAccess() throws Exception {
    RestResponse r = adminRestSession.get("/access/?project=" + project.get());
    r.assertOK();
    Map<String, ProjectAccessInfo> infoByProject =
        newGson()
            .fromJson(r.getReader(), new TypeToken<Map<String, ProjectAccessInfo>>() {}.getType());
    assertThat(infoByProject.keySet()).containsExactly(project.get());
  }
}
