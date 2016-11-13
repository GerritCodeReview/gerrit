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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import java.util.HashMap;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

@NoHttpd
public class DeleteDraftPatchSetIT extends AbstractDaemonTest {

  @Inject private AllUsersName allUsers;

  @Test
  public void deletePatchSetNotDraft() throws Exception {
    String changeId = createChange().getChangeId();
    PatchSet ps = getCurrentPatchSet(changeId);
    String triplet = project.get() + "~master~" + changeId;
    ChangeInfo c = get(triplet);
    assertThat(c.id).isEqualTo(triplet);
    assertThat(c.status).isEqualTo(ChangeStatus.NEW);

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("Patch set is not a draft");
    setApiUser(admin);
    deletePatchSet(changeId, ps);
  }

  @Test
  public void deleteDraftPatchSetNoACL() throws Exception {
    String changeId = createDraftChangeWith2PS();
    PatchSet ps = getCurrentPatchSet(changeId);
    String triplet = project.get() + "~master~" + changeId;
    ChangeInfo c = get(triplet);
    assertThat(c.id).isEqualTo(triplet);
    assertThat(c.status).isEqualTo(ChangeStatus.DRAFT);

    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage("Not found: " + changeId);
    setApiUser(user);
    deletePatchSet(changeId, ps);
  }

  @Test
  public void deleteDraftPatchSetAndChange() throws Exception {
    String changeId = createDraftChangeWith2PS();
    PatchSet ps = getCurrentPatchSet(changeId);
    Change.Id id = ps.getId().getParentKey();

    DraftInput din = new DraftInput();
    din.path = "a.txt";
    din.message = "comment on a.txt";
    gApi.changes().id(changeId).current().createDraft(din);

    if (notesMigration.writeChanges()) {
      assertThat(getDraftRef(admin, id)).isNotNull();
    }

    ChangeData cd = getChange(changeId);
    assertThat(cd.patchSets()).hasSize(2);
    assertThat(cd.change().currentPatchSetId().get()).isEqualTo(2);
    assertThat(cd.change().getStatus()).isEqualTo(Change.Status.DRAFT);
    deletePatchSet(changeId, ps);

    cd = getChange(changeId);
    assertThat(cd.patchSets()).hasSize(1);
    assertThat(cd.change().currentPatchSetId().get()).isEqualTo(1);

    ps = getCurrentPatchSet(changeId);
    deletePatchSet(changeId, ps);
    assertThat(queryProvider.get().byKeyPrefix(changeId)).isEmpty();

    if (notesMigration.writeChanges()) {
      assertThat(getDraftRef(admin, id)).isNull();
      assertThat(getMetaRef(id)).isNull();
    }

    exception.expect(ResourceNotFoundException.class);
    gApi.changes().id(id.get());
  }

  @Test
  public void deleteDraftPS1() throws Exception {
    String changeId = createDraftChangeWith2PS();

    ReviewInput rin = new ReviewInput();
    rin.message = "Change message";
    CommentInput cin = new CommentInput();
    cin.line = 1;
    cin.patchSet = 1;
    cin.path = PushOneCommit.FILE_NAME;
    cin.side = Side.REVISION;
    cin.message = "Inline comment";
    rin.comments = new HashMap<>();
    rin.comments.put(cin.path, ImmutableList.of(cin));
    gApi.changes().id(changeId).revision(1).review(rin);

    ChangeData cd = getChange(changeId);
    PatchSet.Id delPsId = new PatchSet.Id(cd.getId(), 1);
    PatchSet ps = cd.patchSet(delPsId);
    deletePatchSet(changeId, ps);

    cd = getChange(changeId);
    assertThat(cd.patchSets()).hasSize(1);
    assertThat(Iterables.getOnlyElement(cd.patchSets()).getId().get()).isEqualTo(2);

    // Other entities based on deleted patch sets are also deleted.
    for (ChangeMessage m : cd.messages()) {
      assertThat(m.getPatchSetId()).named(m.toString()).isNotEqualTo(delPsId);
    }
    for (Comment c : cd.publishedComments()) {
      assertThat(c.key.patchSetId).named(c.toString()).isNotEqualTo(delPsId.get());
    }
  }

