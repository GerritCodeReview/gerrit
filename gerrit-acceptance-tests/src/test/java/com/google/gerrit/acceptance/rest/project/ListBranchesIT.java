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

import static com.google.gerrit.acceptance.GitUtil.cloneProject;
import static com.google.gerrit.acceptance.GitUtil.createProject;
import static com.google.gerrit.acceptance.GitUtil.initSsh;
import static com.google.gerrit.acceptance.rest.project.BranchAssert.assertBranches;
import static org.junit.Assert.assertEquals;

import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.ListBranches.BranchInfo;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gson.reflect.TypeToken;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;

import com.jcraft.jsch.JSchException;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class ListBranchesIT extends AbstractDaemonTest {

  @Inject
  private AccountCreator accounts;

  @Inject
  private MetaDataUpdate.Server metaDataUpdateFactory;

  @Inject
  private ProjectCache projectCache;

  @Inject
  private SchemaFactory<ReviewDb> reviewDbProvider;

  @Inject
  private PushOneCommit.Factory pushFactory;

  private TestAccount admin;
  private RestSession session;
  private SshSession sshSession;
  private Project.NameKey project;
  private Git git;
  private ReviewDb db;

  @Before
  public void setUp() throws Exception {
    admin = accounts.create("admin", "admin@example.com", "Administrator",
            "Administrators");
    session = new RestSession(server, admin);

    project = new Project.NameKey("p");
    initSsh(admin);
    sshSession = new SshSession(server, admin);
    createProject(sshSession, project.get());
    git = cloneProject(sshSession.getUrl() + "/" + project.get());
    db = reviewDbProvider.open();
  }

  @After
  public void cleanup() {
    sshSession.close();
    db.close();
  }

  @Test
  public void listBranchesOfNonExistingProject_NotFound() throws IOException {
    assertEquals(HttpStatus.SC_NOT_FOUND,
        GET("/projects/non-existing/branches").getStatusCode());
  }

  @Test
  public void listBranchesOfNonVisibleProject_NotFound() throws IOException,
      OrmException, JSchException, ConfigInvalidException {
    blockRead(project, "refs/*");
    RestSession session =
        new RestSession(server, accounts.create("user", "user@example.com", "User"));
    assertEquals(HttpStatus.SC_NOT_FOUND,
        session.get("/projects/" + project.get() + "/branches").getStatusCode());
  }

  @Test
  public void listBranchesOfEmptyProject() throws IOException, JSchException {
    Project.NameKey emptyProject = new Project.NameKey("empty");
    createProject(sshSession, emptyProject.get(), null, false);
    RestResponse r = session.get("/projects/" + emptyProject.get() + "/branches");
    List<BranchInfo> expected = Lists.asList(
        new BranchInfo("refs/meta/config",  null, false),
        new BranchInfo[] {
          new BranchInfo("HEAD", null, false)
        });
    assertBranches(expected, toBranchInfoList(r));
  }

  @Test
  public void listBranches() throws IOException, GitAPIException {
    pushTo("refs/heads/master");
    String masterCommit = git.getRepository().getRef("master").getTarget().getObjectId().getName();
    pushTo("refs/heads/dev");
    String devCommit = git.getRepository().getRef("master").getTarget().getObjectId().getName();
    RestResponse r = session.get("/projects/" + project.get() + "/branches");
    List<BranchInfo> expected = Lists.asList(
        new BranchInfo("refs/meta/config",  null, false),
        new BranchInfo[] {
          new BranchInfo("HEAD", "master", false),
          new BranchInfo("refs/heads/master", masterCommit, false),
          new BranchInfo("refs/heads/dev", devCommit, true)
        });
    List<BranchInfo> result = toBranchInfoList(r);
    assertBranches(expected, result);

    // verify correct sorting
    assertEquals("HEAD", result.get(0).ref);
    assertEquals("refs/meta/config", result.get(1).ref);
    assertEquals("refs/heads/dev", result.get(2).ref);
    assertEquals("refs/heads/master", result.get(3).ref);
  }

  @Test
  public void listBranchesSomeHidden() throws IOException, GitAPIException,
      ConfigInvalidException, OrmException, JSchException {
    blockRead(project, "refs/heads/dev");
    RestSession session =
        new RestSession(server, accounts.create("user", "user@example.com", "User"));
    pushTo("refs/heads/master");
    String masterCommit = git.getRepository().getRef("master").getTarget().getObjectId().getName();
    pushTo("refs/heads/dev");
    RestResponse r = session.get("/projects/" + project.get() + "/branches");
    // refs/meta/config is hidden since user is no project owner
    List<BranchInfo> expected = Lists.asList(
        new BranchInfo("HEAD", "master", false),
        new BranchInfo[] {
          new BranchInfo("refs/heads/master", masterCommit, false),
        });
    assertBranches(expected, toBranchInfoList(r));
  }

  @Test
  public void listBranchesHeadHidden() throws IOException, GitAPIException,
      ConfigInvalidException, OrmException, JSchException {
    blockRead(project, "refs/heads/master");
    RestSession session =
        new RestSession(server, accounts.create("user", "user@example.com", "User"));
    pushTo("refs/heads/master");
    pushTo("refs/heads/dev");
    String devCommit = git.getRepository().getRef("master").getTarget().getObjectId().getName();
    RestResponse r = session.get("/projects/" + project.get() + "/branches");
    // refs/meta/config is hidden since user is no project owner
    assertBranches(Collections.singletonList(new BranchInfo("refs/heads/dev",
        devCommit, false)), toBranchInfoList(r));
  }

  private RestResponse GET(String endpoint) throws IOException {
    return session.get(endpoint);
  }

  private void blockRead(Project.NameKey project, String ref)
      throws RepositoryNotFoundException, IOException, ConfigInvalidException {
    MetaDataUpdate md = metaDataUpdateFactory.create(project);
    md.setMessage("Grant submit on " + ref);
    ProjectConfig config = ProjectConfig.read(md);
    AccessSection s = config.getAccessSection(ref, true);
    Permission p = s.getPermission(Permission.READ, true);
    PermissionRule rule = new PermissionRule(config.resolve(
        SystemGroupBackend.getGroup(SystemGroupBackend.REGISTERED_USERS)));
    rule.setBlock();
    p.add(rule);
    config.commit(md);
    projectCache.evict(config.getProject());
  }

  private static List<BranchInfo> toBranchInfoList(RestResponse r)
      throws IOException {
    List<BranchInfo> result =
        newGson().fromJson(r.getReader(),
            new TypeToken<List<BranchInfo>>() {}.getType());
    return result;
  }

  private PushOneCommit.Result pushTo(String ref) throws GitAPIException,
      IOException {
    PushOneCommit push = pushFactory.create(db, admin.getIdent());
    return push.to(git, ref);
  }
}
