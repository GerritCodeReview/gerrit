// Copyright (C) 2014 The Android Open Source Project
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
import static com.google.gerrit.server.project.Util.REGISTERED;
import static com.google.gerrit.server.project.Util.category;
import static com.google.gerrit.server.project.Util.grant;
import static com.google.gerrit.server.project.Util.value;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.common.collect.Maps;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.git.PushOneCommit;
import com.google.gerrit.common.changes.ListChangesOption;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.change.ChangeJson.LabelInfo;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class CustomLabelIT extends AbstractDaemonTest {

  @Inject
  private ProjectCache projectCache;

  @Inject
  private AllProjectsName allProjects;

  @Inject
  private MetaDataUpdate.Server metaDataUpdateFactory;

  @Inject
  private AccountCreator accounts;

  @Inject
  private SchemaFactory<ReviewDb> reviewDbProvider;

  private TestAccount admin;

  private RestSession session;
  private Git git;
  private ReviewDb db;

  private final LabelType Q = category("CustomLabel",
      value(1, "Positive"),
      value(0, "No score"),
      value(-1, "Negative"));

  @Before
  public void setUp() throws Exception {
    admin = accounts.admin();
    session = new RestSession(server, admin);
    initSsh(admin);
    Project.NameKey project = new Project.NameKey("p");
    SshSession sshSession = new SshSession(server, admin);
    createProject(sshSession, project.get());
    git = cloneProject(sshSession.getUrl() + "/" + project.get());
    sshSession.close();
    db = reviewDbProvider.open();
    ProjectConfig cfg = projectCache.checkedGet(allProjects).getConfig();
    grant(cfg, Permission.forLabel(Q.getName()), -1, +1, REGISTERED,
        "refs/heads/*");
    saveProjectConfig(cfg);
  }

  @After
  public void cleanup() {
    db.close();
  }

  @Test
  public void customLabelNoOp_NegativeVoteNotBlock() throws Exception {
    Q.setFunctionName("NoOp");
    saveLabelConfig();
    ChangeInfo c = get(disliked(change()));
    LabelInfo q = c.labels.get(Q.getName());
    assertEquals(1, q.all.size());
    assertNull(q.rejected);
    assertNotNull(q.disliked);
  }

  @Test
  public void customLabelNoBlock_NegativeVoteNotBlock() throws Exception {
    Q.setFunctionName("NoBlock");
    saveLabelConfig();
    ChangeInfo c = get(disliked(change()));
    LabelInfo q = c.labels.get(Q.getName());
    assertEquals(1, q.all.size());
    assertNull(q.rejected);
    assertNotNull(q.disliked);
  }

  @Test
  public void customLabelMaxNoBlock_NegativeVoteNotBlock() throws Exception {
    Q.setFunctionName("MaxNoBlock");
    saveLabelConfig();
    ChangeInfo c = get(disliked(change()));
    LabelInfo q = c.labels.get(Q.getName());
    assertEquals(1, q.all.size());
    assertNull(q.rejected);
    assertNotNull(q.disliked);
  }

  @Test
  public void customLabelAnyWithBlock_NegativeVoteBlock() throws Exception {
    Q.setFunctionName("AnyWithBlock");
    saveLabelConfig();
    ChangeInfo c = get(disliked(change()));
    LabelInfo q = c.labels.get(Q.getName());
    assertEquals(1, q.all.size());
    assertNull(q.disliked);
    assertNotNull(q.rejected);
  }

  @Test
  public void customLabelMaxWithBlock_NegativeVoteBlock() throws Exception {
    saveLabelConfig();
    ChangeInfo c = get(disliked(change()));
    LabelInfo q = c.labels.get(Q.getName());
    assertEquals(1, q.all.size());
    assertNull(q.disliked);
    assertNotNull(q.rejected);
  }

  private String disliked(String changeId) throws IOException {
    ReviewInput in = new ReviewInput();
    in.labels = Maps.newHashMap();
    in.labels.put(Q.getName(), -1);
    RestResponse r =
        session.post("/changes/" + changeId + "/revisions/current/review",
            in);
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    r.consume();
    return changeId;
  }

  private ChangeInfo get(String changeId) throws IOException {
    RestResponse r = session.get("/changes/"
        + changeId
        + "?o="
        + ListChangesOption.DETAILED_LABELS
        + "&o="
        + ListChangesOption.LABELS);
    return OutputFormat.JSON.newGson()
        .fromJson(r.getReader(), ChangeInfo.class);
  }

  private String change() throws GitAPIException,
      IOException {
    PushOneCommit push = new PushOneCommit(db, admin.getIdent());
    return push.to(git, "refs/for/master").getChangeId();
  }

  private void saveLabelConfig() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(allProjects).getConfig();
    cfg.getLabelSections().put(Q.getName(), Q);
    saveProjectConfig(cfg);
  }

  private void saveProjectConfig(ProjectConfig cfg) throws Exception {
    MetaDataUpdate md = metaDataUpdateFactory.create(allProjects);
    try {
      cfg.commit(md);
    } finally {
      md.close();
    }
    projectCache.evict(allProjects);
  }
}
