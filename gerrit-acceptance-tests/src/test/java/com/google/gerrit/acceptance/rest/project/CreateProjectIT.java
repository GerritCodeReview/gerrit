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
import static com.google.gerrit.acceptance.rest.project.ProjectAssert.assertProjectOwners;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.ProjectState;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

public class CreateProjectIT extends AbstractDaemonTest {
  @Test
  public void testCreateProjectApi() throws Exception {
    final String newProjectName = "newProject";
    ProjectInfo p = gApi.projects().name(newProjectName).create().get();
    assertThat(p.name).isEqualTo(newProjectName);
    ProjectState projectState = projectCache.get(new Project.NameKey(newProjectName));
    assertThat(projectState).isNotNull();
    assertProjectInfo(projectState.getProject(), p);
    assertHead(newProjectName, "refs/heads/master");
  }

  @Test
  public void testCreateProjectApiWithGitSuffix() throws Exception {
    final String newProjectName = "newProject";
    ProjectInfo p = gApi.projects().name(newProjectName + ".git").create().get();
    assertThat(p.name).isEqualTo(newProjectName);
    ProjectState projectState = projectCache.get(new Project.NameKey(newProjectName));
    assertThat(projectState).isNotNull();
    assertProjectInfo(projectState.getProject(), p);
    assertHead(newProjectName, "refs/heads/master");
  }

