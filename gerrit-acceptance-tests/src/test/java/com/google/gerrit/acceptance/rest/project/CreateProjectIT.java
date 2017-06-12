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
import com.google.common.net.HttpHeaders;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.ProjectState;
import java.util.Collections;
import java.util.Set;
import org.apache.http.message.BasicHeader;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Test;

public class CreateProjectIT extends AbstractDaemonTest {
  @Test
  public void testCreateProjectHttp() throws Exception {
    String newProjectName = name("newProject");
    RestResponse r = adminRestSession.put("/projects/" + newProjectName);
    r.assertCreated();
    ProjectInfo p = newGson().fromJson(r.getReader(), ProjectInfo.class);
    assertThat(p.name).isEqualTo(newProjectName);
    ProjectState projectState = projectCache.get(new Project.NameKey(newProjectName));
    assertThat(projectState).isNotNull();
    assertProjectInfo(projectState.getProject(), p);
    assertHead(newProjectName, "refs/heads/master");
  }

  @Test
  public void testCreateProjectHttpWhenProjectAlreadyExists_Conflict() throws Exception {
    adminRestSession.put("/projects/" + allProjects.get()).assertConflict();
  }

  @Test
  public void testCreateProjectHttpWhenProjectAlreadyExists_PreconditionFailed() throws Exception {
    adminRestSession
        .putWithHeader(
            "/projects/" + allProjects.get(), new BasicHeader(HttpHeaders.IF_NONE_MATCH, "*"))
        .assertPreconditionFailed();
  }

  @Test
  @UseLocalDisk
  public void testCreateProjectHttpWithUnreasonableName_BadRequest() throws Exception {
    adminRestSession.put("/projects/" + Url.encode(name("invalid/../name"))).assertBadRequest();
  }

  @Test
  public void testCreateProjectHttpWithNameMismatch_BadRequest() throws Exception {
    ProjectInput in = new ProjectInput();
    in.name = name("otherName");
    adminRestSession.put("/projects/" + name("someName"), in).assertBadRequest();
  }

  @Test
  public void testCreateProjectHttpWithInvalidRefName_BadRequest() throws Exception {
    ProjectInput in = new ProjectInput();
    in.branches = Collections.singletonList(name("invalid ref name"));
    adminRestSession.put("/projects/" + name("newProject"), in).assertBadRequest();
  }

  @Test
  public void testCreateProject() throws Exception {
    String newProjectName = name("newProject");
    ProjectInfo p = gApi.projects().create(newProjectName).get();
    assertThat(p.name).isEqualTo(newProjectName);
    ProjectState projectState = projectCache.get(new Project.NameKey(newProjectName));
    assertThat(projectState).isNotNull();
    assertProjectInfo(projectState.getProject(), p);
    assertHead(newProjectName, "refs/heads/master");
  }

  @Test
  public void testCreateProjectWithGitSuffix() throws Exception {
    String newProjectName = name("newProject");
    ProjectInfo p = gApi.projects().create(newProjectName + ".git").get();
    assertThat(p.name).isEqualTo(newProjectName);
    ProjectState projectState = projectCache.get(new Project.NameKey(newProjectName));
    assertThat(projectState).isNotNull();
    assertProjectInfo(projectState.getProject(), p);
    assertHead(newProjectName, "refs/heads/master");
  }

  @Test
  public void testCreateProjectWithProperties() throws Exception {
    String newProjectName = name("newProject");
    ProjectInput in = new ProjectInput();
    in.name = newProjectName;
    in.description = "Test description";
    in.submitType = SubmitType.CHERRY_PICK;
    in.useContributorAgreements = InheritableBoolean.TRUE;
    in.useSignedOffBy = InheritableBoolean.TRUE;
    in.useContentMerge = InheritableBoolean.TRUE;
    in.requireChangeId = InheritableBoolean.TRUE;
    ProjectInfo p = gApi.projects().create(in).get();
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
    String parentName = name("parent");
    ProjectInput in = new ProjectInput();
    in.name = parentName;
    gApi.projects().create(in);

    String childName = name("child");
    in = new ProjectInput();
    in.name = childName;
    in.parent = parentName;
    gApi.projects().create(in);
    Project project = projectCache.get(new Project.NameKey(childName)).getProject();
    assertThat(project.getParentName()).isEqualTo(in.parent);
  }

  @Test
  public void testCreateChildProjectUnderNonExistingParent_UnprocessableEntity() throws Exception {
    ProjectInput in = new ProjectInput();
    in.name = name("newProjectName");
    in.parent = "non-existing-project";
    assertCreateFails(in, UnprocessableEntityException.class);
  }

