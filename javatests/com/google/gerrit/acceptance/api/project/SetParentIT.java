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

package com.google.gerrit.acceptance.api.project;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.config.AllProjectsNameProvider;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Project;
import org.junit.Test;

@NoHttpd
public class SetParentIT extends AbstractDaemonTest {

  @Test
  public void setParentNotAllowed() throws Exception {
    String parent = createProject("parent", null, true).get();
    setApiUser(user);
    exception.expect(AuthException.class);
    gApi.projects().name(project.get()).parent(parent);
  }

  @Test
  public void setParent() throws Exception {
    String parent = createProject("parent", null, true).get();

    gApi.projects().name(project.get()).parent(parent);
    assertThat(gApi.projects().name(project.get()).parent()).isEqualTo(parent);

    // When the parent name is not explicitly set, it should be
    // set to "All-Projects".
    gApi.projects().name(project.get()).parent(null);
    assertThat(gApi.projects().name(project.get()).parent())
        .isEqualTo(AllProjectsNameProvider.DEFAULT);
  }

  @Test
  public void setParentForAllProjectsNotAllowed() throws Exception {
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("cannot set parent of " + AllProjectsNameProvider.DEFAULT);
    gApi.projects().name(allProjects.get()).parent(project.get());
  }

  @Test
  public void setParentToSelfNotAllowed() throws Exception {
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("cannot set parent to self");
    gApi.projects().name(project.get()).parent(project.get());
  }

  @Test
  public void setParentToOwnChildNotAllowed() throws Exception {
    String child = createProject("child", project, true).get();
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("cycle exists between");
    gApi.projects().name(project.get()).parent(child);
  }

  @Test
  public void setParentToGrandchildNotAllowed() throws Exception {
    Project.NameKey child = createProject("child", project, true);
    String grandchild = createProject("grandchild", child, true).get();
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("cycle exists between");
    gApi.projects().name(project.get()).parent(grandchild);
  }

  @Test
  public void setParentToNonexistentProject() throws Exception {
    exception.expect(UnprocessableEntityException.class);
    exception.expectMessage("not found");
    gApi.projects().name(project.get()).parent("non-existing");
  }

  @Test
  public void setParentToAllUsersNotAllowed() throws Exception {
    exception.expect(ResourceConflictException.class);
    exception.expectMessage(String.format("Cannot inherit from '%s' project", allUsers.get()));
    gApi.projects().name(project.get()).parent(allUsers.get());
  }

  @Test
  public void setParentForAllUsersMustBeAllProjects() throws Exception {
    gApi.projects().name(allUsers.get()).parent(allProjects.get());

    String parent = createProject("parent", null, true).get();

    exception.expect(BadRequestException.class);
    exception.expectMessage("All-Users must inherit from All-Projects");
    gApi.projects().name(allUsers.get()).parent(parent);
  }
}
