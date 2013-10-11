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

package com.google.gerrit.acceptance.rest.change;

import static com.google.gerrit.acceptance.git.GitUtil.cloneProject;
import static com.google.gerrit.acceptance.git.GitUtil.createProject;
import static com.google.gerrit.acceptance.git.GitUtil.initSsh;
import static com.google.gerrit.common.data.Permission.LABEL;
import static org.junit.Assert.assertEquals;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.git.PushOneCommit;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;

import com.jcraft.jsch.JSchException;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class ChangeOwnerIT extends AbstractDaemonTest {

  @Inject
  private AccountCreator accounts;

  @Inject
  private SchemaFactory<ReviewDb> reviewDbProvider;

  @Inject
  private MetaDataUpdate.Server metaDataUpdateFactory;

  @Inject
  private ProjectCache projectCache;

  @Inject
  private GroupCache groupCache;

  private TestAccount owner;
  private TestAccount dev;

  private RestSession sessionOwner;
  private RestSession sessionDev;
  private Git git;
  private ReviewDb db;
  private Project.NameKey project;

  @Before
  public void setUp() throws Exception {
    newProject();
    owner = accounts.user();
    sessionOwner = new RestSession(server, owner);
    SshSession sshSession = new SshSession(server, owner);
    initSsh(owner);
    // need to initialize intern session
    createProject(sshSession, "foo");
    git = cloneProject(sshSession.getUrl() + "/" + project.get());
    sshSession.close();
    dev = accounts.user2();
    sessionDev = new RestSession(server, dev);
    db = reviewDbProvider.open();
  }

  @After
  public void cleanup() {
    db.close();
  }

  @Test
  public void testChangeOwner_OwnerACLNotGranted() throws GitAPIException,
      IOException, OrmException, ConfigInvalidException {
    approve(sessionOwner, createChange(), HttpStatus.SC_FORBIDDEN);
  }

  @Test
  public void testChangeOwner_OwnerACLGranted() throws GitAPIException,
      IOException, OrmException, ConfigInvalidException {
    grantApproveToChangeOwner();
    approve(sessionOwner, createChange(), HttpStatus.SC_OK);
  }

  @Test
  public void testChangeOwner_NotOwnerACLGranted() throws GitAPIException,
      IOException, OrmException, ConfigInvalidException {
    grantApproveToChangeOwner();
    approve(sessionDev, createChange(), HttpStatus.SC_FORBIDDEN);
  }

  private void approve(RestSession s, String changeId, int expected)
      throws IOException {
    RestResponse r =
        s.post("/changes/" + changeId + "/revisions/current/review",
            ReviewInput.approve());
    assertEquals(expected, r.getStatusCode());
    r.consume();
  }

  private void grantApproveToChangeOwner() throws IOException,
      ConfigInvalidException {
    MetaDataUpdate md = metaDataUpdateFactory.create(project);
    md.setMessage(String.format("Grant approve to change owner"));
    ProjectConfig config = ProjectConfig.read(md);
    AccessSection s = config.getAccessSection("refs/heads/*", true);
    Permission p = s.getPermission(LABEL + "Code-Review", true);
    AccountGroup changeOwnerGroup = groupCache
        .get(new AccountGroup.NameKey("Change Owner"));
    PermissionRule rule = new PermissionRule(config
        .resolve(changeOwnerGroup));
    rule.setMin(-2);
    rule.setMax(+2);
    p.add(rule);
    config.commit(md);
    projectCache.evict(config.getProject());
  }

  private String createChange() throws GitAPIException,
      IOException {
    PushOneCommit push = new PushOneCommit(db, owner.getIdent());
    return push.to(git, "refs/for/master").getChangeId();
  }

  private void newProject() throws UnsupportedEncodingException,
      OrmException, JSchException, IOException {
    TestAccount admin = accounts.admin();
    initSsh(admin);
    project = new Project.NameKey("p");
    SshSession sshSession = new SshSession(server, admin);
    createProject(sshSession, project.get());
    sshSession.close();
  }
}
