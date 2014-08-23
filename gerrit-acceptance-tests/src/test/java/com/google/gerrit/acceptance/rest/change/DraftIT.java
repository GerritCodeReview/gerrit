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

import static org.junit.Assert.assertEquals;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeStatus;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwtorm.server.OrmException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.Test;

import java.io.IOException;

public class DraftIT extends AbstractDaemonTest {

  @Test
  public void testAll() throws Exception {
    deleteChange();
    deleteDraftChange();
    publishDraftChange();
    publishDraftPatchSet();
    deletePatchSet();
    deleteDraftPatchSetNoACL();
    reset();
    deleteDraftPatchSetAndChange();
  }

  public void deleteChange() throws GitAPIException,
      IOException, RestApiException {
    String changeId = createChange().getChangeId();
    String triplet = "p~master~" + changeId;
    ChangeInfo c = get(triplet);
    assertEquals(triplet, c.id);
    assertEquals(ChangeStatus.NEW, c.status);
    RestResponse r = deleteChange(changeId, adminSession);
    assertEquals("Change is not a draft", r.getEntityContent());
    assertEquals(409, r.getStatusCode());
  }

  public void deleteDraftChange() throws GitAPIException,
      IOException, RestApiException, OrmException {
    String changeId = createDraftChange();
    String triplet = "p~master~" + changeId;
    ChangeInfo c = get(triplet);
    assertEquals(triplet, c.id);
    assertEquals(ChangeStatus.DRAFT, c.status);
    RestResponse r = deleteChange(changeId, adminSession);
    assertEquals(204, r.getStatusCode());
  }

  public void publishDraftChange() throws GitAPIException,
      IOException, RestApiException {
    String changeId = createDraftChange();
    String triplet = "p~master~" + changeId;
    ChangeInfo c = get(triplet);
    assertEquals(triplet, c.id);
    assertEquals(ChangeStatus.DRAFT, c.status);
    RestResponse r = publishChange(changeId);
    assertEquals(204, r.getStatusCode());
    c = get(triplet);
    assertEquals(ChangeStatus.NEW, c.status);
  }

  public void publishDraftPatchSet() throws GitAPIException,
      IOException, OrmException, RestApiException {
    String changeId = createDraftChange();
    String triplet = "p~master~" + changeId;
    ChangeInfo c = get(triplet);
    assertEquals(triplet, c.id);
    assertEquals(ChangeStatus.DRAFT, c.status);
    RestResponse r = publishPatchSet(changeId);
    assertEquals(204, r.getStatusCode());
    assertEquals(ChangeStatus.NEW, get(triplet).status);
  }

  public void deletePatchSet() throws Exception {
    String changeId = createChange().getChangeId();
    PatchSet ps = getCurrentPatchSet(changeId);
    String triplet = "p~master~" + changeId;
    ChangeInfo c = get(triplet);
    assertEquals(triplet, c.id);
    assertEquals(ChangeStatus.NEW, c.status);
    RestResponse r = deletePatchSet(changeId, ps, adminSession);
    assertEquals("Patch set is not a draft.", r.getEntityContent());
    assertEquals(409, r.getStatusCode());
  }

  public void deleteDraftPatchSetNoACL() throws Exception {
    String changeId = createDraftChangeWith2PS();
    PatchSet ps = getCurrentPatchSet(changeId);
    String triplet = "p~master~" + changeId;
    ChangeInfo c = get(triplet);
    assertEquals(triplet, c.id);
    assertEquals(ChangeStatus.DRAFT, c.status);
    RestResponse r = deletePatchSet(changeId, ps, userSession);
    assertEquals("Not found", r.getEntityContent());
    assertEquals(404, r.getStatusCode());
  }

  public void deleteDraftPatchSetAndChange() throws Exception {
    String changeId = createDraftChangeWith2PS();
    PatchSet ps = getCurrentPatchSet(changeId);
    String triplet = "p~master~" + changeId;
    ChangeInfo c = get(triplet);
    assertEquals(triplet, c.id);
    assertEquals(ChangeStatus.DRAFT, c.status);
    RestResponse r = deletePatchSet(changeId, ps, adminSession);
    assertEquals(204, r.getStatusCode());
    Change change = Iterables.getOnlyElement(db.changes().byKey(
        new Change.Key(changeId)).toList());
    assertEquals(1, db.patchSets().byChange(change.getId())
        .toList().size());
    ps = getCurrentPatchSet(changeId);
    r = deletePatchSet(changeId, ps, adminSession);
    assertEquals(204, r.getStatusCode());
    assertEquals(0, db.changes().byKey(new Change.Key(changeId))
        .toList().size());
  }

  private String createDraftChangeWith2PS() throws GitAPIException,
      IOException {
    PushOneCommit push = pushFactory.create(db, admin.getIdent());
    Result result = push.to(git, "refs/drafts/master");
    push = pushFactory.create(db, admin.getIdent(), PushOneCommit.SUBJECT,
        "b.txt", "4711", result.getChangeId());
    return push.to(git, "refs/drafts/master").getChangeId();
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

  private String createDraftChange() throws GitAPIException, IOException {
    PushOneCommit push = pushFactory.create(db, admin.getIdent());
    return push.to(git, "refs/drafts/master").getChangeId();
  }

  private static RestResponse deleteChange(String changeId,
      RestSession s) throws IOException {
    return s.delete("/changes/" + changeId);
  }

  private RestResponse publishChange(String changeId) throws IOException {
    return adminSession.post("/changes/" + changeId + "/publish");
  }

  private RestResponse publishPatchSet(String changeId) throws IOException,
    OrmException {
    PatchSet patchSet = db.patchSets()
        .get(Iterables.getOnlyElement(db.changes()
            .byKey(new Change.Key(changeId)))
            .currentPatchSetId());
    return adminSession.post("/changes/"
        + changeId
        + "/revisions/"
        + patchSet.getRevision().get()
        + "/publish");
  }
}
