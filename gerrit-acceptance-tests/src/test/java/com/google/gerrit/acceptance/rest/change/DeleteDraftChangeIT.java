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
import static org.junit.Assert.assertEquals;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.git.PushOneCommit;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class DeleteDraftChangeIT extends AbstractDaemonTest {

  @Inject
  private AccountCreator accounts;

  @Inject
  private SchemaFactory<ReviewDb> reviewDbProvider;

  private TestAccount admin;

  private RestSession session;
  private Git git;
  private ReviewDb db;

  @Before
  public void setUp() throws Exception {
    admin = accounts.create("admin", "admin@example.com", "Administrator",
            "Administrators");
    session = new RestSession(server, admin);
    initSsh(admin);
    Project.NameKey project = new Project.NameKey("p");
    SshSession sshSession = new SshSession(server, admin);
    createProject(sshSession, project.get());
    git = cloneProject(sshSession.getUrl() + "/" + project.get());
    sshSession.close();
    db = reviewDbProvider.open();
  }

  @After
  public void cleanup() {
    db.close();
  }

  @Test
  public void deleteChange() throws GitAPIException,
      IOException {
    String changeId = createChange();
    ChangeInfo c = getChange(changeId);
    assertEquals("p~master~" + changeId, c.id);
    assertEquals(Change.Status.NEW, c.status);
    RestResponse r = deleteChange(changeId, session);
    assertEquals("Change is not a draft", r.getEntityContent());
    assertEquals(409, r.getStatusCode());
  }

  @Test
  public void deleteDraftChange() throws GitAPIException,
      IOException, OrmException {
    String changeId = createDraftChange();
    ChangeInfo c = getChange(changeId);
    assertEquals("p~master~" + changeId, c.id);
    assertEquals(Change.Status.DRAFT, c.status);
    RestResponse r = deleteChange(changeId, session);
    assertEquals(204, r.getStatusCode());
  }

  @Test
  public void publishDraftChange() throws GitAPIException,
      IOException {
    String changeId = createDraftChange();
    ChangeInfo c = getChange(changeId);
    assertEquals("p~master~" + changeId, c.id);
    assertEquals(Change.Status.DRAFT, c.status);
    RestResponse r = publishChange(changeId);
    assertEquals(204, r.getStatusCode());
    c = getChange(changeId);
    assertEquals(Change.Status.NEW, c.status);
  }

  @Test
  public void publishDraftPatchSet() throws GitAPIException,
      IOException, OrmException {
    String changeId = createDraftChange();
    ChangeInfo c = getChange(changeId);
    assertEquals("p~master~" + changeId, c.id);
    assertEquals(Change.Status.DRAFT, c.status);
    RestResponse r = publishPatchSet(changeId);
    assertEquals(204, r.getStatusCode());
    c = getChange(changeId);
    assertEquals(Change.Status.NEW, c.status);
  }

  private ChangeInfo getChange(String changeId) throws IOException {
    RestResponse r = session.get("/changes/?q=" + changeId);
    List<ChangeInfo> c = (new Gson()).fromJson(r.getReader(),
        new TypeToken<List<ChangeInfo>>() {}.getType());
    return c.get(0);
  }

  private String createChange() throws GitAPIException,
      IOException {
    PushOneCommit push = new PushOneCommit(db, admin.getIdent());
    return push.to(git, "refs/for/master").getChangeId();
  }

  private String createDraftChange() throws GitAPIException, IOException {
    PushOneCommit push = new PushOneCommit(db, admin.getIdent());
    return push.to(git, "refs/drafts/master").getChangeId();
  }

  private static RestResponse deleteChange(String changeId,
      RestSession s) throws IOException {
    return s.delete("/changes/" + changeId);
  }

  private RestResponse publishChange(String changeId) throws IOException {
    return session.post("/changes/" + changeId + "/publish");
  }

  private RestResponse publishPatchSet(String changeId) throws IOException,
    OrmException {
    PatchSet patchSet = db.patchSets()
        .get(Iterables.getOnlyElement(db.changes()
            .byKey(new Change.Key(changeId)))
            .currentPatchSetId());
    return session.post("/changes/"
        + changeId
        + "/revisions/"
        + patchSet.getRevision().get()
        + "/publish");
  }
}
