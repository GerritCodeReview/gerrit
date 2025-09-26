// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.acceptance.testsuite.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.eclipse.jgit.lib.Constants.HEAD;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.inject.Inject;
import java.util.List;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;

/** Helper to create changes of a certain {@link ChangeKind}. */
public class ChangeKindCreator {
  private GerritApi gApi;
  private PushOneCommit.Factory pushFactory;
  private RequestScopeOperations requestScopeOperations;
  private ProjectOperations projectOperations;

  @Inject
  private ChangeKindCreator(
      GerritApi gApi,
      PushOneCommit.Factory pushFactory,
      RequestScopeOperations requestScopeOperations,
      ProjectOperations projectOperations) {
    this.gApi = gApi;
    this.pushFactory = pushFactory;
    this.requestScopeOperations = requestScopeOperations;
    this.projectOperations = projectOperations;
  }

  /** Creates a change with the given {@link ChangeKind} and returns the change id. */
  public String createChange(
      ChangeKind kind, TestRepository<InMemoryRepository> testRepo, TestAccount user)
      throws Exception {
    return switch (kind) {
      case NO_CODE_CHANGE, REWORK, TRIVIAL_REBASE, TRIVIAL_REBASE_WITH_MESSAGE_UPDATE, NO_CHANGE ->
          createChange(testRepo, user).getChangeId();
      case MERGE_FIRST_PARENT_UPDATE -> createChangeForMergeCommit(testRepo, user);
    };
  }

  /** Updates a change with the given {@link ChangeKind}. */
  public void updateChange(
      String changeId,
      ChangeKind changeKind,
      TestRepository<InMemoryRepository> testRepo,
      TestAccount user,
      Project.NameKey project)
      throws Exception {
    switch (changeKind) {
      case NO_CODE_CHANGE -> {
        noCodeChange(changeId, testRepo, user);
      }
      case REWORK -> {
        rework(changeId, testRepo, user);
      }
      case TRIVIAL_REBASE -> {
        trivialRebase(changeId, testRepo, user, project);
      }
      case MERGE_FIRST_PARENT_UPDATE -> {
        updateFirstParent(changeId, testRepo, user);
      }
      case NO_CHANGE -> {
        noChange(changeId, testRepo, user);
      }
      case TRIVIAL_REBASE_WITH_MESSAGE_UPDATE -> {
        // TODO: this case wasn't implemented yet when the default case was removed
        throw new UnsupportedOperationException("Unimplemented case: " + changeKind);
      }
    }
  }

  /**
   * Creates a cherry pick of the provided change with the given {@link ChangeKind} and returns the
   * change id.
   */
  public String cherryPick(
      String changeId,
      ChangeKind changeKind,
      TestRepository<InMemoryRepository> testRepo,
      TestAccount user,
      Project.NameKey project)
      throws Exception {
    switch (changeKind) {
      case REWORK, TRIVIAL_REBASE -> {}
      case NO_CODE_CHANGE,
          NO_CHANGE,
          MERGE_FIRST_PARENT_UPDATE,
          TRIVIAL_REBASE_WITH_MESSAGE_UPDATE -> {
        assertWithMessage("unexpected change kind: " + changeKind).fail();
      }
    }

    testRepo.reset(projectOperations.project(project).getHead("master"));
    PushOneCommit.Result r =
        pushFactory
            .create(
                user.newIdent(),
                testRepo,
                PushOneCommit.SUBJECT,
                "other.txt",
                "new content " + System.nanoTime())
            .to("refs/for/master");
    r.assertOkStatus();
    vote(user, r.getChangeId(), 2, 1);
    merge(r);

    String subject =
        ChangeKind.TRIVIAL_REBASE.equals(changeKind)
            ? PushOneCommit.SUBJECT
            : "Reworked change " + System.nanoTime();
    CherryPickInput in = new CherryPickInput();
    in.destination = "master";
    in.message = String.format("%s\n\nChange-Id: %s", subject, changeId);
    ChangeInfo c = gApi.changes().id(changeId).current().cherryPick(in).get();
    return c.changeId;
  }

  /** Creates a change that is a merge {@link ChangeKind} and returns the change id. */
  public String createChangeForMergeCommit(
      TestRepository<InMemoryRepository> testRepo, TestAccount user) throws Exception {
    ObjectId initial = testRepo.getRepository().exactRef(HEAD).getLeaf().getObjectId();

    PushOneCommit.Result parent1 = createChange("parent 1", "p1.txt", "content 1", testRepo, user);

    testRepo.reset(initial);
    PushOneCommit.Result parent2 = createChange("parent 2", "p2.txt", "content 2", testRepo, user);

    testRepo.reset(parent1.getCommit());

    PushOneCommit merge = pushFactory.create(user.newIdent(), testRepo);
    merge.setParents(ImmutableList.of(parent1.getCommit(), parent2.getCommit()));
    PushOneCommit.Result result = merge.to("refs/for/master");
    result.assertOkStatus();
    return result.getChangeId();
  }

  /** Update the first parent of a merge. */
  public void updateFirstParent(
      String changeId, TestRepository<InMemoryRepository> testRepo, TestAccount user)
      throws Exception {
    ChangeInfo c = detailedChange(changeId);
    List<CommitInfo> parents = c.revisions.get(c.currentRevision).commit.parents;
    String parent1 = parents.get(0).commit;
    String parent2 = parents.get(1).commit;
    RevCommit commitParent2 = testRepo.getRevWalk().parseCommit(ObjectId.fromString(parent2));

    testRepo.reset(parent1);
    PushOneCommit.Result newParent1 =
        createChange("new parent 1", "p1-1.txt", "content 1-1", testRepo, user);

    PushOneCommit merge = pushFactory.create(user.newIdent(), testRepo, changeId);
    merge.setParents(ImmutableList.of(newParent1.getCommit(), commitParent2));
    PushOneCommit.Result result = merge.to("refs/for/master");
    result.assertOkStatus();

    assertThat(getChangeKind(changeId)).isEqualTo(ChangeKind.MERGE_FIRST_PARENT_UPDATE);
  }

