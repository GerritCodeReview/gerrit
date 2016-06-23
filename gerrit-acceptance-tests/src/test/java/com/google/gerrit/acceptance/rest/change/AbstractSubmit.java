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

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_REVISION;
import static com.google.gerrit.extensions.client.ListChangesOption.DETAILED_LABELS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.fail;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.change.Submit;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.gerrit.testutil.TestTimeUtil;
import com.google.inject.Inject;

import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

@NoHttpd
public abstract class AbstractSubmit extends AbstractDaemonTest {
  @ConfigSuite.Config
  public static Config submitWholeTopicEnabled() {
    return submitWholeTopicEnabledConfig();
  }

  @Inject
  private ApprovalsUtil approvalsUtil;

  @Inject
  private Submit submitHandler;

  private String systemTimeZone;

  @Before
  public void setTimeForTesting() {
    systemTimeZone = System.setProperty("user.timezone", "US/Eastern");
    TestTimeUtil.resetWithClockStep(1, SECONDS);
  }

  @After
  public void resetTime() {
    TestTimeUtil.useSystemTime();
    System.setProperty("user.timezone", systemTimeZone);
  }

  @After
  public void cleanup() {
    db.close();
  }

  protected abstract SubmitType getSubmitType();

  @Test
  @TestProjectInput(createEmptyCommit = false)
  public void submitToEmptyRepo() throws Exception {
    PushOneCommit.Result change = createChange();
    submit(change.getChangeId());
    assertThat(getRemoteHead().getId()).isEqualTo(change.getCommit());
  }

  @Test
  public void submitWholeTopic() throws Exception {
    assume().that(isSubmitWholeTopicEnabled()).isTrue();
    PushOneCommit.Result change1 =
        createChange("Change 1", "a.txt", "content", "test-topic");
    PushOneCommit.Result change2 =
        createChange("Change 2", "b.txt", "content", "test-topic");
    PushOneCommit.Result change3 =
        createChange("Change 3", "c.txt", "content", "test-topic");
    approve(change1.getChangeId());
    approve(change2.getChangeId());
    approve(change3.getChangeId());
    submit(change3.getChangeId());
    change1.assertChange(Change.Status.MERGED, name("test-topic"), admin);
    change2.assertChange(Change.Status.MERGED, name("test-topic"), admin);
    change3.assertChange(Change.Status.MERGED, name("test-topic"), admin);
    // Check for the exact change to have the correct submitter.
    assertSubmitter(change3);
    // Also check submitters for changes submitted via the topic relationship.
    assertSubmitter(change1);
    assertSubmitter(change2);
  }

  @Test
  public void submitDraftChange() throws Exception {
    PushOneCommit.Result draft = createDraftChange();
    Change.Id num = draft.getChange().getId();
    submitWithConflict(draft.getChangeId(),
        "Failed to submit 1 change due to the following problems:\n"
        + "Change " + num + ": Change " + num + " is draft");
  }

  @Test
  public void submitDraftPatchSet() throws Exception {
    PushOneCommit.Result change = createChange();
    PushOneCommit.Result draft = amendChangeAsDraft(change.getChangeId());
    Change.Id num = draft.getChange().getId();

    submitWithConflict(draft.getChangeId(),
        "Failed to submit 1 change due to the following problems:\n"
        + "Change " + num + ": submit rule error: "
        + "Cannot submit draft patch sets");
  }

  @Test
  public void submitWithHiddenBranchInSameTopic() throws Exception {
    assume().that(isSubmitWholeTopicEnabled()).isTrue();
    PushOneCommit.Result visible =
        createChange("refs/for/master/" + name("topic"));
    Change.Id num = visible.getChange().getId();

    createBranch(new Branch.NameKey(project, "hidden"));
    PushOneCommit.Result hidden =
        createChange("refs/for/hidden/" + name("topic"));
    approve(hidden.getChangeId());
    blockRead("refs/heads/hidden");

    submit(visible.getChangeId(), new SubmitInput(), AuthException.class,
        "A change to be submitted with " + num + " is not visible");
  }

