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

package com.google.gerrit.acceptance.api.revision;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.PushOneCommit.FILE_CONTENT;
import static com.google.gerrit.acceptance.PushOneCommit.FILE_NAME;
import static com.google.gerrit.acceptance.PushOneCommit.PATCH;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.HEAD;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.api.changes.DraftApi;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.common.MergeableInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Patch;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@NoHttpd
public class RevisionIT extends AbstractDaemonTest {

  private TestAccount admin2;

  @Before
  public void setUp() throws Exception {
    admin2 = accounts.admin2();
  }

  @Test
  public void reviewTriplet() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id(project.get() + "~master~" + r.getChangeId())
        .revision(r.getCommit().name())
        .review(ReviewInput.approve());
  }

  @Test
  public void reviewCurrent() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id(r.getChangeId())
        .current()
        .review(ReviewInput.approve());
  }

  @Test
  public void reviewNumber() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id(r.getChangeId())
        .revision(1)
        .review(ReviewInput.approve());

    r = updateChange(r, "new content");
    gApi.changes()
        .id(r.getChangeId())
        .revision(2)
        .review(ReviewInput.approve());
  }

  @Test
  public void submit() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id(project.get() + "~master~" + r.getChangeId())
        .current()
        .review(ReviewInput.approve());
    gApi.changes()
        .id(project.get() + "~master~" + r.getChangeId())
        .current()
        .submit();
  }

  @Test(expected = AuthException.class)
  public void submitOnBehalfOf() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id(project.get() + "~master~" + r.getChangeId())
        .current()
        .review(ReviewInput.approve());
    SubmitInput in = new SubmitInput();
    in.onBehalfOf = admin2.email;
    gApi.changes()
        .id(project.get() + "~master~" + r.getChangeId())
        .current()
        .submit(in);
  }

  @Test
  public void deleteDraft() throws Exception {
    PushOneCommit.Result r = createDraft();
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .delete();
  }

  @Test
  public void cherryPick() throws Exception {
    PushOneCommit.Result r = pushTo("refs/for/master%topic=someTopic");
    CherryPickInput in = new CherryPickInput();
    in.destination = "foo";
    in.message = "it goes to stable branch";
    gApi.projects()
        .name(project.get())
        .branch(in.destination)
        .create(new BranchInput());
    ChangeApi orig = gApi.changes()
        .id(project.get() + "~master~" + r.getChangeId());

    assertThat(orig.get().messages).hasSize(1);
    ChangeApi cherry = orig.revision(r.getCommit().name())
        .cherryPick(in);

    Collection<ChangeMessageInfo> messages = gApi.changes()
        .id(project.get() + "~master~" + r.getChangeId())
        .get().messages;
    assertThat(messages).hasSize(2);

    String cherryPickedRevision = cherry.get().currentRevision;
    String expectedMessage = String.format(
        "Patch Set 1: Cherry Picked\n\n" +
        "This patchset was cherry picked to branch %s as commit %s",
        in.destination, cherryPickedRevision);

    Iterator<ChangeMessageInfo> origIt = messages.iterator();
    origIt.next();
    assertThat(origIt.next().message).isEqualTo(expectedMessage);

    assertThat(cherry.get().messages).hasSize(1);
    Iterator<ChangeMessageInfo> cherryIt = cherry.get().messages.iterator();
    expectedMessage = "Patch Set 1: Cherry Picked from branch master.";
    assertThat(cherryIt.next().message).isEqualTo(expectedMessage);

    assertThat(cherry.get().subject).contains(in.message);
    assertThat(cherry.get().topic).isEqualTo("someTopic-foo");
    cherry.current().review(ReviewInput.approve());
    cherry.current().submit();
  }

  @Test
  public void cherryPickwithNoTopic() throws Exception {
    PushOneCommit.Result r = pushTo("refs/for/master");
    CherryPickInput in = new CherryPickInput();
    in.destination = "foo";
    in.message = "it goes to stable branch";
    gApi.projects()
        .name(project.get())
        .branch(in.destination)
        .create(new BranchInput());
    ChangeApi orig = gApi.changes()
        .id(project.get() + "~master~" + r.getChangeId());

    ChangeApi cherry = orig.revision(r.getCommit().name())
        .cherryPick(in);
    assertThat(cherry.get().topic).isNull();
    cherry.current().review(ReviewInput.approve());
    cherry.current().submit();
  }

  @Test
  public void cherryPickToSameBranch() throws Exception {
    PushOneCommit.Result r = createChange();
    CherryPickInput in = new CherryPickInput();
    in.destination = "master";
    in.message = "it generates a new patch set\n\nChange-Id: " + r.getChangeId();
    ChangeInfo cherryInfo = gApi.changes()
        .id(project.get() + "~master~" + r.getChangeId())
        .revision(r.getCommit().name())
        .cherryPick(in)
        .get();
    assertThat(cherryInfo.messages).hasSize(2);
    Iterator<ChangeMessageInfo> cherryIt = cherryInfo.messages.iterator();
    assertThat(cherryIt.next().message).isEqualTo("Uploaded patch set 1.");
    assertThat(cherryIt.next().message).isEqualTo("Uploaded patch set 2.");
  }

  @Test
  public void cherryPickToSameBranchWithRebase() throws Exception {
    // Push a new change, then merge it
    PushOneCommit.Result baseChange = createChange();
    String triplet = project.get() + "~master~" + baseChange.getChangeId();
    RevisionApi baseRevision = gApi.changes().id(triplet).current();
    baseRevision.review(ReviewInput.approve());
    baseRevision.submit();

    // Push a new change (change 1)
    PushOneCommit.Result r1 = createChange();

    // Push another new change (change 2)
    String subject = "Test change\n\n" +
        "Change-Id: Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(), testRepo, subject,
            "another_file.txt", "another content");
    PushOneCommit.Result r2 = push.to("refs/for/master");

    // Change 2's parent should be change 1
    assertThat(r2.getCommit().getParents()[0].name())
      .isEqualTo(r1.getCommit().name());

    // Cherry pick change 2 onto the same branch
    triplet = project.get() + "~master~" + r2.getChangeId();
    ChangeApi orig = gApi.changes().id(triplet);
    CherryPickInput in = new CherryPickInput();
    in.destination = "master";
    in.message = subject;
    ChangeApi cherry = orig.revision(r2.getCommit().name()).cherryPick(in);
    ChangeInfo cherryInfo = cherry.get();
    assertThat(cherryInfo.messages).hasSize(2);
    Iterator<ChangeMessageInfo> cherryIt = cherryInfo.messages.iterator();
    assertThat(cherryIt.next().message).isEqualTo("Uploaded patch set 1.");
    assertThat(cherryIt.next().message).isEqualTo("Uploaded patch set 2.");

    // Parent of change 2 should now be the change that was merged, i.e.
    // change 2 is rebased onto the head of the master branch.
    String newParent = cherryInfo.revisions.get(cherryInfo.currentRevision)
        .commit.parents.get(0).commit;
    assertThat(newParent).isEqualTo(baseChange.getCommit().name());
  }

  @Test
  public void cherryPickIdenticalTree() throws Exception {
    PushOneCommit.Result r = createChange();
    CherryPickInput in = new CherryPickInput();
    in.destination = "foo";
    in.message = "it goes to stable branch";
    gApi.projects()
        .name(project.get())
        .branch(in.destination)
        .create(new BranchInput());
    ChangeApi orig = gApi.changes()
        .id(project.get() + "~master~" + r.getChangeId());

    assertThat(orig.get().messages).hasSize(1);
    ChangeApi cherry = orig.revision(r.getCommit().name())
        .cherryPick(in);

    Collection<ChangeMessageInfo> messages = gApi.changes()
        .id(project.get() + "~master~" + r.getChangeId())
        .get().messages;
    assertThat(messages).hasSize(2);

    assertThat(cherry.get().subject).contains(in.message);
    cherry.current().review(ReviewInput.approve());
    cherry.current().submit();

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("Cherry pick failed: identical tree");
    orig.revision(r.getCommit().name()).cherryPick(in);
  }

  @Test
  public void cherryPickConflict() throws Exception {
    PushOneCommit.Result r = createChange();
    CherryPickInput in = new CherryPickInput();
    in.destination = "foo";
    in.message = "it goes to stable branch";
    gApi.projects()
        .name(project.get())
        .branch(in.destination)
        .create(new BranchInput());

    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(), testRepo, PushOneCommit.SUBJECT,
            PushOneCommit.FILE_NAME, "another content");
    push.to("refs/heads/foo");

    String triplet = project.get() + "~master~" + r.getChangeId();
    ChangeApi orig = gApi.changes().id(triplet);
    assertThat(orig.get().messages).hasSize(1);

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("Cherry pick failed: merge conflict");
    orig.revision(r.getCommit().name()).cherryPick(in);
  }

  @Test
  public void canRebase() throws Exception {
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    PushOneCommit.Result r1 = push.to("refs/for/master");
    merge(r1);

    push = pushFactory.create(db, admin.getIdent(), testRepo);
    PushOneCommit.Result r2 = push.to("refs/for/master");
    boolean canRebase = gApi.changes()
        .id(r2.getChangeId())
        .revision(r2.getCommit().name())
        .canRebase();
    assertThat(canRebase).isFalse();
    merge(r2);

    testRepo.reset(r1.getCommit());
    push = pushFactory.create(db, admin.getIdent(), testRepo);
    PushOneCommit.Result r3 = push.to("refs/for/master");

    canRebase = gApi.changes()
        .id(r3.getChangeId())
        .revision(r3.getCommit().name())
        .canRebase();
    assertThat(canRebase).isTrue();
  }

  @Test
  public void setUnsetReviewedFlag() throws Exception {
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    PushOneCommit.Result r = push.to("refs/for/master");

    gApi.changes()
        .id(r.getChangeId())
        .current()
        .setReviewed(PushOneCommit.FILE_NAME, true);

    assertThat(Iterables.getOnlyElement(
            gApi.changes()
                .id(r.getChangeId())
                .current()
                .reviewed())).isEqualTo(PushOneCommit.FILE_NAME);

    gApi.changes()
        .id(r.getChangeId())
        .current()
        .setReviewed(PushOneCommit.FILE_NAME, false);

    assertThat(gApi.changes().id(r.getChangeId()).current().reviewed())
        .isEmpty();
  }

  @Test
  public void mergeable() throws Exception {
    ObjectId initial = repo().getRef(HEAD).getLeaf().getObjectId();

    PushOneCommit push1 =
        pushFactory.create(db, admin.getIdent(), testRepo, PushOneCommit.SUBJECT,
            PushOneCommit.FILE_NAME, "push 1 content");

    PushOneCommit.Result r1 = push1.to("refs/for/master");
    assertMergeable(r1.getChangeId(), true);
    merge(r1);

    // Reset HEAD to initial so the new change is a merge conflict.
    RefUpdate ru = repo().updateRef(HEAD);
    ru.setNewObjectId(initial);
    assertThat(ru.forceUpdate()).isEqualTo(RefUpdate.Result.FORCED);

    PushOneCommit push2 =
        pushFactory.create(db, admin.getIdent(), testRepo, PushOneCommit.SUBJECT,
            PushOneCommit.FILE_NAME, "push 2 content");
    PushOneCommit.Result r2 = push2.to("refs/for/master");
    assertMergeable(r2.getChangeId(), false);
    // TODO(dborowitz): Test for other-branches.
  }

  @Test
  public void files() throws Exception {
    PushOneCommit.Result r = createChange();
    assertThat(Iterables.all(gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .files()
        .keySet(), new Predicate<String>() {
            @Override
            public boolean apply(String file) {
              return file.matches(FILE_NAME + '|' + Patch.COMMIT_MSG);
            }
         }))
      .isTrue();
  }

  @Test
  public void diff() throws Exception {
    PushOneCommit.Result r = createChange();
    DiffInfo diff = gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .file(FILE_NAME)
        .diff();
    assertThat(diff.metaA).isNull();
    assertThat(diff.metaB.lines).isEqualTo(1);
  }

  @Test
  public void content() throws Exception {
    PushOneCommit.Result r = createChange();
    BinaryResult bin = gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .file(FILE_NAME)
        .content();
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    bin.writeTo(os);
    String res = new String(os.toByteArray(), UTF_8);
    assertThat(res).isEqualTo(FILE_CONTENT);
  }

  private void assertMergeable(String id, boolean expected) throws Exception {
    MergeableInfo m = gApi.changes().id(id).current().mergeable();
    assertThat(m.mergeable).isEqualTo(expected);
    assertThat(m.submitType).isEqualTo(SubmitType.MERGE_IF_NECESSARY);
    assertThat(m.mergeableInto).isNull();
    ChangeInfo c = gApi.changes().id(id).info();
    assertThat(c.mergeable).isEqualTo(expected);
  }

  @Test
  public void drafts() throws Exception {
    PushOneCommit.Result r = createChange();
    DraftInput in = new DraftInput();
    in.line = 1;
    in.message = "nit: trailing whitespace";
    in.path = FILE_NAME;

    DraftApi draftApi = gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .createDraft(in);
    assertThat(draftApi
        .get()
        .message)
      .isEqualTo(in.message);
    assertThat(gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .draft(draftApi.get().id)
        .get()
        .message)
      .isEqualTo(in.message);
    assertThat(gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .drafts())
      .hasSize(1);

    in.message = "good catch!";
    assertThat(gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .draft(draftApi.get().id)
        .update(in)
        .message)
      .isEqualTo(in.message);

    assertThat(gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .draft(draftApi.get().id)
        .get()
        .author
        .email)
      .isEqualTo(admin.email);

    draftApi.delete();
    assertThat(gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .drafts())
      .isEmpty();
  }

  @Test
  public void comments() throws Exception {
    PushOneCommit.Result r = createChange();
    CommentInput in = new CommentInput();
    in.line = 1;
    in.message = "nit: trailing whitespace";
    in.path = FILE_NAME;
    ReviewInput reviewInput = new ReviewInput();
    Map<String, List<CommentInput>> comments = new HashMap<>();
    comments.put(FILE_NAME, Collections.singletonList(in));
    reviewInput.comments = comments;
    reviewInput.message = "comment test";
    gApi.changes()
       .id(r.getChangeId())
       .current()
       .review(reviewInput);

    Map<String, List<CommentInfo>> out = gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .comments();
    assertThat(out).hasSize(1);
    CommentInfo comment = Iterables.getOnlyElement(out.get(FILE_NAME));
    assertThat(comment.message).isEqualTo(in.message);
    assertThat(comment.author.email).isEqualTo(admin.email);
    assertThat(comment.path).isNull();

    List<CommentInfo> list = gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .commentsAsList();
    assertThat(list).hasSize(1);

    CommentInfo comment2 = list.get(0);
    assertThat(comment2.path).isEqualTo(FILE_NAME);
    assertThat(comment2.line).isEqualTo(comment.line);
    assertThat(comment2.message).isEqualTo(comment.message);
    assertThat(comment2.author.email).isEqualTo(comment.author.email);

    assertThat(gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .comment(comment.id)
        .get()
        .message)
      .isEqualTo(in.message);
  }

  @Test
  public void patch() throws Exception {
    PushOneCommit.Result r = createChange();
    ChangeApi changeApi = gApi.changes()
        .id(r.getChangeId());
    BinaryResult bin = changeApi
        .revision(r.getCommit().name())
        .patch();
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    bin.writeTo(os);
    String res = new String(os.toByteArray(), UTF_8);
    ChangeInfo change = changeApi.get();
    RevisionInfo rev = change.revisions.get(change.currentRevision);
    DateFormat df = new SimpleDateFormat(
        "EEE, dd MMM yyyy HH:mm:ss Z",
        Locale.US);
    String date = df.format(rev.commit.author.date);
    assertThat(res).isEqualTo(
        String.format(PATCH, r.getCommitId().name(), date, r.getChangeId()));
  }

  private PushOneCommit.Result updateChange(PushOneCommit.Result r,
      String content) throws Exception {
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo,
        "test commit", "a.txt", content, r.getChangeId());
    return push.to("refs/for/master");
  }

  private PushOneCommit.Result createDraft() throws Exception {
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    return push.to("refs/drafts/master");
  }
}
