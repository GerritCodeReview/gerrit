// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.change;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.server.change.ApprovalCopierIT.ApprovalDataSubject.assertThat;
import static com.google.gerrit.acceptance.server.change.ApprovalCopierIT.ApprovalDataSubject.assertThatList;
import static com.google.gerrit.acceptance.server.change.ApprovalCopierIT.ApprovalDataSubject.hasTestId;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.testing.TestLabels.labelBuilder;
import static com.google.gerrit.server.project.testing.TestLabels.value;
import static com.google.gerrit.truth.ListSubject.elements;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Correspondence;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.StandardSubjectBuilder;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth8;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.approval.ApprovalCopier;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.truth.ListSubject;
import com.google.gerrit.truth.NullAwareCorrespondence;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests of the {@link ApprovalCopier} API.
 *
 * <p>This class doesn't verify the copy condition predicates, as they are already covered by {@code
 * StickyApprovalsIT}.
 */
@NoHttpd
public class ApprovalCopierIT extends AbstractDaemonTest {
  @Inject private ApprovalCopier approvalCopier;
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Before
  public void setup() throws Exception {
    // Add Verified label.
    try (ProjectConfigUpdate u = updateProject(project)) {
      LabelType.Builder verified =
          labelBuilder(
                  LabelId.VERIFIED, value(1, "Passes"), value(0, "No score"), value(-1, "Failed"))
              .setCopyCondition("is:MIN");
      u.getConfig().upsertLabelType(verified.build());
      u.save();
    }

    // Grant permissions to vote on the verified label.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(LabelId.VERIFIED)
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-1, 1))
        .update();
  }

  @Test
  public void forInitialPatchSet_noApprovals() throws Exception {
    ChangeData changeData = createChange().getChange();
    try (Repository repo = repoManager.openRepository(project);
        RevWalk revWalk = new RevWalk(repo)) {
      ApprovalCopier.Result approvalCopierResult =
          approvalCopier.forPatchSet(
              changeData.notes(), changeData.currentPatchSet(), revWalk, repo.getConfig());
      assertThat(approvalCopierResult.copiedApprovals()).isEmpty();
      assertThat(approvalCopierResult.outdatedApprovals()).isEmpty();
    }
  }

  @Test
  public void forInitialPatchSet_currentApprovals() throws Exception {
    PushOneCommit.Result r = createChange();

    // Add some current approvals.
    vote(r.getChangeId(), admin, LabelId.CODE_REVIEW, 2);
    vote(r.getChangeId(), admin, LabelId.VERIFIED, 1);
    vote(r.getChangeId(), user, LabelId.CODE_REVIEW, -1);
    vote(r.getChangeId(), user, LabelId.VERIFIED, -1);

    ApprovalCopier.Result approvalCopierResult =
        invokeApprovalCopierForCurrentPatchSet(
            r.getChange().getId(), /* expectedCurrentPatchSetNum= */ 1);
    assertThatList(approvalCopierResult.copiedApprovals()).isEmpty();
    assertThatList(approvalCopierResult.outdatedApprovals()).isEmpty();
  }

  @Test
  public void forPatchSet_noApprovals() throws Exception {
    PushOneCommit.Result r = createChange();
    amendChange(r.getChangeId(), "refs/for/master", admin, testRepo).assertOkStatus();
    ApprovalCopier.Result approvalCopierResult =
        invokeApprovalCopierForCurrentPatchSet(
            r.getChange().getId(), /* expectedCurrentPatchSetNum= */ 2);
    assertThatList(approvalCopierResult.copiedApprovals()).isEmpty();
    assertThatList(approvalCopierResult.outdatedApprovals()).isEmpty();
  }

  @Test
  public void forPatchSet_outdatedApprovals() throws Exception {
    PushOneCommit.Result r = createChange();
    PatchSet.Id patchSet1Id = r.getPatchSetId();

    // Add some approvals that are not copied.
    vote(r.getChangeId(), admin, LabelId.CODE_REVIEW, 2);
    vote(r.getChangeId(), user, LabelId.VERIFIED, 1);

    amendChange(r.getChangeId(), "refs/for/master", admin, testRepo).assertOkStatus();
    ApprovalCopier.Result approvalCopierResult =
        invokeApprovalCopierForCurrentPatchSet(
            r.getChange().getId(), /* expectedCurrentPatchSetNum= */ 2);
    assertThat(approvalCopierResult.copiedApprovals()).isEmpty();
    assertThat(approvalCopierResult.outdatedApprovals())
        .comparingElementsUsing(hasTestId())
        .containsExactly(
            PatchSetApprovalTestId.create(patchSet1Id, admin.id(), LabelId.CODE_REVIEW, 2),
            PatchSetApprovalTestId.create(patchSet1Id, user.id(), LabelId.VERIFIED, 1));

    ApprovalDataSubject codeReviewApprovalSubject =
        assertThat(approvalCopierResult.outdatedApprovals(), LabelId.CODE_REVIEW, admin.id());
    codeReviewApprovalSubject.hasPassingAtomsThat().isEmpty();
    codeReviewApprovalSubject
        .hasFailingAtomsThat()
        .containsExactly("changekind:NO_CHANGE", "changekind:TRIVIAL_REBASE", "is:MIN");

    ApprovalDataSubject verifiedApprovalSubject =
        assertThat(approvalCopierResult.outdatedApprovals(), LabelId.VERIFIED, user.id());
    verifiedApprovalSubject.hasPassingAtomsThat().isEmpty();
    verifiedApprovalSubject.hasFailingAtomsThat().containsExactly("is:MIN");
  }

  @Test
  public void forPatchSet_copiedApprovals() throws Exception {
    PushOneCommit.Result r = createChange();

    // Add some approvals that are copied.
    vote(r.getChangeId(), admin, LabelId.CODE_REVIEW, -2);
    vote(r.getChangeId(), user, LabelId.VERIFIED, -1);

    r = amendChange(r.getChangeId(), "refs/for/master", admin, testRepo);
    r.assertOkStatus();
    PatchSet.Id patchSet2Id = r.getPatchSetId();

    ApprovalCopier.Result approvalCopierResult =
        invokeApprovalCopierForCurrentPatchSet(
            r.getChange().getId(), /* expectedCurrentPatchSetNum= */ 2);
    assertThatList(approvalCopierResult.copiedApprovals())
        .comparingElementsUsing(hasTestId())
        .containsExactly(
            PatchSetApprovalTestId.create(patchSet2Id, admin.id(), LabelId.CODE_REVIEW, -2),
            PatchSetApprovalTestId.create(patchSet2Id, user.id(), LabelId.VERIFIED, -1));
    assertThatList(approvalCopierResult.outdatedApprovals()).isEmpty();

    ApprovalDataSubject codeReviewApprovalSubject =
        assertThat(approvalCopierResult.copiedApprovals(), LabelId.CODE_REVIEW, admin.id());
    codeReviewApprovalSubject.hasPassingAtomsThat().containsExactly("is:MIN");
    codeReviewApprovalSubject
        .hasFailingAtomsThat()
        .containsExactly("changekind:NO_CHANGE", "changekind:TRIVIAL_REBASE");

    ApprovalDataSubject verifiedApprovalSubject =
        assertThat(approvalCopierResult.copiedApprovals(), LabelId.VERIFIED, user.id());
    verifiedApprovalSubject.hasPassingAtomsThat().containsExactly("is:MIN");
    verifiedApprovalSubject.hasFailingAtomsThat().isEmpty();
  }

  @Test
  public void forPatchSet_currentApprovals() throws Exception {
    PushOneCommit.Result r = createChange();
    amendChange(r.getChangeId(), "refs/for/master", admin, testRepo).assertOkStatus();

    // Add some current approvals.
    vote(r.getChangeId(), admin, LabelId.CODE_REVIEW, 2);
    vote(r.getChangeId(), admin, LabelId.VERIFIED, 1);
    vote(r.getChangeId(), user, LabelId.CODE_REVIEW, -1);
    vote(r.getChangeId(), user, LabelId.VERIFIED, -1);

    ApprovalCopier.Result approvalCopierResult =
        invokeApprovalCopierForCurrentPatchSet(
            r.getChange().getId(), /* expectedCurrentPatchSetNum= */ 2);
    assertThatList(approvalCopierResult.copiedApprovals()).isEmpty();
    assertThatList(approvalCopierResult.outdatedApprovals()).isEmpty();
  }

  @Test
  public void forPatchSet_allKindOfApprovals() throws Exception {
    PushOneCommit.Result r = createChange();
    PatchSet.Id patchSet1Id = r.getPatchSetId();

    // Add some approvals that are copied.
    vote(r.getChangeId(), admin, LabelId.CODE_REVIEW, -2);
    vote(r.getChangeId(), user, LabelId.VERIFIED, -1);

    // Add some approvals that are not copied.
    vote(r.getChangeId(), user, LabelId.CODE_REVIEW, 1);
    vote(r.getChangeId(), admin, LabelId.VERIFIED, 1);

    r = amendChange(r.getChangeId(), "refs/for/master", admin, testRepo);
    r.assertOkStatus();
    PatchSet.Id patchSet2Id = r.getPatchSetId();

    // Add some current approvals.
    vote(r.getChangeId(), user, LabelId.CODE_REVIEW, -1);
    vote(r.getChangeId(), admin, LabelId.VERIFIED, -1);

    ApprovalCopier.Result approvalCopierResult =
        invokeApprovalCopierForCurrentPatchSet(
            r.getChange().getId(), /* expectedCurrentPatchSetNum= */ 2);
    assertThatList(approvalCopierResult.copiedApprovals())
        .comparingElementsUsing(hasTestId())
        .containsExactly(
            PatchSetApprovalTestId.create(patchSet2Id, admin.id(), LabelId.CODE_REVIEW, -2),
            PatchSetApprovalTestId.create(patchSet2Id, user.id(), LabelId.VERIFIED, -1));
    assertThatList(approvalCopierResult.outdatedApprovals())
        .comparingElementsUsing(hasTestId())
        .containsExactly(
            PatchSetApprovalTestId.create(patchSet1Id, user.id(), LabelId.CODE_REVIEW, 1),
            PatchSetApprovalTestId.create(patchSet1Id, admin.id(), LabelId.VERIFIED, 1));

    ApprovalDataSubject copiedCodeReviewApprovalSubject =
        assertThat(approvalCopierResult.copiedApprovals(), LabelId.CODE_REVIEW, admin.id());
    copiedCodeReviewApprovalSubject.hasPassingAtomsThat().containsExactly("is:MIN");
    copiedCodeReviewApprovalSubject
        .hasFailingAtomsThat()
        .containsExactly("changekind:NO_CHANGE", "changekind:TRIVIAL_REBASE");

    ApprovalDataSubject copiedVerifiedApprovalSubject =
        assertThat(approvalCopierResult.copiedApprovals(), LabelId.VERIFIED, user.id());
    copiedVerifiedApprovalSubject.hasPassingAtomsThat().containsExactly("is:MIN");
    copiedVerifiedApprovalSubject.hasFailingAtomsThat().isEmpty();

    ApprovalDataSubject outdatedCodeReviewApprovalSubject1 =
        assertThat(approvalCopierResult.outdatedApprovals(), LabelId.CODE_REVIEW, user.id());
    outdatedCodeReviewApprovalSubject1.hasPassingAtomsThat().isEmpty();
    outdatedCodeReviewApprovalSubject1
        .hasFailingAtomsThat()
        .containsExactly("changekind:NO_CHANGE", "changekind:TRIVIAL_REBASE", "is:MIN");

    ApprovalDataSubject outdatedVerifiedApprovalSubject1 =
        assertThat(approvalCopierResult.outdatedApprovals(), LabelId.VERIFIED, admin.id());
    outdatedVerifiedApprovalSubject1.hasPassingAtomsThat().isEmpty();
    outdatedVerifiedApprovalSubject1.hasFailingAtomsThat().containsExactly("is:MIN");
  }

  @Test
  public void forPatchSet_copiedApprovalOverriddenByCurrentApproval() throws Exception {
    PushOneCommit.Result r = createChange();

    // Add approval that is copied.
    vote(r.getChangeId(), admin, LabelId.CODE_REVIEW, -2);

    amendChange(r.getChangeId(), "refs/for/master", admin, testRepo).assertOkStatus();

    // Override the copied approval.
    vote(r.getChangeId(), admin, LabelId.CODE_REVIEW, 1);

    ApprovalCopier.Result approvalCopierResult =
        invokeApprovalCopierForCurrentPatchSet(
            r.getChange().getId(), /* expectedCurrentPatchSetNum= */ 2);
    assertThatList(approvalCopierResult.copiedApprovals()).isEmpty();
    assertThatList(approvalCopierResult.outdatedApprovals()).isEmpty();
  }

  @Test
  public void forPatchSet_approvalForNonExistingLabel() throws Exception {
    PushOneCommit.Result r = createChange();
    PatchSet.Id patchSet1Id = r.getPatchSetId();

    // Add approval that could be copied.
    vote(r.getChangeId(), admin, LabelId.CODE_REVIEW, -2);

    // Delete the Code-Review label (override it with an empty label definition).
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().upsertLabelType(labelBuilder(LabelId.CODE_REVIEW).build());
      u.save();
    }

    amendChange(r.getChangeId(), "refs/for/master", admin, testRepo).assertOkStatus();

    ApprovalCopier.Result approvalCopierResult =
        invokeApprovalCopierForCurrentPatchSet(
            r.getChange().getId(), /* expectedCurrentPatchSetNum= */ 2);
    assertThatList(approvalCopierResult.copiedApprovals()).isEmpty();
    assertThatList(approvalCopierResult.outdatedApprovals())
        .comparingElementsUsing(hasTestId())
        .containsExactly(
            PatchSetApprovalTestId.create(patchSet1Id, admin.id(), LabelId.CODE_REVIEW, -2));

    ApprovalDataSubject codeReviewApprovalSubject1 =
        assertThat(approvalCopierResult.outdatedApprovals(), LabelId.CODE_REVIEW, admin.id());
    codeReviewApprovalSubject1.hasPassingAtomsThat().isEmpty();
    codeReviewApprovalSubject1.hasFailingAtomsThat().isEmpty();
  }

  @Test
  public void forPatchSet_copyableZeroApproval() throws Exception {
    PushOneCommit.Result r = createChange();

    // Override the inherited Code-Review label to make all votes copyable, including zero votes.
    try (ProjectConfigUpdate u = updateProject(project)) {
      LabelType.Builder codeReview =
          labelBuilder(
                  LabelId.CODE_REVIEW,
                  value(2, "Looks good to me, approved"),
                  value(1, "Looks good to me, but someone else must approve"),
                  value(0, "No score"),
                  value(-1, "I would prefer this is not submitted as is"),
                  value(-2, "This shall not be submitted"))
              .setCopyCondition("is:ANY");
      u.getConfig().upsertLabelType(codeReview.build());
      u.save();
    }

    // Create a zero approval that is copyable, by adding an approval and removing it again.
    vote(r.getChangeId(), admin, LabelId.CODE_REVIEW, 2);
    vote(r.getChangeId(), admin, LabelId.CODE_REVIEW, 0);

    amendChange(r.getChangeId(), "refs/for/master", admin, testRepo).assertOkStatus();

    ApprovalCopier.Result approvalCopierResult =
        invokeApprovalCopierForCurrentPatchSet(
            r.getChange().getId(), /* expectedCurrentPatchSetNum= */ 2);
    assertThatList(approvalCopierResult.copiedApprovals()).isEmpty();
    assertThatList(approvalCopierResult.outdatedApprovals()).isEmpty();
  }

  @Test
  public void forPatchSet_nonCopyableZeroApproval() throws Exception {
    PushOneCommit.Result r = createChange();

    // Create a zero approval that is non-copyable, by adding an approval and removing it again.
    vote(r.getChangeId(), admin, LabelId.CODE_REVIEW, 2);
    vote(r.getChangeId(), admin, LabelId.CODE_REVIEW, 0);

    amendChange(r.getChangeId(), "refs/for/master", admin, testRepo).assertOkStatus();

    ApprovalCopier.Result approvalCopierResult =
        invokeApprovalCopierForCurrentPatchSet(
            r.getChange().getId(), /* expectedCurrentPatchSetNum= */ 2);
    assertThatList(approvalCopierResult.copiedApprovals()).isEmpty();
    assertThatList(approvalCopierResult.outdatedApprovals()).isEmpty();
  }

  @Test
  public void copiedFlagSetOnCopiedApprovals() throws Exception {
    PushOneCommit.Result r = createChange();

    // Add approvals that are copied.
    vote(r.getChangeId(), admin, LabelId.CODE_REVIEW, -2);
    vote(r.getChangeId(), user, LabelId.VERIFIED, -1);

    r = amendChange(r.getChangeId(), "refs/for/master", admin, testRepo);
    r.assertOkStatus();
    PatchSet.Id patchSet2Id = r.getPatchSetId();

    // Override copied approval.
    vote(r.getChangeId(), admin, LabelId.CODE_REVIEW, 1);

    // Add new current approval.
    vote(r.getChangeId(), admin, LabelId.VERIFIED, 1);

    ApprovalCopier.Result approvalCopierResult =
        invokeApprovalCopierForCurrentPatchSet(
            r.getChange().getId(), /* expectedCurrentPatchSetNum= */ 2);
    ImmutableSet<ApprovalCopier.Result.ApprovalData> copiedApprovals =
        approvalCopierResult.copiedApprovals();
    assertThatList(filter(copiedApprovals, approval -> approval.patchSetApproval().copied()))
        .comparingElementsUsing(hasTestId())
        .containsExactly(
            PatchSetApprovalTestId.create(patchSet2Id, user.id(), LabelId.VERIFIED, -1));
    assertThatList(filter(copiedApprovals, approval -> !approval.patchSetApproval().copied()))
        .isEmpty();
  }

  private void vote(String changeId, TestAccount testAccount, String label, int value)
      throws RestApiException {
    requestScopeOperations.setApiUser(testAccount.id());
    gApi.changes().id(changeId).current().review(new ReviewInput().label(label, value));
    requestScopeOperations.setApiUser(admin.id());
  }

  private ImmutableSet<ApprovalCopier.Result.ApprovalData> filter(
      Set<ApprovalCopier.Result.ApprovalData> approvals,
      Predicate<ApprovalCopier.Result.ApprovalData> filter) {
    return approvals.stream().filter(filter).collect(toImmutableSet());
  }

  private ApprovalCopier.Result invokeApprovalCopierForCurrentPatchSet(
      Change.Id changeId, int expectedCurrentPatchSetNum) throws IOException {
    ChangeData changeData = changeDataFactory.create(project, changeId);
    assertThat(changeData.currentPatchSet().id().get()).isEqualTo(expectedCurrentPatchSetNum);
    try (Repository repo = repoManager.openRepository(project);
        RevWalk revWalk = new RevWalk(repo)) {
      return approvalCopier.forPatchSet(
          changeData.notes(), changeData.currentPatchSet(), revWalk, repo.getConfig());
    }
  }

  public static class ApprovalDataSubject extends Subject {
    public static Correspondence<ApprovalCopier.Result.ApprovalData, PatchSetApprovalTestId>
        hasTestId() {
      return NullAwareCorrespondence.transforming(
          approvalData -> PatchSetApprovalTestId.create(approvalData.patchSetApproval()),
          "has test ID");
    }

    public static ApprovalDataSubject assertThat(ApprovalCopier.Result.ApprovalData approvalData) {
      return assertAbout(approvalDatas()).that(approvalData);
    }

    public static ApprovalDataSubject assertThat(
        ImmutableSet<ApprovalCopier.Result.ApprovalData> approvalDatas,
        String labelId,
        Account.Id accountId) {
      Optional<ApprovalCopier.Result.ApprovalData> approvalDataForLabelAndAccount =
          approvalDatas.stream()
              .filter(
                  approvalData ->
                      approvalData.patchSetApproval().label().equals(labelId)
                          && approvalData.patchSetApproval().accountId().equals(accountId))
              .findAny();
      Truth8.assertThat(approvalDataForLabelAndAccount).isPresent();
      return assertAbout(approvalDatas()).that(approvalDataForLabelAndAccount.get());
    }

    public static ListSubject<ApprovalDataSubject, ApprovalCopier.Result.ApprovalData>
        assertThatList(ImmutableSet<ApprovalCopier.Result.ApprovalData> approvalDatas) {
      return ListSubject.assertThat(approvalDatas.asList(), approvalDatas());
    }

    private static Factory<ApprovalDataSubject, ApprovalCopier.Result.ApprovalData>
        approvalDatas() {
      return ApprovalDataSubject::new;
    }

    private final ApprovalCopier.Result.ApprovalData approvalData;

    private ApprovalDataSubject(
        FailureMetadata metadata, ApprovalCopier.Result.ApprovalData approvalData) {
      super(metadata, approvalData);
      this.approvalData = approvalData;
    }

    public ListSubject<StringSubject, String> hasPassingAtomsThat() {
      return check("linesOfB()")
          .about(elements())
          .that(approvalData().passingAtoms().asList(), StandardSubjectBuilder::that);
    }

    public ListSubject<StringSubject, String> hasFailingAtomsThat() {
      return check("linesOfB()")
          .about(elements())
          .that(approvalData().failingAtoms().asList(), StandardSubjectBuilder::that);
    }

    private ApprovalCopier.Result.ApprovalData approvalData() {
      isNotNull();
      return approvalData;
    }
  }

  public static class PatchSetApprovalSubject extends Subject {
    public static PatchSetApprovalSubject assertThat(PatchSetApproval patchSetApproval) {
      return assertAbout(patchSetApprovals()).that(patchSetApproval);
    }

    private static Factory<PatchSetApprovalSubject, PatchSetApproval> patchSetApprovals() {
      return PatchSetApprovalSubject::new;
    }

    private PatchSetApprovalSubject(FailureMetadata metadata, PatchSetApproval patchSetApproval) {
      super(metadata, patchSetApproval);
    }
  }

  /**
   * AutoValue class that contains all properties of a PatchSetApproval that are relevant to do
   * assertions in tests (patch set ID, account ID, label name, voting value).
   */
  @AutoValue
  public abstract static class PatchSetApprovalTestId {
    public abstract PatchSet.Id patchSetId();

    public abstract Account.Id accountId();

    public abstract LabelId labelId();

    public abstract short value();

    public static PatchSetApprovalTestId create(PatchSetApproval patchSetApproval) {
      return new AutoValue_ApprovalCopierIT_PatchSetApprovalTestId(
          patchSetApproval.patchSetId(),
          patchSetApproval.accountId(),
          patchSetApproval.labelId(),
          patchSetApproval.value());
    }

    public static PatchSetApprovalTestId create(
        PatchSet.Id patchSetId, Account.Id accountId, String labelId, int value) {
      return new AutoValue_ApprovalCopierIT_PatchSetApprovalTestId(
          patchSetId, accountId, LabelId.create(labelId), (short) value);
    }
  }
}
