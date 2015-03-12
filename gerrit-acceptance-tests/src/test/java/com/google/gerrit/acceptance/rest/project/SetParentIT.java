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
import com.google.gerrit.server.project.SetParent;

import org.apache.http.HttpStatus;
import org.junit.Test;

public class SetParentIT extends AbstractDaemonTest {
  @Test
  public void setParent_Forbidden() throws Exception {
    String parent = "parent";
    createProject(parent, null, true);
    RestResponse r =
        userSession.put("/projects/" + project.get() + "/parent",
            newParentInput(parent));
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_FORBIDDEN);
    r.consume();
  }

  @Test
  public void setParent() throws Exception {
    String parent = "parent";
    createProject(parent, null, true);
    RestResponse r =
        adminSession.put("/projects/" + project.get() + "/parent",
            newParentInput(parent));
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    r.consume();

    r = adminSession.get("/projects/" + project.get() + "/parent");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    String newParent =
        newGson().fromJson(r.getReader(), String.class);
    assertThat(newParent).isEqualTo(parent);
    r.consume();
  }

  @Test
  public void setParentForAllProjects_Conflict() throws Exception {
    RestResponse r =
        adminSession.put("/projects/" + allProjects.get() + "/parent",
            newParentInput(project.get()));
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_CONFLICT);
    r.consume();
  }

  @Test
  public void setInvalidParent_Conflict() throws Exception {
    RestResponse r =
        adminSession.put("/projects/" + project.get() + "/parent",
            newParentInput(project.get()));
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_CONFLICT);
    r.consume();

    String child = "child";
    createProject(child, project, true);
    r = adminSession.put("/projects/" + project.get() + "/parent",
           newParentInput(child));
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_CONFLICT);
    r.consume();

    String grandchild = "grandchild";
    createProject(grandchild, new Project.NameKey(child), true);
    r = adminSession.put("/projects/" + project.get() + "/parent",
           newParentInput(grandchild));
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_CONFLICT);
    r.consume();
  }

  @Test
  public void setNonExistingParent_UnprocessibleEntity() throws Exception {
    RestResponse r =
        adminSession.put("/projects/" + project.get() + "/parent",
            newParentInput("non-existing"));
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_UNPROCESSABLE_ENTITY);
    r.consume();
  }

  SetParent.Input newParentInput(String project) {
    SetParent.Input in = new SetParent.Input();
    in.parent = project;
    return in;
  }
}
