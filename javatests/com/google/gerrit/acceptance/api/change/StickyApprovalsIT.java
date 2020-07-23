// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.extensions.client.ChangeKind.MERGE_FIRST_PARENT_UPDATE;
import static com.google.gerrit.extensions.client.ChangeKind.NO_CHANGE;
import static com.google.gerrit.extensions.client.ChangeKind.NO_CODE_CHANGE;
import static com.google.gerrit.extensions.client.ChangeKind.REWORK;
import static com.google.gerrit.extensions.client.ChangeKind.TRIVIAL_REBASE;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_COMMIT;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_REVISION;
import static com.google.gerrit.extensions.client.ListChangesOption.DETAILED_LABELS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.testing.TestLabels.labelBuilder;
import static com.google.gerrit.server.project.testing.TestLabels.value;
import static org.eclipse.jgit.lib.Constants.HEAD;

import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.server.change.ChangeKindCacheImpl;
import com.google.gerrit.server.project.testing.TestLabels;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class StickyApprovalsIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Inject
  @Named("change_kind")
  private Cache<ChangeKindCacheImpl.Key, ChangeKind> changeKindCache;

  @Before
  public void setup() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      // Overwrite "Code-Review" label that is inherited from All-Projects.
      // This way changes to the "Code Review" label don't affect other tests.
      LabelType.Builder codeReview =
          labelBuilder(
              "Code-Review",
              value(2, "Looks good to me, approved"),
              value(1, "Looks good to me, but someone else must approve"),
              value(0, "No score"),
              value(-1, "I would prefer that you didn't submit this"),
              value(-2, "Do not submit"));
      codeReview.setCopyAllScoresIfNoChange(false);
      u.getConfig().upsertLabelType(codeReview.build());

      LabelType.Builder verified =
          labelBuilder("Verified", value(1, "Passes"), value(0, "No score"), value(-1, "Failed"));
      verified.setCopyAllScoresIfNoChange(false);
      u.getConfig().upsertLabelType(verified.build());

      u.save();
    }
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(TestLabels.codeReview().getName())
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-2, 2))
        .add(
            allowLabel(TestLabels.verified().getName())
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-1, 1))
        .update();
  }

  @Test
  public void notSticky() throws Exception {
    assertNotSticky(
        EnumSet.of(REWORK, TRIVIAL_REBASE, NO_CODE_CHANGE, MERGE_FIRST_PARENT_UPDATE, NO_CHANGE));
  }

  @Test
  public void stickyOnAnyScore() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateLabelType("Code-Review", b -> b.setCopyAnyScore(true));
      u.save();
    }

    for (ChangeKind changeKind :
        EnumSet.of(REWORK, TRIVIAL_REBASE, NO_CODE_CHANGE, MERGE_FIRST_PARENT_UPDATE, NO_CHANGE)) {
      testRepo.reset(projectOperations.project(project).getHead("master"));

      String changeId = createChange(changeKind);
      vote(admin, changeId, 2, 1);
      vote(user, changeId, 1, -1);

      updateChange(changeId, changeKind);
      ChangeInfo c = detailedChange(changeId);
      assertVotes(c, admin, 2, 0, changeKind);
      assertVotes(c, user, 1, 0, changeKind);
    }
  }

  @Test
  public void stickyOnMinScore() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateLabelType("Code-Review", b -> b.setCopyMinScore(true));
      u.save();
    }

    for (ChangeKind changeKind :
        EnumSet.of(REWORK, TRIVIAL_REBASE, NO_CODE_CHANGE, MERGE_FIRST_PARENT_UPDATE, NO_CHANGE)) {
      testRepo.reset(projectOperations.project(project).getHead("master"));

      String changeId = createChange(changeKind);
      vote(admin, changeId, -1, 1);
      vote(user, changeId, -2, -1);

      updateChange(changeId, changeKind);
      ChangeInfo c = detailedChange(changeId);
      assertVotes(c, admin, 0, 0, changeKind);
      assertVotes(c, user, -2, 0, changeKind);
    }
  }

  @Test
  public void stickyOnMaxScore() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateLabelType("Code-Review", b -> b.setCopyMaxScore(true));
      u.save();
    }

    for (ChangeKind changeKind :
        EnumSet.of(REWORK, TRIVIAL_REBASE, NO_CODE_CHANGE, MERGE_FIRST_PARENT_UPDATE, NO_CHANGE)) {
      testRepo.reset(projectOperations.project(project).getHead("master"));

      String changeId = createChange(changeKind);
      vote(admin, changeId, 2, 1);
      vote(user, changeId, 1, -1);

      updateChange(changeId, changeKind);
      ChangeInfo c = detailedChange(changeId);
      assertVotes(c, admin, 2, 0, changeKind);
      assertVotes(c, user, 0, 0, changeKind);
    }
  }

  @Test
  public void stickyOnCopyValues() throws Exception {
    TestAccount user2 = accountCreator.user2();

    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateLabelType(
              "Code-Review", b -> b.setCopyValues(ImmutableList.of((short) -1, (short) 1)));
      u.save();
    }

    for (ChangeKind changeKind :
        EnumSet.of(REWORK, TRIVIAL_REBASE, NO_CODE_CHANGE, MERGE_FIRST_PARENT_UPDATE, NO_CHANGE)) {
      testRepo.reset(projectOperations.project(project).getHead("master"));

      String changeId = createChange(changeKind);
      vote(admin, changeId, -1, 1);
      vote(user, changeId, -2, -1);
      vote(user2, changeId, 1, -1);

      updateChange(changeId, changeKind);
      ChangeInfo c = detailedChange(changeId);
      assertVotes(c, admin, -1, 0, changeKind);
      assertVotes(c, user, 0, 0, changeKind);
      assertVotes(c, user2, 1, 0, changeKind);
    }
  }

  @Test
  public void stickyOnTrivialRebase() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateLabelType("Code-Review", b -> b.setCopyAllScoresOnTrivialRebase(true));
      u.save();
    }

    String changeId = createChange(TRIVIAL_REBASE);
    vote(admin, changeId, 2, 1);
    vote(user, changeId, -2, -1);

    updateChange(changeId, NO_CHANGE);
    ChangeInfo c = detailedChange(changeId);
    assertVotes(c, admin, 2, 0, NO_CHANGE);
    assertVotes(c, user, -2, 0, NO_CHANGE);

    updateChange(changeId, TRIVIAL_REBASE);
    c = detailedChange(changeId);
    assertVotes(c, admin, 2, 0, TRIVIAL_REBASE);
    assertVotes(c, user, -2, 0, TRIVIAL_REBASE);

    assertNotSticky(EnumSet.of(REWORK, NO_CODE_CHANGE, MERGE_FIRST_PARENT_UPDATE));

    // check that votes are sticky when trivial rebase is done by cherry-pick
    testRepo.reset(projectOperations.project(project).getHead("master"));
    changeId = createChange().getChangeId();
    vote(admin, changeId, 2, 1);
    vote(user, changeId, -2, -1);

    String cherryPickChangeId = cherryPick(changeId, TRIVIAL_REBASE);
    c = detailedChange(cherryPickChangeId);
    assertVotes(c, admin, 2, 0);
    assertVotes(c, user, -2, 0);

    // check that votes are not sticky when rework is done by cherry-pick
    testRepo.reset(projectOperations.project(project).getHead("master"));
    changeId = createChange().getChangeId();
    vote(admin, changeId, 2, 1);
    vote(user, changeId, -2, -1);

    cherryPickChangeId = cherryPick(changeId, REWORK);
    c = detailedChange(cherryPickChangeId);
    assertVotes(c, admin, 0, 0);
    assertVotes(c, user, 0, 0);
  }

  @Test
  public void stickyOnNoCodeChange() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateLabelType("Verified", b -> b.setCopyAllScoresIfNoCodeChange(true));
      u.save();
    }

    String changeId = createChange(NO_CODE_CHANGE);
    vote(admin, changeId, 2, 1);
    vote(user, changeId, -2, -1);

    updateChange(changeId, NO_CHANGE);
    ChangeInfo c = detailedChange(changeId);
    assertVotes(c, admin, 0, 1, NO_CHANGE);
    assertVotes(c, user, 0, -1, NO_CHANGE);

    updateChange(changeId, NO_CODE_CHANGE);
    c = detailedChange(changeId);
    assertVotes(c, admin, 0, 1, NO_CODE_CHANGE);
    assertVotes(c, user, 0, -1, NO_CODE_CHANGE);

    assertNotSticky(EnumSet.of(REWORK, TRIVIAL_REBASE, MERGE_FIRST_PARENT_UPDATE));
  }

  @Test
  public void stickyOnMergeFirstParentUpdate() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateLabelType("Code-Review", b -> b.setCopyAllScoresOnMergeFirstParentUpdate(true));
      u.save();
    }

    String changeId = createChange(MERGE_FIRST_PARENT_UPDATE);
    vote(admin, changeId, 2, 1);
    vote(user, changeId, -2, -1);

    updateChange(changeId, NO_CHANGE);
    ChangeInfo c = detailedChange(changeId);
    assertVotes(c, admin, 2, 0, NO_CHANGE);
    assertVotes(c, user, -2, 0, NO_CHANGE);

    updateChange(changeId, MERGE_FIRST_PARENT_UPDATE);
    c = detailedChange(changeId);
    assertVotes(c, admin, 2, 0, MERGE_FIRST_PARENT_UPDATE);
    assertVotes(c, user, -2, 0, MERGE_FIRST_PARENT_UPDATE);

    assertNotSticky(EnumSet.of(REWORK, NO_CODE_CHANGE, TRIVIAL_REBASE));
  }

  @Test
  public void notStickyWithCopyOnNoChangeWhenSecondParentIsUpdated() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateLabelType("Code-Review", b -> b.setCopyAllScoresIfNoChange(true));
      u.save();
    }

    String changeId = createChangeForMergeCommit();
    vote(admin, changeId, 2, 1);
    vote(user, changeId, -2, -1);

    updateSecondParent(changeId);
    ChangeInfo c = detailedChange(changeId);
    assertVotes(c, admin, 0, 0, null);
    assertVotes(c, user, 0, 0, null);
  }

  @Test
  public void removedVotesNotSticky() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateLabelType("Code-Review", b -> b.setCopyAllScoresOnTrivialRebase(true));
      u.getConfig().updateLabelType("Verified", b -> b.setCopyAllScoresIfNoCodeChange(true));
      u.save();
    }

    for (ChangeKind changeKind :
        EnumSet.of(REWORK, TRIVIAL_REBASE, NO_CODE_CHANGE, MERGE_FIRST_PARENT_UPDATE, NO_CHANGE)) {
      testRepo.reset(projectOperations.project(project).getHead("master"));

      String changeId = createChange(changeKind);
      vote(admin, changeId, 2, 1);
      vote(user, changeId, -2, -1);

      // Remove votes by re-voting with 0
      vote(admin, changeId, 0, 0);
      vote(user, changeId, 0, 0);
      ChangeInfo c = detailedChange(changeId);
      assertVotes(c, admin, 0, 0, null);
      assertVotes(c, user, 0, 0, null);

      updateChange(changeId, changeKind);
      c = detailedChange(changeId);
      assertVotes(c, admin, 0, 0, changeKind);
      assertVotes(c, user, 0, 0, changeKind);
    }
  }

  @Test
  public void stickyAcrossMultiplePatchSets() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateLabelType("Code-Review", b -> b.setCopyMaxScore(true));
      u.getConfig().updateLabelType("Verified", b -> b.setCopyAllScoresIfNoCodeChange(true));
      u.save();
    }

    String changeId = createChange(REWORK);
    vote(admin, changeId, 2, 1);

    for (int i = 0; i < 5; i++) {
      updateChange(changeId, NO_CODE_CHANGE);
      ChangeInfo c = detailedChange(changeId);
      assertVotes(c, admin, 2, 1, NO_CODE_CHANGE);
    }

    updateChange(changeId, REWORK);
    ChangeInfo c = detailedChange(changeId);
    assertVotes(c, admin, 2, 0, REWORK);
  }

  @Test
  public void stickyAcrossMultiplePatchSetsDoNotRegressPerformance() throws Exception {
    // The purpose of this test is to make sure that we compute change kind only against the parent
    // patch set. Change kind is a heavy operation. In prior version of Gerrit, we computed the
    // change kind against all prior patch sets. This is a regression that made Gerrit do expensive
    // work in O(num-patch-sets). This test ensures that we aren't regressing.
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateLabelType("Code-Review", b -> b.setCopyMaxScore(true));
      u.getConfig().updateLabelType("Verified", b -> b.setCopyAllScoresIfNoCodeChange(true));
      u.save();
    }

    String changeId = createChange(REWORK);
    vote(admin, changeId, 2, 1);
    updateChange(changeId, NO_CODE_CHANGE);
    updateChange(changeId, NO_CODE_CHANGE);
    updateChange(changeId, NO_CODE_CHANGE);

    Map<Integer, ObjectId> revisions = new HashMap<>();
    gApi.changes()
        .id(changeId)
        .get()
        .revisions
        .forEach(
            (revId, revisionInfo) ->
                revisions.put(revisionInfo._number, ObjectId.fromString(revId)));
    assertThat(revisions.size()).isEqualTo(4);
    assertChangeKindCacheContains(revisions.get(3), revisions.get(4));
    assertChangeKindCacheContains(revisions.get(2), revisions.get(3));
    assertChangeKindCacheContains(revisions.get(1), revisions.get(2));

    assertChangeKindCacheDoesNotContain(revisions.get(1), revisions.get(4));
    assertChangeKindCacheDoesNotContain(revisions.get(2), revisions.get(4));
    assertChangeKindCacheDoesNotContain(revisions.get(1), revisions.get(3));
  }

  @Test
  public void copyMinMaxAcrossMultiplePatchSets() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateLabelType("Code-Review", b -> b.setCopyMaxScore(true));
      u.getConfig().updateLabelType("Code-Review", b -> b.setCopyMinScore(true));
      u.save();
    }

    // Vote max score on PS1
    String changeId = createChange(REWORK);
    vote(admin, changeId, 2, 1);

    // Have someone else vote min score on PS2
    updateChange(changeId, REWORK);
    vote(user, changeId, -2, 0);
    ChangeInfo c = detailedChange(changeId);
    assertVotes(c, admin, 2, 0, REWORK);
    assertVotes(c, user, -2, 0, REWORK);

    // No vote changes on PS3
    updateChange(changeId, REWORK);
    c = detailedChange(changeId);
    assertVotes(c, admin, 2, 0, REWORK);
    assertVotes(c, user, -2, 0, REWORK);

    // Both users revote on PS4
    updateChange(changeId, REWORK);
    vote(admin, changeId, 1, 1);
    vote(user, changeId, 1, 1);
    c = detailedChange(changeId);
    assertVotes(c, admin, 1, 1, REWORK);
    assertVotes(c, user, 1, 1, REWORK);

    // New approvals shouldn't carry through to PS5
    updateChange(changeId, REWORK);
    c = detailedChange(changeId);
    assertVotes(c, admin, 0, 0, REWORK);
    assertVotes(c, user, 0, 0, REWORK);
  }

  @Test
  public void deleteStickyVote() throws Exception {
    String label = "Code-Review";
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateLabelType(label, b -> b.setCopyMaxScore(true));
      u.save();
    }

    // Vote max score on PS1
    String changeId = createChange(REWORK);
    vote(admin, changeId, label, 2);
    assertVotes(detailedChange(changeId), admin, label, 2, null);
    updateChange(changeId, REWORK);
    assertVotes(detailedChange(changeId), admin, label, 2, REWORK);

    // Delete vote that was copied via sticky approval
    deleteVote(admin, changeId, "Code-Review");
    assertVotes(detailedChange(changeId), admin, label, 0, REWORK);
  }

  private void assertChangeKindCacheContains(ObjectId prior, ObjectId next) {
    ChangeKind kind =
        changeKindCache.getIfPresent(ChangeKindCacheImpl.Key.create(prior, next, "recursive"));
    assertThat(kind).isNotNull();
  }

  private void assertChangeKindCacheDoesNotContain(ObjectId prior, ObjectId next) {
    ChangeKind kind =
        changeKindCache.getIfPresent(ChangeKindCacheImpl.Key.create(prior, next, "recursive"));
    assertThat(kind).isNull();
  }

  private ChangeInfo detailedChange(String changeId) throws Exception {
    return gApi.changes().id(changeId).get(DETAILED_LABELS, CURRENT_REVISION, CURRENT_COMMIT);
  }

  private void assertNotSticky(Set<ChangeKind> changeKinds) throws Exception {
    for (ChangeKind changeKind : changeKinds) {
      testRepo.reset(projectOperations.project(project).getHead("master"));

      String changeId = createChange(changeKind);
      vote(admin, changeId, +2, 1);
      vote(user, changeId, -2, -1);

      updateChange(changeId, changeKind);
      ChangeInfo c = detailedChange(changeId);
      assertVotes(c, admin, 0, 0, changeKind);
      assertVotes(c, user, 0, 0, changeKind);
    }
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
    assertThat(getChangeKind(changeId)).isEqualTo(NO_CODE_CHANGE);
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
    assertThat(getChangeKind(changeId)).isEqualTo(NO_CHANGE);
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
    assertThat(getChangeKind(changeId)).isEqualTo(REWORK);
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
    ReviewInput in = new ReviewInput().label("Code-Review", 2).label("Verified", 1);
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

    assertThat(getChangeKind(changeId)).isEqualTo(MERGE_FIRST_PARENT_UPDATE);
  }

  private void updateSecondParent(String changeId) throws Exception {
    ChangeInfo c = detailedChange(changeId);
    List<CommitInfo> parents = c.revisions.get(c.currentRevision).commit.parents;
    String parent1 = parents.get(0).commit;
    String parent2 = parents.get(1).commit;
    RevCommit commitParent1 = testRepo.getRevWalk().parseCommit(ObjectId.fromString(parent1));

    testRepo.reset(parent2);
    PushOneCommit.Result newParent2 = createChange("new parent 2", "p2-2.txt", "content 2-2");

    PushOneCommit merge = pushFactory.create(admin.newIdent(), testRepo, changeId);
    merge.setParents(ImmutableList.of(commitParent1, newParent2.getCommit()));
    PushOneCommit.Result result = merge.to("refs/for/master");
    result.assertOkStatus();

    assertThat(getChangeKind(changeId)).isEqualTo(REWORK);
  }

  private String cherryPick(String changeId, ChangeKind changeKind) throws Exception {
    switch (changeKind) {
      case REWORK:
      case TRIVIAL_REBASE:
        break;
      case NO_CODE_CHANGE:
      case NO_CHANGE:
      case MERGE_FIRST_PARENT_UPDATE:
      default:
        assertWithMessage("unexpected change kind: " + changeKind).fail();
    }

    testRepo.reset(projectOperations.project(project).getHead("master"));
    PushOneCommit.Result r =
        pushFactory
            .create(
                admin.newIdent(),
                testRepo,
                PushOneCommit.SUBJECT,
                "other.txt",
                "new content " + System.nanoTime())
            .to("refs/for/master");
    r.assertOkStatus();
    vote(admin, r.getChangeId(), 2, 1);
    merge(r);

    String subject =
        TRIVIAL_REBASE.equals(changeKind)
            ? PushOneCommit.SUBJECT
            : "Reworked change " + System.nanoTime();
    CherryPickInput in = new CherryPickInput();
    in.destination = "master";
    in.message = String.format("%s\n\nChange-Id: %s", subject, changeId);
    ChangeInfo c = gApi.changes().id(changeId).current().cherryPick(in).get();
    return c.changeId;
  }

  private ChangeKind getChangeKind(String changeId) throws Exception {
    ChangeInfo c = gApi.changes().id(changeId).get(CURRENT_REVISION);
    return c.revisions.get(c.currentRevision).kind;
  }

  private void vote(TestAccount user, String changeId, String label, int vote) throws Exception {
    requestScopeOperations.setApiUser(user.id());
    gApi.changes().id(changeId).current().review(new ReviewInput().label(label, vote));
  }

  private void vote(TestAccount user, String changeId, int codeReviewVote, int verifiedVote)
      throws Exception {
    requestScopeOperations.setApiUser(user.id());
    ReviewInput in =
        new ReviewInput().label("Code-Review", codeReviewVote).label("Verified", verifiedVote);
    gApi.changes().id(changeId).current().review(in);
  }

  private void deleteVote(TestAccount user, String changeId, String label) throws Exception {
    requestScopeOperations.setApiUser(user.id());
    gApi.changes().id(changeId).reviewer(user.id().toString()).deleteVote(label);
  }

  private void assertVotes(ChangeInfo c, TestAccount user, int codeReviewVote, int verifiedVote) {
    assertVotes(c, user, codeReviewVote, verifiedVote, null);
  }

  private void assertVotes(
      ChangeInfo c, TestAccount user, int codeReviewVote, int verifiedVote, ChangeKind changeKind) {
    assertVotes(c, user, "Code-Review", codeReviewVote, changeKind);
    assertVotes(c, user, "Verified", verifiedVote, changeKind);
  }

  private void assertVotes(
      ChangeInfo c, TestAccount user, String label, int expectedVote, ChangeKind changeKind) {
    Integer vote = 0;
    if (c.labels.get(label) != null && c.labels.get(label).all != null) {
      for (ApprovalInfo approval : c.labels.get(label).all) {
        if (approval._accountId == user.id().get()) {
          vote = approval.value;
          break;
        }
      }
    }

    String name = "label = " + label;
    if (changeKind != null) {
      name += "; changeKind = " + changeKind.name();
    }
    assertWithMessage(name).that(vote).isEqualTo(expectedVote);
  }
}
