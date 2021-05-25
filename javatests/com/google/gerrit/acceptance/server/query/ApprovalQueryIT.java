package com.google.gerrit.acceptance.server.query;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.extensions.client.ChangeKind.TRIVIAL_REBASE;
import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.server.query.approval.ApprovalContext;
import com.google.gerrit.server.query.approval.ApprovalQueryBuilder;
import com.google.inject.Inject;
import java.util.Date;
import java.util.List;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class ApprovalQueryIT extends AbstractDaemonTest {
  @Inject private ApprovalQueryBuilder queryBuilder;
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void magicValuePredicate() throws Exception {
    assertTrue(queryBuilder.parse("is:MAX").asMatchable().match(contextForCodeReviewLabel(2)));
    assertTrue(queryBuilder.parse("is:mAx").asMatchable().match(contextForCodeReviewLabel(2)));
    assertFalse(queryBuilder.parse("is:MAX").asMatchable().match(contextForCodeReviewLabel(-2)));
    assertFalse(queryBuilder.parse("is:MAX").asMatchable().match(contextForCodeReviewLabel(1)));
    assertFalse(queryBuilder.parse("is:MAX").asMatchable().match(contextForCodeReviewLabel(5000)));

    assertTrue(queryBuilder.parse("is:MIN").asMatchable().match(contextForCodeReviewLabel(-2)));
    assertTrue(queryBuilder.parse("is:mIn").asMatchable().match(contextForCodeReviewLabel(-2)));
    assertFalse(queryBuilder.parse("is:MIN").asMatchable().match(contextForCodeReviewLabel(2)));
    assertFalse(queryBuilder.parse("is:MIN").asMatchable().match(contextForCodeReviewLabel(-1)));
    assertFalse(queryBuilder.parse("is:MIN").asMatchable().match(contextForCodeReviewLabel(5000)));

    assertTrue(queryBuilder.parse("is:ANY").asMatchable().match(contextForCodeReviewLabel(-2)));
    assertTrue(queryBuilder.parse("is:ANY").asMatchable().match(contextForCodeReviewLabel(2)));
    assertTrue(queryBuilder.parse("is:aNy").asMatchable().match(contextForCodeReviewLabel(2)));
  }

  @Test
  public void changeKindPredicate_noCodeChange() throws Exception {
    String change = createChange(ChangeKind.NO_CODE_CHANGE);
    updateChange(change, ChangeKind.NO_CODE_CHANGE);
    PatchSet.Id ps1 = PatchSet.id(Change.id(gApi.changes().id(change).get()._number), 1);
    assertTrue(
        queryBuilder
            .parse("changekind:no-code-change")
            .asMatchable()
            .match(contextForCodeReviewLabel(-2, ps1)));

    updateChange(change, ChangeKind.TRIVIAL_REBASE);
    PatchSet.Id ps2 = PatchSet.id(Change.id(gApi.changes().id(change).get()._number), 2);
    assertFalse(
        queryBuilder
            .parse("changekind:no-code-change")
            .asMatchable()
            .match(contextForCodeReviewLabel(-2, ps2)));
  }

  @Test
  public void changeKindPredicate_trivialRebase() throws Exception {
    String change = createChange(ChangeKind.TRIVIAL_REBASE);
    updateChange(change, ChangeKind.TRIVIAL_REBASE);
    PatchSet.Id ps1 = PatchSet.id(Change.id(gApi.changes().id(change).get()._number), 1);
    assertTrue(
        queryBuilder
            .parse("changekind:trivial-rebase")
            .asMatchable()
            .match(contextForCodeReviewLabel(-2, ps1)));

    updateChange(change, ChangeKind.REWORK);
    PatchSet.Id ps2 = PatchSet.id(Change.id(gApi.changes().id(change).get()._number), 2);
    assertFalse(
        queryBuilder
            .parse("changekind:trivial-rebase")
            .asMatchable()
            .match(contextForCodeReviewLabel(-2, ps2)));
  }

  @Test
  public void changeKindPredicate_reworkAndNotRework() throws Exception {
    String change = createChange(ChangeKind.REWORK);
    updateChange(change, ChangeKind.REWORK);
    PatchSet.Id ps1 = PatchSet.id(Change.id(gApi.changes().id(change).get()._number), 1);
    assertTrue(
        queryBuilder
            .parse("changekind:rework")
            .asMatchable()
            .match(contextForCodeReviewLabel(-2, ps1)));

    updateChange(change, ChangeKind.REWORK);
    PatchSet.Id ps2 = PatchSet.id(Change.id(gApi.changes().id(change).get()._number), 2);
    assertFalse(
        queryBuilder
            .parse("-changekind:rework")
            .asMatchable()
            .match(contextForCodeReviewLabel(-2, ps2)));
  }

  private ApprovalContext contextForCodeReviewLabel(int value) {
    PatchSet.Id psId = PatchSet.id(Change.id(1), 1);
    return contextForCodeReviewLabel(value, psId);
  }

  private ApprovalContext contextForCodeReviewLabel(int value, PatchSet.Id psId) {
    PatchSetApproval approval =
        PatchSetApproval.builder()
            .postSubmit(false)
            .granted(new Date())
            .key(PatchSetApproval.key(psId, admin.id(), LabelId.create("Code-Review")))
            .value(value)
            .build();
    return ApprovalContext.create(project, approval, PatchSet.id(psId.changeId(), psId.get() + 1));
  }

  private String createChange(ChangeKind kind) throws Exception {
    switch (kind) {
      case NO_CODE_CHANGE:
      case REWORK:
      case TRIVIAL_REBASE:
      case NO_CHANGE:
        return createChange().getChangeId();
      case MERGE_FIRST_PARENT_UPDATE:
        return createChangeForMergeCommit();
      default:
        throw new IllegalStateException("unexpected change kind: " + kind);
    }
  }

  private void updateChange(String changeId, ChangeKind changeKind) throws Exception {
    switch (changeKind) {
      case NO_CODE_CHANGE:
        noCodeChange(changeId);
        return;
      case REWORK:
        rework(changeId);
        return;
      case TRIVIAL_REBASE:
        trivialRebase(changeId);
        return;
      case MERGE_FIRST_PARENT_UPDATE:
        updateFirstParent(changeId);
        return;
      case NO_CHANGE:
        noChange(changeId);
        return;
      default:
        assertWithMessage("unexpected change kind: " + changeKind).fail();
    }
  }

  private void noCodeChange(String changeId) throws Exception {
    TestRepository<?>.CommitBuilder commitBuilder =
        testRepo.amendRef("HEAD").insertChangeId(changeId.substring(1));
    commitBuilder
        .message("New subject " + System.nanoTime())
        .author(admin.newIdent())
        .committer(new PersonIdent(admin.newIdent(), testRepo.getDate()));
    commitBuilder.create();
    GitUtil.pushHead(testRepo, "refs/for/master", false);
    assertThat(getChangeKind(changeId)).isEqualTo(ChangeKind.NO_CODE_CHANGE);
  }

  private void noChange(String changeId) throws Exception {
    ChangeInfo change = gApi.changes().id(changeId).get();
    String commitMessage = change.revisions.get(change.currentRevision).commit.message;

    TestRepository<?>.CommitBuilder commitBuilder =
        testRepo.amendRef("HEAD").insertChangeId(changeId.substring(1));
    commitBuilder
        .message(commitMessage)
        .author(admin.newIdent())
        .committer(new PersonIdent(admin.newIdent(), testRepo.getDate()));
    commitBuilder.create();
    GitUtil.pushHead(testRepo, "refs/for/master", false);
    assertThat(getChangeKind(changeId)).isEqualTo(ChangeKind.NO_CHANGE);
  }

  private void rework(String changeId) throws Exception {
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            PushOneCommit.SUBJECT,
            PushOneCommit.FILE_NAME,
            "new content " + System.nanoTime(),
            changeId);
    push.to("refs/for/master").assertOkStatus();
    assertThat(getChangeKind(changeId)).isEqualTo(ChangeKind.REWORK);
  }

  private void trivialRebase(String changeId) throws Exception {
    requestScopeOperations.setApiUser(admin.id());
    testRepo.reset(projectOperations.project(project).getHead("master"));
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Other Change",
            "a" + System.nanoTime() + ".txt",
            PushOneCommit.FILE_CONTENT);
    PushOneCommit.Result r = push.to("refs/for/master");
    r.assertOkStatus();
    RevisionApi revision = gApi.changes().id(r.getChangeId()).current();
    ReviewInput in = new ReviewInput().label(LabelId.CODE_REVIEW, 2).label(LabelId.VERIFIED, 1);
    revision.review(in);
    revision.submit();

    gApi.changes().id(changeId).current().rebase();
    assertThat(getChangeKind(changeId)).isEqualTo(TRIVIAL_REBASE);
  }

  private String createChangeForMergeCommit() throws Exception {
    ObjectId initial = repo().exactRef(HEAD).getLeaf().getObjectId();

    PushOneCommit.Result parent1 = createChange("parent 1", "p1.txt", "content 1");

    testRepo.reset(initial);
    PushOneCommit.Result parent2 = createChange("parent 2", "p2.txt", "content 2");

    testRepo.reset(parent1.getCommit());

    PushOneCommit merge = pushFactory.create(admin.newIdent(), testRepo);
    merge.setParents(ImmutableList.of(parent1.getCommit(), parent2.getCommit()));
    PushOneCommit.Result result = merge.to("refs/for/master");
    result.assertOkStatus();
    return result.getChangeId();
  }

  private void updateFirstParent(String changeId) throws Exception {
    ChangeInfo c = detailedChange(changeId);
    List<CommitInfo> parents = c.revisions.get(c.currentRevision).commit.parents;
    String parent1 = parents.get(0).commit;
    String parent2 = parents.get(1).commit;
    RevCommit commitParent2 = testRepo.getRevWalk().parseCommit(ObjectId.fromString(parent2));

    testRepo.reset(parent1);
    PushOneCommit.Result newParent1 = createChange("new parent 1", "p1-1.txt", "content 1-1");

    PushOneCommit merge = pushFactory.create(admin.newIdent(), testRepo, changeId);
    merge.setParents(ImmutableList.of(newParent1.getCommit(), commitParent2));
    PushOneCommit.Result result = merge.to("refs/for/master");
    result.assertOkStatus();

    assertThat(getChangeKind(changeId)).isEqualTo(ChangeKind.MERGE_FIRST_PARENT_UPDATE);
  }

  private ChangeKind getChangeKind(String changeId) throws Exception {
    ChangeInfo c = gApi.changes().id(changeId).get(ListChangesOption.CURRENT_REVISION);
    return c.revisions.get(c.currentRevision).kind;
  }

  private ChangeInfo detailedChange(String changeId) throws Exception {
    return gApi.changes()
        .id(changeId)
        .get(
            ListChangesOption.DETAILED_LABELS,
            ListChangesOption.CURRENT_REVISION,
            ListChangesOption.CURRENT_COMMIT);
  }
}
