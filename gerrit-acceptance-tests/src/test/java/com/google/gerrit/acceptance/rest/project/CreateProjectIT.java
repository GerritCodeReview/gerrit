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

import static com.google.gerrit.acceptance.rest.project.ProjectAssert.assertProjectInfo;
import static com.google.gerrit.acceptance.rest.project.ProjectAssert.assertProjectOwners;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.InheritableBoolean;
import com.google.gerrit.reviewdb.client.Project.SubmitType;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import com.jcraft.jsch.JSchException;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class CreateProjectIT extends AbstractDaemonTest {

  @Inject
  private AccountCreator accounts;

  @Inject
  private ProjectCache projectCache;

  @Inject
  private GroupCache groupCache;

  @Inject
  private GitRepositoryManager git;

  private TestAccount admin;
  private RestSession session;

  @Before
  public void setUp() throws Exception {
    admin = accounts.create("admin", "admin@example.com", "Administrator",
            "Administrators");
    session = new RestSession(admin);
  }

  @Test
  public void testCreateProject() throws IOException {
    final String newProjectName = "newProject";
    RestResponse r = session.put("/projects/" + newProjectName);
    assertEquals(HttpStatus.SC_CREATED, r.getStatusCode());
    ProjectInfo p = (new Gson()).fromJson(r.getReader(), new TypeToken<ProjectInfo>() {}.getType());
    assertEquals(newProjectName, p.name);
    ProjectState projectState = projectCache.get(new Project.NameKey(newProjectName));
    assertNotNull(projectState);
    assertProjectInfo(projectState.getProject(), p);
    assertHead(newProjectName, "refs/heads/master");
  }

  @Test
  public void testCreateProjectWithNameMismatch_BadRequest() throws IOException {
    ProjectInput in = new ProjectInput();
    in.name = "otherName";
    RestResponse r = session.put("/projects/someName", in);
    assertEquals(HttpStatus.SC_BAD_REQUEST, r.getStatusCode());
  }

  @Test
  public void testCreateProjectWithProperties() throws IOException {
    final String newProjectName = "newProject";
    ProjectInput in = new ProjectInput();
    in.description = "Test description";
    in.submit_type = SubmitType.CHERRY_PICK;
    in.use_contributor_agreements = InheritableBoolean.TRUE;
    in.use_signed_off_by = InheritableBoolean.TRUE;
    in.use_content_merge = InheritableBoolean.TRUE;
    in.require_change_id = InheritableBoolean.TRUE;
    RestResponse r = session.put("/projects/" + newProjectName, in);
    ProjectInfo p = (new Gson()).fromJson(r.getReader(), new TypeToken<ProjectInfo>() {}.getType());
    assertEquals(newProjectName, p.name);
    Project project = projectCache.get(new Project.NameKey(newProjectName)).getProject();
    assertProjectInfo(project, p);
    assertEquals(in.description, project.getDescription());
    assertEquals(in.submit_type, project.getSubmitType());
    assertEquals(in.use_contributor_agreements, project.getUseContributorAgreements());
    assertEquals(in.use_signed_off_by, project.getUseSignedOffBy());
    assertEquals(in.use_content_merge, project.getUseContentMerge());
    assertEquals(in.require_change_id, project.getRequireChangeID());
  }

  @Test
  public void testCreateChildProject() throws IOException {
    final String parentName = "parent";
    RestResponse r = session.put("/projects/" + parentName);
    r.consume();
    final String childName = "child";
    ProjectInput in = new ProjectInput();
    in.parent = parentName;
    r = session.put("/projects/" + childName, in);
    Project project = projectCache.get(new Project.NameKey(childName)).getProject();
    assertEquals(in.parent, project.getParentName());
  }

  public void testCreateChildProjectUnderNonExistingParent_UnprocessableEntity()
      throws IOException {
    ProjectInput in = new ProjectInput();
    in.parent = "non-existing-project";
    RestResponse r = session.put("/projects/child", in);
    assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, r.getStatusCode());
  }

  @Test
  public void testCreateProjectWithOwner() throws IOException {
    final String newProjectName = "newProject";
    ProjectInput in = new ProjectInput();
    in.owners = Lists.newArrayListWithCapacity(3);
    in.owners.add("Administrators"); // by name
    in.owners.add(groupUuid("Registered Users").get()); // by group UUID
    in.owners.add(Integer.toString(groupCache.get(new AccountGroup.NameKey("Anonymous Users"))
        .getId().get())); // by legacy group ID
    session.put("/projects/" + newProjectName, in);
    ProjectState projectState = projectCache.get(new Project.NameKey(newProjectName));
    Set<AccountGroup.UUID> expectedOwnerIds = Sets.newHashSetWithExpectedSize(3);
    expectedOwnerIds.add(groupUuid("Administrators"));
    expectedOwnerIds.add(groupUuid("Registered Users"));
    expectedOwnerIds.add(groupUuid("Anonymous Users"));
    assertProjectOwners(expectedOwnerIds, projectState);
  }

  public void testCreateProjectWithNonExistingOwner_UnprocessableEntity()
      throws IOException {
    ProjectInput in = new ProjectInput();
    in.owners = Collections.singletonList("non-existing-group");
    RestResponse r = session.put("/projects/newProject", in);
    assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, r.getStatusCode());
  }

  @Test
  public void testCreatePermissionOnlyProject() throws IOException {
    final String newProjectName = "newProject";
    ProjectInput in = new ProjectInput();
    in.permissions_only = true;
    session.put("/projects/" + newProjectName, in);
    assertHead(newProjectName, GitRepositoryManager.REF_CONFIG);
  }

  @Test
  public void testCreateProjectWithEmptyCommit() throws IOException {
    final String newProjectName = "newProject";
    ProjectInput in = new ProjectInput();
    in.create_empty_commit = true;
    session.put("/projects/" + newProjectName, in);
    assertEmptyCommit(newProjectName, "refs/heads/master");
  }

  @Test
  public void testCreateProjectWithBranches() throws IOException {
    final String newProjectName = "newProject";
    ProjectInput in = new ProjectInput();
    in.create_empty_commit = true;
    in.branches = Lists.newArrayListWithCapacity(3);
    in.branches.add("refs/heads/test");
    in.branches.add("refs/heads/master");
    in.branches.add("release"); // without 'refs/heads' prefix
    session.put("/projects/" + newProjectName, in);
    assertHead(newProjectName, "refs/heads/test");
    assertEmptyCommit(newProjectName, "refs/heads/test", "refs/heads/master",
        "refs/heads/release");
  }

  @Test
  public void testCreateProjectWithoutCapability_Forbidden() throws OrmException,
      JSchException, IOException {
    TestAccount user = accounts.create("user", "user@example.com", "User");
    RestResponse r = (new RestSession(user)).put("/projects/newProject");
    assertEquals(HttpStatus.SC_FORBIDDEN, r.getStatusCode());
  }

  @Test
  public void testCreateProjectWhenProjectAlreadyExists_Conflict()
      throws OrmException, JSchException, IOException {
    RestResponse r = session.put("/projects/All-Projects");
    assertEquals(HttpStatus.SC_CONFLICT, r.getStatusCode());
  }

  private AccountGroup.UUID groupUuid(String groupName) {
    return groupCache.get(new AccountGroup.NameKey(groupName)).getGroupUUID();
  }

  private void assertHead(String projectName, String expectedRef)
      throws RepositoryNotFoundException, IOException {
    Repository repo = git.openRepository(new Project.NameKey(projectName));
    try {
      assertEquals(expectedRef, repo.getRef(Constants.HEAD).getTarget()
          .getName());
    } finally {
      repo.close();
    }
  }

  private void assertEmptyCommit(String projectName, String... refs)
      throws RepositoryNotFoundException, IOException {
    Repository repo = git.openRepository(new Project.NameKey(projectName));
    RevWalk rw = new RevWalk(repo);
    TreeWalk tw = new TreeWalk(repo);
    try {
      for (String ref : refs) {
        RevCommit commit = rw.lookupCommit(repo.getRef(ref).getObjectId());
        rw.parseBody(commit);
        tw.addTree(commit.getTree());
        assertFalse("ref " + ref + " has non empty commit", tw.next());
        tw.reset();
      }
    } finally {
      tw.release();
      rw.release();
      repo.close();
    }
  }

  @SuppressWarnings("unused")
  private static class ProjectInput {
    String name;
    String parent;
    String description;
    boolean permissions_only;
    boolean create_empty_commit;
    SubmitType submit_type;
    List<String> branches;
    List<String> owners;
    InheritableBoolean use_contributor_agreements;
    InheritableBoolean use_signed_off_by;
    InheritableBoolean use_content_merge;
    InheritableBoolean require_change_id;
  }
}