  private void assertSubmitter(PushOneCommit.Result change) throws Exception {
    ChangeInfo info = get(change.getChangeId(), ListChangesOption.MESSAGES);
    assertThat(info.messages).isNotNull();
    Iterable<String> messages = Iterables.transform(info.messages,
        new Function<ChangeMessageInfo, String>() {
          @Override
          public String apply(ChangeMessageInfo in) {
            return in.message;
          }
        });
    assertThat(messages).hasSize(3);
    String last = Iterables.getLast(messages);
    if (getSubmitType() == SubmitType.CHERRY_PICK) {
      assertThat(last).startsWith(
          "Change has been successfully cherry-picked as ");
    } else {
      assertThat(last).isEqualTo(
          "Change has been successfully merged by Administrator");
    }
  }

  @Override
  protected void updateProjectInput(ProjectInput in) {
    in.submitType = getSubmitType();
    if (in.useContentMerge == InheritableBoolean.INHERIT) {
      in.useContentMerge = InheritableBoolean.FALSE;
    }
  }

  protected void submit(String changeId) throws Exception {
    submit(changeId, new SubmitInput(), null, null);
  }

  protected void submit(String changeId, SubmitInput input) throws Exception {
    submit(changeId, input, null, null);
  }

  protected void submitWithConflict(String changeId,
      String expectedError) throws Exception {
    submit(changeId, new SubmitInput(), ResourceConflictException.class,
        expectedError);
  }

  protected void submit(String changeId, SubmitInput input,
      Class<? extends RestApiException> expectedExceptionType,
      String expectedExceptionMsg) throws Exception {
    approve(changeId);
    if (expectedExceptionType == null) {
      assertSubmittable(changeId);
    }
    try {
      gApi.changes().id(changeId).current().submit(input);
      if (expectedExceptionType != null) {
        fail("Expected exception of type "
            + expectedExceptionType.getSimpleName());
      }
    } catch (RestApiException e) {
      if (expectedExceptionType == null) {
        throw e;
      }
      // More verbose than using assertThat and/or ExpectedException, but gives
      // us the stack trace.
      if (!expectedExceptionType.isAssignableFrom(e.getClass())
          || !e.getMessage().equals(expectedExceptionMsg)) {
        throw new AssertionError("Expected exception of type "
            + expectedExceptionType.getSimpleName() + " with message: \""
            + expectedExceptionMsg + "\" but got exception of type "
            + e.getClass().getSimpleName() + " with message \""
            + e.getMessage() + "\"", e);
      }
      return;
    }
    ChangeInfo change = gApi.changes().id(changeId).info();
    assertMerged(change.changeId);
  }

  protected void assertSubmittable(String changeId) throws Exception {
    assertThat(gApi.changes().id(changeId).info().submittable)
        .named("submit bit on ChangeInfo")
        .isEqualTo(true);
    RevisionResource rsrc = parseCurrentRevisionResource(changeId);
    UiAction.Description desc = submitHandler.getDescription(rsrc);
    assertThat(desc.isVisible()).named("visible bit on submit action").isTrue();
    assertThat(desc.isEnabled()).named("enabled bit on submit action").isTrue();
  }

  protected void assertChangeMergedEvents(String... expected) throws Exception {
    eventRecorder.assertChangeMergedEvents(
        project.get(), "refs/heads/master", expected);
  }

  protected void assertRefUpdatedEvents(RevCommit... expected)
      throws Exception {
    eventRecorder.assertRefUpdatedEvents(
        project.get(), "refs/heads/master", expected);
  }

  protected void assertCurrentRevision(String changeId, int expectedNum,
      ObjectId expectedId) throws Exception {
    ChangeInfo c = get(changeId, CURRENT_REVISION);
    assertThat(c.currentRevision).isEqualTo(expectedId.name());
    assertThat(c.revisions.get(expectedId.name())._number).isEqualTo(expectedNum);
    try (Repository repo =
        repoManager.openRepository(new Project.NameKey(c.project))) {
      String refName = new PatchSet.Id(new Change.Id(c._number), expectedNum)
          .toRefName();
      Ref ref = repo.exactRef(refName);
      assertThat(ref).named(refName).isNotNull();
      assertThat(ref.getObjectId()).isEqualTo(expectedId);
    }
  }

  protected void assertNew(String changeId) throws Exception {
    assertThat(get(changeId).status).isEqualTo(ChangeStatus.NEW);
  }

