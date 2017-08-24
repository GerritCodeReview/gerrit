// Copyright (C) 2013 The Android Open Source Project
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
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.project.SetParent;
import org.junit.Test;

public class SetParentIT extends AbstractDaemonTest {
  @Test
  public void setParent_Forbidden() throws Exception {
    String parent = createProject("parent", null, true).get();
    RestResponse r =
        userRestSession.put("/projects/" + project.get() + "/parent", newParentInput(parent));
    r.assertForbidden();
    r.consume();
  }

  @Test
  public void setParent() throws Exception {
    String parent = createProject("parent", null, true).get();
    RestResponse r =
        adminRestSession.put("/projects/" + project.get() + "/parent", newParentInput(parent));
    r.assertOK();
    r.consume();

    r = adminRestSession.get("/projects/" + project.get() + "/parent");
    r.assertOK();
    String newParent = newGson().fromJson(r.getReader(), String.class);
    assertThat(newParent).isEqualTo(parent);
    r.consume();

    // When the parent name is not explicitly set, it should be
    // set to "All-Projects".
    r = adminRestSession.put("/projects/" + project.get() + "/parent", newParentInput(null));
    r.assertOK();
    r.consume();

    r = adminRestSession.get("/projects/" + project.get() + "/parent");
    r.assertOK();
    newParent = newGson().fromJson(r.getReader(), String.class);
    assertThat(newParent).isEqualTo(AllProjectsNameProvider.DEFAULT);
    r.consume();
  }

  @Test
  public void setParentForAllProjects_Conflict() throws Exception {
    RestResponse r =
        adminRestSession.put(
            "/projects/" + allProjects.get() + "/parent", newParentInput(project.get()));
    r.assertConflict();
    r.consume();
  }

  @Test
  public void setInvalidParent_Conflict() throws Exception {
    RestResponse r =
        adminRestSession.put(
            "/projects/" + project.get() + "/parent", newParentInput(project.get()));
    r.assertConflict();
    r.consume();

    Project.NameKey child = createProject("child", project, true);
    r = adminRestSession.put("/projects/" + project.get() + "/parent", newParentInput(child.get()));
    r.assertConflict();
    r.consume();

    String grandchild = createProject("grandchild", child, true).get();
    r = adminRestSession.put("/projects/" + project.get() + "/parent", newParentInput(grandchild));
    r.assertConflict();
    r.consume();
  }

  @Test
  public void setNonExistingParent_UnprocessibleEntity() throws Exception {
    RestResponse r =
        adminRestSession.put(
            "/projects/" + project.get() + "/parent", newParentInput("non-existing"));
    r.assertUnprocessableEntity();
    r.consume();
  }

  SetParent.Input newParentInput(String project) {
    SetParent.Input in = new SetParent.Input();
    in.parent = project;
    return in;
  }
}
