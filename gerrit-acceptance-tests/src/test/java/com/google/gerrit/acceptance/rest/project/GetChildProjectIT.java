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
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.Project;

import org.junit.Test;

@NoHttpd
public class GetChildProjectIT extends AbstractDaemonTest {

  @Test
  public void getNonExistingChildProject_NotFound() throws Exception {
    assertChildNotFound(allProjects, "non-existing");
  }

  @Test
  public void getNonChildProject_NotFound() throws Exception {
    SshSession sshSession = new SshSession(server, admin);
    Project.NameKey p1 = new Project.NameKey("p1");
    createProject(p1.get());
    Project.NameKey p2 = new Project.NameKey("p2");
    createProject(p2.get());
    sshSession.close();

    assertChildNotFound(p1, p2.get());
  }

  @Test
  public void getChildProject() throws Exception {
    SshSession sshSession = new SshSession(server, admin);
    Project.NameKey child = new Project.NameKey("p1");
    createProject(child.get());
    sshSession.close();

    ProjectInfo childInfo = gApi.projects().name(allProjects.get())
        .child(child.get()).get();
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

    assertChildNotFound(allProjects, grandChild.get());
  }

  @Test
  public void getGrandChildProjectWithRecursiveFlag() throws Exception {
    SshSession sshSession = new SshSession(server, admin);
    Project.NameKey child = new Project.NameKey("p1");
    createProject(child.get());
    Project.NameKey grandChild = new Project.NameKey("p1.1");
    createProject(grandChild.get(), child);
    sshSession.close();

    ProjectInfo grandChildInfo = gApi.projects().name(allProjects.get())
        .child(grandChild.get()).get(true);
    assertProjectInfo(
        projectCache.get(grandChild).getProject(), grandChildInfo);
  }

  private void assertChildNotFound(Project.NameKey parent, String child)
      throws Exception {
    try {
      gApi.projects().name(parent.get()).child(child);
    } catch (ResourceNotFoundException e) {
      e.printStackTrace();
      assertThat(e.getMessage()).contains(child);
    }
  }
}