  protected void assertApproved(String changeId) throws Exception {
    ChangeInfo c = get(changeId, DETAILED_LABELS);
    LabelInfo cr = c.labels.get("Code-Review");
    assertThat(cr.all).hasSize(1);
    assertThat(cr.all.get(0).value).isEqualTo(2);
    assertThat(new Account.Id(cr.all.get(0)._accountId)).isEqualTo(admin.getId());
  }

  protected void assertMerged(String changeId) throws RestApiException {
    ChangeStatus status = gApi.changes().id(changeId).info().status;
    assertThat(status).isEqualTo(ChangeStatus.MERGED);
  }

  protected void assertPersonEquals(PersonIdent expected,
      PersonIdent actual) {
    assertThat(actual.getEmailAddress())
        .isEqualTo(expected.getEmailAddress());
    assertThat(actual.getName())
        .isEqualTo(expected.getName());
    assertThat(actual.getTimeZone())
        .isEqualTo(expected.getTimeZone());
  }

  protected void assertSubmitter(String changeId, int psId)
      throws Exception {
    Change c =
        getOnlyElement(queryProvider.get().byKeyPrefix(changeId)).change();
    ChangeNotes cn = notesFactory.createChecked(db, c);
    PatchSetApproval submitter = approvalsUtil.getSubmitter(
        db, cn, new PatchSet.Id(cn.getChangeId(), psId));
    assertThat(submitter).isNotNull();
    assertThat(submitter.isLegacySubmit()).isTrue();
    assertThat(submitter.getAccountId()).isEqualTo(admin.getId());
  }

  protected void assertNoSubmitter(String changeId, int psId)
      throws Exception {
    Change c =
        getOnlyElement(queryProvider.get().byKeyPrefix(changeId)).change();
    ChangeNotes cn = notesFactory.createChecked(db, c);
    PatchSetApproval submitter = approvalsUtil.getSubmitter(
        db, cn, new PatchSet.Id(cn.getChangeId(), psId));
    assertThat(submitter).isNull();
  }

  protected void assertCherryPick(TestRepository<?> testRepo,
      boolean contentMerge) throws Exception {
    assertRebase(testRepo, contentMerge);
    RevCommit remoteHead = getRemoteHead();
    assertThat(remoteHead.getFooterLines("Reviewed-On")).isNotEmpty();
    assertThat(remoteHead.getFooterLines("Reviewed-By")).isNotEmpty();
  }

  protected void assertRebase(TestRepository<?> testRepo, boolean contentMerge)
      throws Exception {
    Repository repo = testRepo.getRepository();
    RevCommit localHead = getHead(repo);
    RevCommit remoteHead = getRemoteHead();
    assert_().withFailureMessage(
        String.format("%s not equal %s", localHead.name(), remoteHead.name()))
          .that(localHead.getId()).isNotEqualTo(remoteHead.getId());
    assertThat(remoteHead.getParentCount()).isEqualTo(1);
    if (!contentMerge) {
      assertThat(getLatestRemoteDiff()).isEqualTo(getLatestDiff(repo));
    }
    assertThat(remoteHead.getShortMessage()).isEqualTo(localHead.getShortMessage());
  }

  protected List<RevCommit> getRemoteLog(Project.NameKey project, String branch)
      throws Exception {
    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      rw.markStart(rw.parseCommit(
          repo.exactRef("refs/heads/" + branch).getObjectId()));
      return Lists.newArrayList(rw);
    }
  }

  protected List<RevCommit> getRemoteLog() throws Exception {
    return getRemoteLog(project, "master");
  }

  private String getLatestDiff(Repository repo) throws Exception {
    ObjectId oldTreeId = repo.resolve("HEAD~1^{tree}");
    ObjectId newTreeId = repo.resolve("HEAD^{tree}");
    return getLatestDiff(repo, oldTreeId, newTreeId);
  }

  private String getLatestRemoteDiff() throws Exception {
    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      ObjectId oldTreeId = repo.resolve("refs/heads/master~1^{tree}");
      ObjectId newTreeId = repo.resolve("refs/heads/master^{tree}");
      return getLatestDiff(repo, oldTreeId, newTreeId);
    }
  }

  private String getLatestDiff(Repository repo, ObjectId oldTreeId,
      ObjectId newTreeId) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (DiffFormatter fmt = new DiffFormatter(out)) {
      fmt.setRepository(repo);
      fmt.format(oldTreeId, newTreeId);
      fmt.flush();
      return out.toString();
    }
  }
}