  /** Update the second parent of a merge. */
  public void updateSecondParent(
      String changeId, TestRepository<InMemoryRepository> testRepo, TestAccount user)
      throws Exception {
    ChangeInfo c = detailedChange(changeId);
    List<CommitInfo> parents = c.revisions.get(c.currentRevision).commit.parents;
    String parent1 = parents.get(0).commit;
    String parent2 = parents.get(1).commit;
    RevCommit commitParent1 = testRepo.getRevWalk().parseCommit(ObjectId.fromString(parent1));

    testRepo.reset(parent2);
    PushOneCommit.Result newParent2 =
        createChange("new parent 2", "p2-2.txt", "content 2-2", testRepo, user);

    PushOneCommit merge = pushFactory.create(user.newIdent(), testRepo, changeId);
    merge.setParents(ImmutableList.of(commitParent1, newParent2.getCommit()));
    PushOneCommit.Result result = merge.to("refs/for/master");
    result.assertOkStatus();

    assertThat(getChangeKind(changeId)).isEqualTo(ChangeKind.REWORK);
  }

  private void noCodeChange(
      String changeId, TestRepository<InMemoryRepository> testRepo, TestAccount user)
      throws Exception {
    TestRepository<?>.CommitBuilder commitBuilder =
        testRepo.amendRef("HEAD").insertChangeId(changeId.substring(1));
    commitBuilder
        .message("New subject " + System.nanoTime())
        .author(user.newIdent())
        .committer(new PersonIdent(user.newIdent(), testRepo.getInstant()));
    commitBuilder.create();
    GitUtil.pushHead(testRepo, "refs/for/master", false);
    assertThat(getChangeKind(changeId)).isEqualTo(ChangeKind.NO_CODE_CHANGE);
  }

  private void noChange(
      String changeId, TestRepository<InMemoryRepository> testRepo, TestAccount user)
      throws Exception {
    ChangeInfo change = gApi.changes().id(changeId).get();
    String commitMessage = change.revisions.get(change.currentRevision).commit.message;

    TestRepository<?>.CommitBuilder commitBuilder =
        testRepo.amendRef("HEAD").insertChangeId(changeId.substring(1));
    commitBuilder
        .message(commitMessage)
        .author(user.newIdent())
        .committer(new PersonIdent(user.newIdent(), testRepo.getInstant()));
    commitBuilder.create();
    GitUtil.pushHead(testRepo, "refs/for/master", false);
    assertThat(getChangeKind(changeId)).isEqualTo(ChangeKind.NO_CHANGE);
  }

  private void rework(
      String changeId, TestRepository<InMemoryRepository> testRepo, TestAccount user)
      throws Exception {
    PushOneCommit push =
        pushFactory.create(
            user.newIdent(),
            testRepo,
            PushOneCommit.SUBJECT,
            PushOneCommit.FILE_NAME,
            "new content " + System.nanoTime(),
            changeId);
    push.to("refs/for/master").assertOkStatus();
    assertThat(getChangeKind(changeId)).isEqualTo(ChangeKind.REWORK);
  }

  private void trivialRebase(
      String changeId,
      TestRepository<InMemoryRepository> testRepo,
      TestAccount user,
      Project.NameKey project)
      throws Exception {
    requestScopeOperations.setApiUser(user.id());
    testRepo.reset(projectOperations.project(project).getHead("master"));
    PushOneCommit push =
        pushFactory.create(
            user.newIdent(),
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
    assertThat(getChangeKind(changeId)).isEqualTo(ChangeKind.TRIVIAL_REBASE);
  }

  private ChangeKind getChangeKind(String changeId) throws Exception {
    ChangeInfo c = gApi.changes().id(changeId).get(ListChangesOption.CURRENT_REVISION);
    return c.revisions.get(c.currentRevision).kind;
  }

  private PushOneCommit.Result createChange(
      TestRepository<InMemoryRepository> testRepo, TestAccount user) throws Exception {
    PushOneCommit push = pushFactory.create(user.newIdent(), testRepo);
    PushOneCommit.Result result = push.to("refs/for/master");
    result.assertOkStatus();
    return result;
  }

  private ChangeInfo detailedChange(String changeId) throws Exception {
    return gApi.changes()
        .id(changeId)
        .get(
            ListChangesOption.DETAILED_LABELS,
            ListChangesOption.CURRENT_REVISION,
            ListChangesOption.CURRENT_COMMIT);
  }

  private PushOneCommit.Result createChange(
      String subject,
      String fileName,
      String content,
      TestRepository<InMemoryRepository> testRepo,
      TestAccount user)
      throws Exception {
    PushOneCommit push = pushFactory.create(user.newIdent(), testRepo, subject, fileName, content);
    return push.to("refs/for/master");
  }

  private void vote(TestAccount user, String changeId, int codeReviewVote, int verifiedVote)
      throws Exception {
    requestScopeOperations.setApiUser(user.id());
    ReviewInput in =
        new ReviewInput()
            .label(LabelId.CODE_REVIEW, codeReviewVote)
            .label(LabelId.VERIFIED, verifiedVote);
    gApi.changes().id(changeId).current().review(in);
  }

  private void merge(PushOneCommit.Result r) throws Exception {
    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(r.getChangeId()).current().submit();
  }
}
