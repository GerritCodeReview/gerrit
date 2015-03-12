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
import static com.google.gerrit.acceptance.rest.project.ProjectAssert.assertProjectInfo;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.reviewdb.client.Project;

import org.apache.http.HttpStatus;
import org.junit.Test;

import java.io.IOException;

public class GetChildProjectIT extends AbstractDaemonTest {

  @Test
  public void getNonExistingChildProject_NotFound() throws Exception {
    assertThat(
        GET("/projects/" + allProjects.get() + "/children/non-existing")
            .getStatusCode()).isEqualTo(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void getNonChildProject_NotFound() throws Exception {
    SshSession sshSession = new SshSession(server, admin);
    Project.NameKey p1 = new Project.NameKey("p1");
    createProject(p1.get());
    Project.NameKey p2 = new Project.NameKey("p2");
    createProject(p2.get());
    sshSession.close();
    assertThat(
        GET("/projects/" + p1.get() + "/children/" + p2.get()).getStatusCode())
        .isEqualTo(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void getChildProject() throws Exception {
    SshSession sshSession = new SshSession(server, admin);
    Project.NameKey child = new Project.NameKey("p1");
    createProject(child.get());
    sshSession.close();
    RestResponse r =
        GET("/projects/" + allProjects.get() + "/children/" + child.get());
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    ProjectInfo childInfo =
        newGson().fromJson(r.getReader(), ProjectInfo.class);
    assertProjectInfo(projectCache.get(child).getProject(), childInfo);
  }

  @Test
  public void getGrandChildProject_NotFound() throws Exception {
    SshSession sshSession = new SshSession(server, admin);
    Project.NameKey child = new Project.NameKey("p1");
    createProject(child.get());
    Project.NameKey grandChild = new Project.NameKey("p1.1");
    createProject(grandChild.get(), child);
    sshSession.close();
    assertThat(
        GET("/projects/" + allProjects.get() + "/children/" + grandChild.get())
            .getStatusCode()).isEqualTo(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void getGrandChildProjectWithRecursiveFlag() throws Exception {
    SshSession sshSession = new SshSession(server, admin);
    Project.NameKey child = new Project.NameKey("p1");
    createProject(child.get());
    Project.NameKey grandChild = new Project.NameKey("p1.1");
    createProject(grandChild.get(), child);
    sshSession.close();
    RestResponse r =
        GET("/projects/" + allProjects.get() + "/children/" + grandChild.get()
            + "?recursive");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    ProjectInfo grandChildInfo =
        newGson().fromJson(r.getReader(), ProjectInfo.class);
    assertProjectInfo(projectCache.get(grandChild).getProject(), grandChildInfo);
  }

  private RestResponse GET(String endpoint) throws IOException {
    return adminSession.get(endpoint);
  }
}