  @Test
  public void deleteDraftPS2() throws Exception {
    String changeId = createDraftChangeWith2PS();

    ReviewInput rin = new ReviewInput();
    rin.message = "Change message";
    CommentInput cin = new CommentInput();
    cin.line = 1;
    cin.patchSet = 1;
    cin.path = PushOneCommit.FILE_NAME;
    cin.side = Side.REVISION;
    cin.message = "Inline comment";
    rin.comments = new HashMap<>();
    rin.comments.put(cin.path, ImmutableList.of(cin));
    gApi.changes().id(changeId).revision(1).review(rin);

    ChangeData cd = getChange(changeId);
    PatchSet.Id delPsId = new PatchSet.Id(cd.getId(), 2);
    PatchSet ps = cd.patchSet(delPsId);
    deletePatchSet(changeId, ps);

    cd = getChange(changeId);
    assertThat(cd.patchSets()).hasSize(1);
    assertThat(Iterables.getOnlyElement(cd.patchSets()).getId().get()).isEqualTo(1);

    // Other entities based on deleted patch sets are also deleted.
    for (ChangeMessage m : cd.messages()) {
      assertThat(m.getPatchSetId()).named(m.toString()).isNotEqualTo(delPsId);
    }
    for (Comment c : cd.publishedComments()) {
      assertThat(c.key.patchSetId).named(c.toString()).isNotEqualTo(delPsId.get());
    }
  }

  @Test
  public void deleteDraftPatchSetAndPushNewDraftPatchSet() throws Exception {
    String ref = "refs/drafts/master";

    // Clone repository
    TestRepository<InMemoryRepository> testRepo = cloneProject(project, admin);

    // Create change
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    PushOneCommit.Result r1 = push.to(ref);
    r1.assertOkStatus();
    String revPs1 = r1.getChange().currentPatchSet().getRevision().get();

    // Push draft patch set
    PushOneCommit.Result r2 = amendChange(r1.getChangeId(), ref, admin, testRepo);
    r2.assertOkStatus();
    String revPs2 = r2.getChange().currentPatchSet().getRevision().get();

    assertThat(gApi.changes().id(r1.getChange().getId().get()).get().currentRevision)
        .isEqualTo(revPs2);

    // Remove draft patch set
    gApi.changes().id(r1.getChange().getId().get()).revision(revPs2).delete();

    assertThat(gApi.changes().id(r1.getChange().getId().get()).get().currentRevision)
        .isEqualTo(revPs1);

    // Push new draft patch set
    PushOneCommit.Result r3 = amendChange(r1.getChangeId(), ref, admin, testRepo);
    r3.assertOkStatus();
    String revPs3 = r2.getChange().currentPatchSet().getRevision().get();

    assertThat(gApi.changes().id(r1.getChange().getId().get()).get().currentRevision)
        .isEqualTo(revPs3);

    // Check that all patch sets have different SHA1s
    assertThat(revPs1).doesNotMatch(revPs2);
    assertThat(revPs2).doesNotMatch(revPs3);
  }

  private Ref getDraftRef(TestAccount account, Change.Id changeId) throws Exception {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      return repo.exactRef(RefNames.refsDraftComments(changeId, account.id));
    }
  }

  private Ref getMetaRef(Change.Id changeId) throws Exception {
    try (Repository repo = repoManager.openRepository(project)) {
      return repo.exactRef(RefNames.changeMetaRef(changeId));
    }
  }

  private String createDraftChangeWith2PS() throws Exception {
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    Result result = push.to("refs/drafts/master");
    push =
        pushFactory.create(
            db,
            admin.getIdent(),
            testRepo,
            PushOneCommit.SUBJECT,
            "b.txt",
            "4711",
            result.getChangeId());
    return push.to("refs/drafts/master").getChangeId();
  }

  private PatchSet getCurrentPatchSet(String changeId) throws Exception {
    return getChange(changeId).currentPatchSet();
  }

  private ChangeData getChange(String changeId) throws Exception {
    return Iterables.getOnlyElement(queryProvider.get().byKeyPrefix(changeId));
  }

  private void deletePatchSet(String changeId, PatchSet ps) throws Exception {
    gApi.changes().id(changeId).revision(ps.getId().get()).delete();
  }
}
