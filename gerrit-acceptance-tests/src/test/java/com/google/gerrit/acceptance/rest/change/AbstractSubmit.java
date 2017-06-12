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
import static com.google.gerrit.extensions.client.ListChangesOption.SUBMITTABLE;
import static com.google.gerrit.server.group.SystemGroupBackend.CHANGE_OWNER;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.fail;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BinaryResult;
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
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.Util;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.gerrit.testutil.TestTimeUtil;
import com.google.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public abstract class AbstractSubmit extends AbstractDaemonTest {
  @ConfigSuite.Config
  public static Config submitWholeTopicEnabled() {
    return submitWholeTopicEnabledConfig();
  }

  @Inject private ApprovalsUtil approvalsUtil;

  @Inject private Submit submitHandler;

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
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change = createChange();
    BinaryResult request = submitPreview(change.getChangeId());
    RevCommit headAfterSubmitPreview = getRemoteHead();
    assertThat(headAfterSubmitPreview).isEqualTo(initialHead);
    Map<Branch.NameKey, RevTree> actual = fetchFromBundles(request);
    assertThat(actual).hasSize(1);

    submit(change.getChangeId());
    assertThat(getRemoteHead().getId()).isEqualTo(change.getCommit());
    assertRevTrees(project, actual);
  }

  @Test
  public void submitSingleChange() throws Exception {
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change = createChange();
    BinaryResult request = submitPreview(change.getChangeId());
    RevCommit headAfterSubmit = getRemoteHead();
    assertThat(headAfterSubmit).isEqualTo(initialHead);
    assertRefUpdatedEvents();
    assertChangeMergedEvents();

    Map<Branch.NameKey, RevTree> actual = fetchFromBundles(request);

    if ((getSubmitType() == SubmitType.CHERRY_PICK)
        || (getSubmitType() == SubmitType.REBASE_ALWAYS)) {
      // The change is updated as well:
      assertThat(actual).hasSize(2);
    } else {
      assertThat(actual).hasSize(1);
    }

    submit(change.getChangeId());
    assertRevTrees(project, actual);
  }

  @Test
  public void submitMultipleChangesOtherMergeConflictPreview() throws Exception {
    RevCommit initialHead = getRemoteHead();

    PushOneCommit.Result change = createChange("Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit headAfterFirstSubmit = getRemoteHead();
    testRepo.reset(initialHead);
    PushOneCommit.Result change2 = createChange("Change 2", "a.txt", "other content");
    PushOneCommit.Result change3 = createChange("Change 3", "d", "d");
    PushOneCommit.Result change4 = createChange("Change 4", "e", "e");
    // change 2 is not approved, but we ignore labels
    approve(change3.getChangeId());
    BinaryResult request = null;
    String msg = null;
    try {
      request = submitPreview(change4.getChangeId());
    } catch (Exception e) {
      msg = e.getMessage();
    }

    if (getSubmitType() == SubmitType.CHERRY_PICK) {
      Map<Branch.NameKey, RevTree> s = fetchFromBundles(request);
      submit(change4.getChangeId());
      assertRevTrees(project, s);
    } else if (getSubmitType() == SubmitType.FAST_FORWARD_ONLY) {
      assertThat(msg)
          .isEqualTo(
              "Failed to submit 3 changes due to the following problems:\n"
                  + "Change "
                  + change2.getChange().getId()
                  + ": internal error: "
                  + "change not processed by merge strategy\n"
                  + "Change "
                  + change3.getChange().getId()
                  + ": internal error: "
                  + "change not processed by merge strategy\n"
                  + "Change "
                  + change4.getChange().getId()
                  + ": Project policy "
                  + "requires all submissions to be a fast-forward. Please "
                  + "rebase the change locally and upload again for review.");
      RevCommit headAfterSubmit = getRemoteHead();
      assertThat(headAfterSubmit).isEqualTo(headAfterFirstSubmit);
      assertRefUpdatedEvents(initialHead, headAfterFirstSubmit);
      assertChangeMergedEvents(change.getChangeId(), headAfterFirstSubmit.name());
    } else if ((getSubmitType() == SubmitType.REBASE_IF_NECESSARY)
        || (getSubmitType() == SubmitType.REBASE_ALWAYS)) {
      String change2hash = change2.getChange().currentPatchSet().getRevision().get();
      assertThat(msg)
          .isEqualTo(
              "Cannot rebase "
                  + change2hash
                  + ": The change could "
                  + "not be rebased due to a conflict during merge.");
      RevCommit headAfterSubmit = getRemoteHead();
      assertThat(headAfterSubmit).isEqualTo(headAfterFirstSubmit);
      assertRefUpdatedEvents(initialHead, headAfterFirstSubmit);
      assertChangeMergedEvents(change.getChangeId(), headAfterFirstSubmit.name());
    } else {
      assertThat(msg)
          .isEqualTo(
              "Failed to submit 3 changes due to the following problems:\n"
                  + "Change "
                  + change2.getChange().getId()
                  + ": Change could not be "
                  + "merged due to a path conflict. Please rebase the change "
                  + "locally and upload the rebased commit for review.\n"
                  + "Change "
                  + change3.getChange().getId()
                  + ": Change could not be "
                  + "merged due to a path conflict. Please rebase the change "
                  + "locally and upload the rebased commit for review.\n"
                  + "Change "
                  + change4.getChange().getId()
                  + ": Change could not be "
                  + "merged due to a path conflict. Please rebase the change "
                  + "locally and upload the rebased commit for review.");
      RevCommit headAfterSubmit = getRemoteHead();
      assertThat(headAfterSubmit).isEqualTo(headAfterFirstSubmit);
      assertRefUpdatedEvents(initialHead, headAfterFirstSubmit);
      assertChangeMergedEvents(change.getChangeId(), headAfterFirstSubmit.name());
    }
  }

  @Test
  public void submitMultipleChangesPreview() throws Exception {
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change2 = createChange("Change 2", "a.txt", "other content");
    PushOneCommit.Result change3 = createChange("Change 3", "d", "d");
    PushOneCommit.Result change4 = createChange("Change 4", "e", "e");
    // change 2 is not approved, but we ignore labels
    approve(change3.getChangeId());
    BinaryResult request = submitPreview(change4.getChangeId());

    Map<String, Map<String, Integer>> expected = new HashMap<>();
    expected.put(project.get(), new HashMap<String, Integer>());
    expected.get(project.get()).put("refs/heads/master", 3);
    Map<Branch.NameKey, RevTree> actual = fetchFromBundles(request);

    assertThat(actual).containsKey(new Branch.NameKey(project, "refs/heads/master"));
    if (getSubmitType() == SubmitType.CHERRY_PICK) {
      // CherryPick ignores dependencies, thus only change and destination
      // branch refs are modified.
      assertThat(actual).hasSize(2);
    } else if (getSubmitType() == SubmitType.REBASE_ALWAYS) {
      // RebaseAlways takes care of dependencies, therefore Change{2,3,4} and
      // destination branch will be modified.
      assertThat(actual).hasSize(4);
    } else {
      assertThat(actual).hasSize(1);
    }

    // check that the submit preview did not actually submit
    RevCommit headAfterSubmit = getRemoteHead();
    assertThat(headAfterSubmit).isEqualTo(initialHead);
    assertRefUpdatedEvents();
    assertChangeMergedEvents();

    // now check we actually have the same content:
    approve(change2.getChangeId());
    submit(change4.getChangeId());
    assertRevTrees(project, actual);
  }

  @Test
  public void submitNoPermission() throws Exception {
    // create project where submit is blocked
    Project.NameKey p = createProject("p");
    block(Permission.SUBMIT, REGISTERED_USERS, "refs/*", p);

    TestRepository<InMemoryRepository> repo = cloneProject(p, admin);
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), repo);
    PushOneCommit.Result result = push.to("refs/for/master");
    result.assertOkStatus();

    submit(result.getChangeId(), new SubmitInput(), AuthException.class, "submit not permitted");
  }

  @Test
  public void noSelfSubmit() throws Exception {
    // create project where submit is blocked for the change owner
    Project.NameKey p = createProject("p");
    ProjectConfig cfg = projectCache.checkedGet(p).getConfig();
    Util.block(cfg, Permission.SUBMIT, CHANGE_OWNER, "refs/*");
    Util.allow(cfg, Permission.SUBMIT, REGISTERED_USERS, "refs/heads/*");
    Util.allow(cfg, Permission.forLabel("Code-Review"), -2, +2, REGISTERED_USERS, "refs/*");
    saveProjectConfig(p, cfg);

    TestRepository<InMemoryRepository> repo = cloneProject(p, admin);
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), repo);
    PushOneCommit.Result result = push.to("refs/for/master");
    result.assertOkStatus();

    ChangeInfo change = gApi.changes().id(result.getChangeId()).get();
    assertThat(change.owner._accountId).isEqualTo(admin.id.get());

    submit(result.getChangeId(), new SubmitInput(), AuthException.class, "submit not permitted");

    setApiUser(user);
    submit(result.getChangeId());
  }

  @Test
  public void onlySelfSubmit() throws Exception {
    // create project where only the change owner can submit
    Project.NameKey p = createProject("p");
    ProjectConfig cfg = projectCache.checkedGet(p).getConfig();
    Util.block(cfg, Permission.SUBMIT, REGISTERED_USERS, "refs/*");
    Util.allow(cfg, Permission.SUBMIT, CHANGE_OWNER, "refs/*");
    Util.allow(cfg, Permission.forLabel("Code-Review"), -2, +2, REGISTERED_USERS, "refs/*");
    saveProjectConfig(p, cfg);

    TestRepository<InMemoryRepository> repo = cloneProject(p, admin);
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), repo);
    PushOneCommit.Result result = push.to("refs/for/master");
    result.assertOkStatus();

    ChangeInfo change = gApi.changes().id(result.getChangeId()).get();
    assertThat(change.owner._accountId).isEqualTo(admin.id.get());

    setApiUser(user);
    submit(result.getChangeId(), new SubmitInput(), AuthException.class, "submit not permitted");

    setApiUser(admin);
    submit(result.getChangeId());
  }

  @Test
  public void submitWholeTopic() throws Exception {
    assume().that(isSubmitWholeTopicEnabled()).isTrue();
    PushOneCommit.Result change1 = createChange("Change 1", "a.txt", "content", "test-topic");
    PushOneCommit.Result change2 = createChange("Change 2", "b.txt", "content", "test-topic");
    PushOneCommit.Result change3 = createChange("Change 3", "c.txt", "content", "test-topic");
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
    submitWithConflict(
        draft.getChangeId(),
        "Failed to submit 1 change due to the following problems:\n"
            + "Change "
            + num
            + ": Change "
            + num
            + " is draft");
  }

  @Test
  public void submitDraftPatchSet() throws Exception {
    PushOneCommit.Result change = createChange();
    PushOneCommit.Result draft = amendChangeAsDraft(change.getChangeId());
    Change.Id num = draft.getChange().getId();

    submitWithConflict(
        draft.getChangeId(),
        "Failed to submit 1 change due to the following problems:\n"
            + "Change "
            + num
            + ": submit rule error: "
            + "Cannot submit draft patch sets");
  }

  @Test
  public void submitWithHiddenBranchInSameTopic() throws Exception {
    assume().that(isSubmitWholeTopicEnabled()).isTrue();
    PushOneCommit.Result visible = createChange("refs/for/master/" + name("topic"));
    Change.Id num = visible.getChange().getId();

    createBranch(new Branch.NameKey(project, "hidden"));
    PushOneCommit.Result hidden = createChange("refs/for/hidden/" + name("topic"));
    approve(hidden.getChangeId());
    blockRead("refs/heads/hidden");

    submit(
        visible.getChangeId(),
        new SubmitInput(),
        AuthException.class,
        "A change to be submitted with " + num + " is not visible");
  }

  private void assertSubmitter(PushOneCommit.Result change) throws Exception {
    ChangeInfo info = get(change.getChangeId(), ListChangesOption.MESSAGES);
    assertThat(info.messages).isNotNull();
    Iterable<String> messages = Iterables.transform(info.messages, i -> i.message);
    assertThat(messages).hasSize(3);
    String last = Iterables.getLast(messages);
    if (getSubmitType() == SubmitType.CHERRY_PICK) {
      assertThat(last).startsWith("Change has been successfully cherry-picked as ");
    } else if (getSubmitType() == SubmitType.REBASE_ALWAYS) {
      assertThat(last).startsWith("Change has been successfully rebased as");
    } else {
      assertThat(last).isEqualTo("Change has been successfully merged by Administrator");
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

  protected void submitWithConflict(String changeId, String expectedError) throws Exception {
    submit(changeId, new SubmitInput(), ResourceConflictException.class, expectedError);
  }

  protected void submit(
      String changeId,
      SubmitInput input,
      Class<? extends RestApiException> expectedExceptionType,
      String expectedExceptionMsg)
      throws Exception {
    approve(changeId);
    if (expectedExceptionType == null) {
      assertSubmittable(changeId);
    }
    try {
      gApi.changes().id(changeId).current().submit(input);
      if (expectedExceptionType != null) {
        fail("Expected exception of type " + expectedExceptionType.getSimpleName());
      }
    } catch (RestApiException e) {
      if (expectedExceptionType == null) {
        throw e;
      }
      // More verbose than using assertThat and/or ExpectedException, but gives
      // us the stack trace.
      if (!expectedExceptionType.isAssignableFrom(e.getClass())
          || !e.getMessage().equals(expectedExceptionMsg)) {
        throw new AssertionError(
            "Expected exception of type "
                + expectedExceptionType.getSimpleName()
                + " with message: \""
                + expectedExceptionMsg
                + "\" but got exception of type "
                + e.getClass().getSimpleName()
                + " with message \""
                + e.getMessage()
                + "\"",
            e);
      }
      return;
    }
    ChangeInfo change = gApi.changes().id(changeId).info();
    assertMerged(change.changeId);
  }

  protected BinaryResult submitPreview(String changeId) throws Exception {
    return gApi.changes().id(changeId).current().submitPreview();
  }

  protected void assertSubmittable(String changeId) throws Exception {
    assertThat(get(changeId, SUBMITTABLE).submittable)
        .named("submit bit on ChangeInfo")
        .isEqualTo(true);
    RevisionResource rsrc = parseCurrentRevisionResource(changeId);
    UiAction.Description desc = submitHandler.getDescription(rsrc);
    assertThat(desc.isVisible()).named("visible bit on submit action").isTrue();
    assertThat(desc.isEnabled()).named("enabled bit on submit action").isTrue();
  }

  protected void assertChangeMergedEvents(String... expected) throws Exception {
    eventRecorder.assertChangeMergedEvents(project.get(), "refs/heads/master", expected);
  }

  protected void assertRefUpdatedEvents(RevCommit... expected) throws Exception {
    eventRecorder.assertRefUpdatedEvents(project.get(), "refs/heads/master", expected);
  }

  protected void assertCurrentRevision(String changeId, int expectedNum, ObjectId expectedId)
      throws Exception {
    ChangeInfo c = get(changeId, CURRENT_REVISION);
    assertThat(c.currentRevision).isEqualTo(expectedId.name());
    assertThat(c.revisions.get(expectedId.name())._number).isEqualTo(expectedNum);
    try (Repository repo = repoManager.openRepository(new Project.NameKey(c.project))) {
      String refName = new PatchSet.Id(new Change.Id(c._number), expectedNum).toRefName();
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

  protected void assertPersonEquals(PersonIdent expected, PersonIdent actual) {
    assertThat(actual.getEmailAddress()).isEqualTo(expected.getEmailAddress());
    assertThat(actual.getName()).isEqualTo(expected.getName());
    assertThat(actual.getTimeZone()).isEqualTo(expected.getTimeZone());
  }

  protected void assertSubmitter(String changeId, int psId) throws Exception {
    Change c = getOnlyElement(queryProvider.get().byKeyPrefix(changeId)).change();
    ChangeNotes cn = notesFactory.createChecked(db, c);
    PatchSetApproval submitter =
        approvalsUtil.getSubmitter(db, cn, new PatchSet.Id(cn.getChangeId(), psId));
    assertThat(submitter).isNotNull();
    assertThat(submitter.isLegacySubmit()).isTrue();
    assertThat(submitter.getAccountId()).isEqualTo(admin.getId());
  }

  protected void assertNoSubmitter(String changeId, int psId) throws Exception {
    Change c = getOnlyElement(queryProvider.get().byKeyPrefix(changeId)).change();
    ChangeNotes cn = notesFactory.createChecked(db, c);
    PatchSetApproval submitter =
        approvalsUtil.getSubmitter(db, cn, new PatchSet.Id(cn.getChangeId(), psId));
    assertThat(submitter).isNull();
  }

  protected void assertCherryPick(TestRepository<?> testRepo, boolean contentMerge)
      throws Exception {
    assertRebase(testRepo, contentMerge);
    RevCommit remoteHead = getRemoteHead();
    assertThat(remoteHead.getFooterLines("Reviewed-On")).isNotEmpty();
    assertThat(remoteHead.getFooterLines("Reviewed-By")).isNotEmpty();
  }

  protected void assertRebase(TestRepository<?> testRepo, boolean contentMerge) throws Exception {
    Repository repo = testRepo.getRepository();
    RevCommit localHead = getHead(repo);
    RevCommit remoteHead = getRemoteHead();
    assert_()
        .withFailureMessage(String.format("%s not equal %s", localHead.name(), remoteHead.name()))
        .that(localHead.getId())
        .isNotEqualTo(remoteHead.getId());
    assertThat(remoteHead.getParentCount()).isEqualTo(1);
    if (!contentMerge) {
      assertThat(getLatestRemoteDiff()).isEqualTo(getLatestDiff(repo));
    }
    assertThat(remoteHead.getShortMessage()).isEqualTo(localHead.getShortMessage());
  }

  protected List<RevCommit> getRemoteLog(Project.NameKey project, String branch) throws Exception {
    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      rw.markStart(rw.parseCommit(repo.exactRef("refs/heads/" + branch).getObjectId()));
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

  private String getLatestDiff(Repository repo, ObjectId oldTreeId, ObjectId newTreeId)
      throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (DiffFormatter fmt = new DiffFormatter(out)) {
      fmt.setRepository(repo);
      fmt.format(oldTreeId, newTreeId);
      fmt.flush();
      return out.toString();
    }
  }
}