  @Test
  public void testCreateProjectWithOwner() throws Exception {
    String newProjectName = name("newProject");
    ProjectInput in = new ProjectInput();
    in.name = newProjectName;
    in.owners = Lists.newArrayListWithCapacity(3);
    in.owners.add("Anonymous Users"); // by name
    in.owners.add(SystemGroupBackend.REGISTERED_USERS.get()); // by UUID
    in.owners.add(
        Integer.toString(
            groupCache.get(new AccountGroup.NameKey("Administrators")).getId().get())); // by ID
    gApi.projects().create(in);
    ProjectState projectState = projectCache.get(new Project.NameKey(newProjectName));
    Set<AccountGroup.UUID> expectedOwnerIds = Sets.newHashSetWithExpectedSize(3);
    expectedOwnerIds.add(SystemGroupBackend.ANONYMOUS_USERS);
    expectedOwnerIds.add(SystemGroupBackend.REGISTERED_USERS);
    expectedOwnerIds.add(groupUuid("Administrators"));
    assertProjectOwners(expectedOwnerIds, projectState);
  }

  @Test
  public void testCreateProjectWithNonExistingOwner_UnprocessableEntity() throws Exception {
    ProjectInput in = new ProjectInput();
    in.name = name("newProjectName");
    in.owners = Collections.singletonList("non-existing-group");
    assertCreateFails(in, UnprocessableEntityException.class);
  }

  @Test
  public void testCreatePermissionOnlyProject() throws Exception {
    String newProjectName = name("newProject");
    ProjectInput in = new ProjectInput();
    in.name = newProjectName;
    in.permissionsOnly = true;
    gApi.projects().create(in);
    assertHead(newProjectName, RefNames.REFS_CONFIG);
  }

  @Test
  public void testCreateProjectWithEmptyCommit() throws Exception {
    String newProjectName = name("newProject");
    ProjectInput in = new ProjectInput();
    in.name = newProjectName;
    in.createEmptyCommit = true;
    gApi.projects().create(in);
    assertEmptyCommit(newProjectName, "refs/heads/master");
  }

  @Test
  public void testCreateProjectWithBranches() throws Exception {
    String newProjectName = name("newProject");
    ProjectInput in = new ProjectInput();
    in.name = newProjectName;
    in.createEmptyCommit = true;
    in.branches = Lists.newArrayListWithCapacity(3);
    in.branches.add("refs/heads/test");
    in.branches.add("refs/heads/master");
    in.branches.add("release"); // without 'refs/heads' prefix
    gApi.projects().create(in);
    assertHead(newProjectName, "refs/heads/test");
    assertEmptyCommit(newProjectName, "refs/heads/test", "refs/heads/master", "refs/heads/release");
  }

  @Test
  public void testCreateProjectWithoutCapability_Forbidden() throws Exception {
    setApiUser(user);
    ProjectInput in = new ProjectInput();
    in.name = name("newProject");
    assertCreateFails(in, AuthException.class);
  }

  @Test
  public void testCreateProjectWhenProjectAlreadyExists_Conflict() throws Exception {
    ProjectInput in = new ProjectInput();
    in.name = allProjects.get();
    assertCreateFails(in, ResourceConflictException.class);
  }

  private AccountGroup.UUID groupUuid(String groupName) {
    return groupCache.get(new AccountGroup.NameKey(groupName)).getGroupUUID();
  }

  private void assertHead(String projectName, String expectedRef) throws Exception {
    try (Repository repo = repoManager.openRepository(new Project.NameKey(projectName))) {
      assertThat(repo.exactRef(Constants.HEAD).getTarget().getName()).isEqualTo(expectedRef);
    }
  }

  private void assertEmptyCommit(String projectName, String... refs) throws Exception {
    Project.NameKey projectKey = new Project.NameKey(projectName);
    try (Repository repo = repoManager.openRepository(projectKey);
        RevWalk rw = new RevWalk(repo);
        TreeWalk tw = new TreeWalk(rw.getObjectReader())) {
      for (String ref : refs) {
        RevCommit commit = rw.lookupCommit(repo.exactRef(ref).getObjectId());
        rw.parseBody(commit);
        tw.addTree(commit.getTree());
        assertThat(tw.next()).isFalse();
        tw.reset();
      }
    }
  }

  private void assertCreateFails(ProjectInput in, Class<? extends RestApiException> errType)
      throws Exception {
    exception.expect(errType);
    gApi.projects().create(in);
  }
}
