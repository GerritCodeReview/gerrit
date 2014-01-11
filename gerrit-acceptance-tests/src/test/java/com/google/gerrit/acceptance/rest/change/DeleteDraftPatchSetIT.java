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

import static com.google.gerrit.acceptance.GitUtil.cloneProject;
import static com.google.gerrit.acceptance.GitUtil.createProject;
import static com.google.gerrit.acceptance.GitUtil.initSsh;
import static org.junit.Assert.assertEquals;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.ChangeJson.ChangeInfo;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class DeleteDraftPatchSetIT extends AbstractDaemonTest {

  @Inject
  private AccountCreator accounts;

  @Inject
  private SchemaFactory<ReviewDb> reviewDbProvider;

  @Inject
  private PushOneCommit.Factory pushFactory;

  private TestAccount admin;
  private TestAccount user;

  private RestSession session;
  private RestSession userSession;
  private Git git;
  private ReviewDb db;

  @Before
  public void setUp() throws Exception {
    admin = accounts.create("admin", "admin@example.com", "Administrator",
            "Administrators");
    user = accounts.create("user", "user@example.com", "User");
    session = new RestSession(server, admin);
    userSession = new RestSession(server, user);
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
  public void deletePatchSet() throws GitAPIException,
      IOException, OrmException {
    String changeId = createChangeWith2PS("refs/for/master");
    PatchSet ps = getCurrentPatchSet(changeId);
    ChangeInfo c = getChange(changeId);
    assertEquals("p~master~" + changeId, c.id);
    assertEquals(Change.Status.NEW, c.status);
    RestResponse r = deletePatchSet(changeId, ps, session);
    assertEquals("Patch set is not a draft.", r.getEntityContent());
    assertEquals(409, r.getStatusCode());
  }

  @Test
  public void deleteDraftPatchSetNoACL() throws GitAPIException,
      IOException, OrmException {
    String changeId = createChangeWith2PS("refs/drafts/master");
    PatchSet ps = getCurrentPatchSet(changeId);
    ChangeInfo c = getChange(changeId);
    assertEquals("p~master~" + changeId, c.id);
    assertEquals(Change.Status.DRAFT, c.status);
    RestResponse r = deletePatchSet(changeId, ps, userSession);
    assertEquals("Not found", r.getEntityContent());
    assertEquals(404, r.getStatusCode());
  }

  @Test
  public void deleteDraftPatchSetAndChange() throws GitAPIException,
      IOException, OrmException {
    String changeId = createChangeWith2PS("refs/drafts/master");
    PatchSet ps = getCurrentPatchSet(changeId);
    ChangeInfo c = getChange(changeId);
    assertEquals("p~master~" + changeId, c.id);
    assertEquals(Change.Status.DRAFT, c.status);
    RestResponse r = deletePatchSet(changeId, ps, session);
    assertEquals(204, r.getStatusCode());
    Change change = Iterables.getOnlyElement(db.changes().byKey(
        new Change.Key(changeId)).toList());
    assertEquals(1, db.patchSets().byChange(change.getId())
        .toList().size());
    ps = getCurrentPatchSet(changeId);
    r = deletePatchSet(changeId, ps, session);
    assertEquals(204, r.getStatusCode());
    assertEquals(0, db.changes().byKey(new Change.Key(changeId))
        .toList().size());
  }

  private String createChangeWith2PS(String ref) throws GitAPIException,
      IOException {
    PushOneCommit push = pushFactory.create(db, admin.getIdent());
    Result result = push.to(git, ref);
    push = pushFactory.create(db, admin.getIdent(), PushOneCommit.SUBJECT,
        "b.txt", "4711", result.getChangeId());
    return push.to(git, ref).getChangeId();
  }

  private ChangeInfo getChange(String changeId) throws IOException {
    RestResponse r = session.get("/changes/" + changeId + "/detail");
    return newGson().fromJson(r.getReader(), ChangeInfo.class);
  }

  private PatchSet getCurrentPatchSet(String changeId) throws OrmException {
    return db.patchSets()
        .get(Iterables.getOnlyElement(db.changes()
            .byKey(new Change.Key(changeId)))
            .currentPatchSetId());
  }

  private static RestResponse deletePatchSet(String changeId,
      PatchSet ps, RestSession s) throws IOException {
    return s.delete("/changes/"
        + changeId
        + "/revisions/"
        + ps.getRevision().get());
  }
}