  @Test
  public void testCreateProject() throws Exception {
    final String newProjectName = "newProject";
    RestResponse r = adminSession.put("/projects/" + newProjectName);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_CREATED);
    ProjectInfo p = newGson().fromJson(r.getReader(), ProjectInfo.class);
    assertThat(p.name).isEqualTo(newProjectName);
    ProjectState projectState = projectCache.get(new Project.NameKey(newProjectName));
    assertThat(projectState).isNotNull();
    assertProjectInfo(projectState.getProject(), p);
    assertHead(newProjectName, "refs/heads/master");
  }

  @Test
  public void testCreateProjectWithGitSuffix() throws Exception {
    final String newProjectName = "newProject";
    RestResponse r = adminSession.put("/projects/" + newProjectName + ".git");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_CREATED);
    ProjectInfo p = newGson().fromJson(r.getReader(), ProjectInfo.class);
    assertThat(p.name).isEqualTo(newProjectName);
    ProjectState projectState = projectCache.get(new Project.NameKey(newProjectName));
    assertThat(projectState).isNotNull();
    assertProjectInfo(projectState.getProject(), p);
    assertHead(newProjectName, "refs/heads/master");
  }

  @Test
  public void testCreateProjectWithNameMismatch_BadRequest() throws Exception {
    ProjectInput in = new ProjectInput();
    in.name = "otherName";
    RestResponse r = adminSession.put("/projects/someName", in);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  public void testCreateProjectWithProperties() throws Exception {
    final String newProjectName = "newProject";
    ProjectInput in = new ProjectInput();
    in.description = "Test description";
    in.submitType = SubmitType.CHERRY_PICK;
    in.useContributorAgreements = InheritableBoolean.TRUE;
    in.useSignedOffBy = InheritableBoolean.TRUE;
    in.useContentMerge = InheritableBoolean.TRUE;
    in.requireChangeId = InheritableBoolean.TRUE;
    RestResponse r = adminSession.put("/projects/" + newProjectName, in);
    ProjectInfo p = newGson().fromJson(r.getReader(), ProjectInfo.class);
    assertThat(p.name).isEqualTo(newProjectName);
    Project project = projectCache.get(new Project.NameKey(newProjectName)).getProject();
    assertProjectInfo(project, p);
    assertThat(project.getDescription()).isEqualTo(in.description);
    assertThat(project.getSubmitType()).isEqualTo(in.submitType);
    assertThat(project.getUseContributorAgreements()).isEqualTo(in.useContributorAgreements);
    assertThat(project.getUseSignedOffBy()).isEqualTo(in.useSignedOffBy);
    assertThat(project.getUseContentMerge()).isEqualTo(in.useContentMerge);
    assertThat(project.getRequireChangeID()).isEqualTo(in.requireChangeId);
  }

  @Test
  public void testCreateChildProject() throws Exception {
    final String parentName = "parent";
    RestResponse r = adminSession.put("/projects/" + parentName);
    r.consume();
    final String childName = "child";
    ProjectInput in = new ProjectInput();
    in.parent = parentName;
    r = adminSession.put("/projects/" + childName, in);
    Project project = projectCache.get(new Project.NameKey(childName)).getProject();
    assertThat(project.getParentName()).isEqualTo(in.parent);
  }

  @Test
  public void testCreateChildProjectUnderNonExistingParent_UnprocessableEntity()
      throws Exception {
    ProjectInput in = new ProjectInput();
    in.parent = "non-existing-project";
    RestResponse r = adminSession.put("/projects/child", in);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  public void testCreateProjectWithOwner() throws Exception {
    final String newProjectName = "newProject";
    ProjectInput in = new ProjectInput();
    in.owners = Lists.newArrayListWithCapacity(3);
    in.owners.add("Anonymous Users"); // by name
    in.owners.add(SystemGroupBackend.REGISTERED_USERS.get()); // by UUID
    in.owners.add(Integer.toString(groupCache.get(
        new AccountGroup.NameKey("Administrators")).getId().get())); // by ID
    adminSession.put("/projects/" + newProjectName, in);
    ProjectState projectState = projectCache.get(new Project.NameKey(newProjectName));
    Set<AccountGroup.UUID> expectedOwnerIds = Sets.newHashSetWithExpectedSize(3);
    expectedOwnerIds.add(SystemGroupBackend.ANONYMOUS_USERS);
    expectedOwnerIds.add(SystemGroupBackend.REGISTERED_USERS);
    expectedOwnerIds.add(groupUuid("Administrators"));
    assertProjectOwners(expectedOwnerIds, projectState);
  }

  @Test
  public void testCreateProjectWithNonExistingOwner_UnprocessableEntity()
      throws Exception {
    ProjectInput in = new ProjectInput();
    in.owners = Collections.singletonList("non-existing-group");
    RestResponse r = adminSession.put("/projects/newProject", in);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  public void testCreatePermissionOnlyProject() throws Exception {
    final String newProjectName = "newProject";
    ProjectInput in = new ProjectInput();
    in.permissionsOnly = true;
    adminSession.put("/projects/" + newProjectName, in);
    assertHead(newProjectName, RefNames.REFS_CONFIG);
  }

  @Test
  public void testCreateProjectWithEmptyCommit() throws Exception {
    final String newProjectName = "newProject";
    ProjectInput in = new ProjectInput();
    in.createEmptyCommit = true;
    adminSession.put("/projects/" + newProjectName, in);
    assertEmptyCommit(newProjectName, "refs/heads/master");
  }

  @Test
  public void testCreateProjectWithBranches() throws Exception {
    final String newProjectName = "newProject";
    ProjectInput in = new ProjectInput();
    in.createEmptyCommit = true;
    in.branches = Lists.newArrayListWithCapacity(3);
    in.branches.add("refs/heads/test");
    in.branches.add("refs/heads/master");
    in.branches.add("release"); // without 'refs/heads' prefix
    adminSession.put("/projects/" + newProjectName, in);
    assertHead(newProjectName, "refs/heads/test");
    assertEmptyCommit(newProjectName, "refs/heads/test", "refs/heads/master",
        "refs/heads/release");
  }

  @Test
  public void testCreateProjectWithoutCapability_Forbidden() throws Exception {
    RestResponse r = userSession.put("/projects/newProject");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_FORBIDDEN);
  }

  @Test
  public void testCreateProjectWhenProjectAlreadyExists_Conflict()
      throws Exception {
    RestResponse r = adminSession.put("/projects/All-Projects");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_CONFLICT);
  }

  private AccountGroup.UUID groupUuid(String groupName) {
    return groupCache.get(new AccountGroup.NameKey(groupName)).getGroupUUID();
  }

  private void assertHead(String projectName, String expectedRef)
      throws RepositoryNotFoundException, IOException {
    Repository repo =
        repoManager.openRepository(new Project.NameKey(projectName));
    try {
      assertThat(repo.getRef(Constants.HEAD).getTarget().getName())
        .isEqualTo(expectedRef);
    } finally {
      repo.close();
    }
  }

  private void assertEmptyCommit(String projectName, String... refs)
      throws RepositoryNotFoundException, IOException {
    Repository repo =
        repoManager.openRepository(new Project.NameKey(projectName));
    RevWalk rw = new RevWalk(repo);
    TreeWalk tw = new TreeWalk(repo);
    try {
      for (String ref : refs) {
        RevCommit commit = rw.lookupCommit(repo.getRef(ref).getObjectId());
        rw.parseBody(commit);
        tw.addTree(commit.getTree());
        assertThat(tw.next()).isFalse();
        tw.reset();
      }
    } finally {
      rw.release();
      repo.close();
    }
  }
}
