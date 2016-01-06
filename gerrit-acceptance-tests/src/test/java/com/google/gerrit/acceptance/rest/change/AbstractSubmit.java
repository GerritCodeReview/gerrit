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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_REVISION;
import static com.google.gerrit.extensions.client.ListChangesOption.DETAILED_LABELS;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.common.EventListener;
import com.google.gerrit.common.EventSource;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.api.projects.BranchInfo;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.data.RefUpdateAttribute;
import com.google.gerrit.server.events.ChangeMergedEvent;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.RefUpdatedEvent;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.gson.reflect.TypeToken;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.apache.http.HttpStatus;
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
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Sandboxed
public abstract class AbstractSubmit extends AbstractDaemonTest {
  @ConfigSuite.Config
  public static Config submitWholeTopicEnabled() {
    return submitWholeTopicEnabledConfig();
  }

  private Map<String, String> mergeResults;
  protected Multimap<String, RefUpdateAttribute> refUpdatedEvents;

  @Inject
  private ChangeNotes.Factory notesFactory;

  @Inject
  private ApprovalsUtil approvalsUtil;

  @Inject
  private IdentifiedUser.GenericFactory factory;

  @Inject
  EventSource source;

  @Before
  public void setUp() throws Exception {
    mergeResults = Maps.newHashMap();
    refUpdatedEvents = HashMultimap.create();
    CurrentUser listenerUser = factory.create(user.id);
    source.addEventListener(new EventListener() {

      @Override
      public void onEvent(Event event) {
        if (event instanceof ChangeMergedEvent) {
          ChangeMergedEvent changeMergedEvent = (ChangeMergedEvent) event;
          mergeResults.put(changeMergedEvent.change.number,
              changeMergedEvent.newRev);
        } else if (event instanceof RefUpdatedEvent) {
          RefUpdatedEvent e = (RefUpdatedEvent) event;
          RefUpdateAttribute r = e.refUpdate;
          refUpdatedEvents.put(r.project + "-" + r.refName, r);
        }
      }

    }, listenerUser);
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
    assertThat(getRemoteHead().getId()).isEqualTo(change.getCommitId());
  }

  @Test
  public void submitWholeTopicMultipleProjects() throws Exception {
    assume().that(isSubmitWholeTopicEnabled()).isTrue();
    String topic = "test-topic";

    // Create test projects
    TestRepository<?> repoA = createProjectWithPush(
        "project-a", null, getSubmitType());
    TestRepository<?> repoB = createProjectWithPush(
        "project-b", null, getSubmitType());

    // Create changes on project-a
    PushOneCommit.Result change1 =
        createChange(repoA, "master", "Change 1", "a.txt", "content", topic);
    PushOneCommit.Result change2 =
        createChange(repoA, "master", "Change 2", "b.txt", "content", topic);

    // Create changes on project-b
    PushOneCommit.Result change3 =
        createChange(repoB, "master", "Change 3", "a.txt", "content", topic);
    PushOneCommit.Result change4 =
        createChange(repoB, "master", "Change 4", "b.txt", "content", topic);

    approve(change1.getChangeId());
    approve(change2.getChangeId());
    approve(change3.getChangeId());
    approve(change4.getChangeId());
    submit(change4.getChangeId());

    String expectedTopic = name(topic);
    change1.assertChange(Change.Status.MERGED, expectedTopic, admin);
    change2.assertChange(Change.Status.MERGED, expectedTopic, admin);
    change3.assertChange(Change.Status.MERGED, expectedTopic, admin);
    change4.assertChange(Change.Status.MERGED, expectedTopic, admin);
  }

  @Test
  public void submitWholeTopicMultipleBranchesOnSameProject() throws Exception {
    assume().that(isSubmitWholeTopicEnabled()).isTrue();
    String topic = "test-topic";

    // Create test project
    String projectName = "project-a";
    TestRepository<?> repoA = createProjectWithPush(
        projectName, null, getSubmitType());

    RevCommit initialHead =
        getRemoteHead(new Project.NameKey(name(projectName)), "master");

    // Create the dev branch on the test project
    BranchInput in = new BranchInput();
    in.revision = initialHead.name();
    gApi.projects().name(name(projectName)).branch("dev").create(in);

    // Create changes on master
    PushOneCommit.Result change1 =
        createChange(repoA, "master", "Change 1", "a.txt", "content", topic);
    PushOneCommit.Result change2 =
        createChange(repoA, "master", "Change 2", "b.txt", "content", topic);

    // Create  changes on dev
    repoA.reset(initialHead);
    PushOneCommit.Result change3 =
        createChange(repoA, "dev", "Change 3", "a.txt", "content", topic);
    PushOneCommit.Result change4 =
        createChange(repoA, "dev", "Change 4", "b.txt", "content", topic);

    approve(change1.getChangeId());
    approve(change2.getChangeId());
    approve(change3.getChangeId());
    approve(change4.getChangeId());
    submit(change4.getChangeId());

    String expectedTopic = name(topic);
    change1.assertChange(Change.Status.MERGED, expectedTopic, admin);
    change2.assertChange(Change.Status.MERGED, expectedTopic, admin);
    change3.assertChange(Change.Status.MERGED, expectedTopic, admin);
    change4.assertChange(Change.Status.MERGED, expectedTopic, admin);
  }

  @Test
  public void submitWholeTopic() throws Exception {
    assume().that(isSubmitWholeTopicEnabled()).isTrue();
    String topic = "test-topic";
    PushOneCommit.Result change1 =
        createChange("Change 1", "a.txt", "content", topic);
    PushOneCommit.Result change2 =
        createChange("Change 2", "b.txt", "content", topic);
    PushOneCommit.Result change3 =
        createChange("Change 3", "c.txt", "content", topic);
    approve(change1.getChangeId());
    approve(change2.getChangeId());
    approve(change3.getChangeId());
    submit(change3.getChangeId());

    change1.assertChange(Change.Status.MERGED, topic, admin);
    change2.assertChange(Change.Status.MERGED, topic, admin);
    change3.assertChange(Change.Status.MERGED, topic, admin);
    // Check for the exact change to have the correct submitter.
    assertSubmitter(change3);
    // Also check submitters for changes submitted via the topic relationship.
    assertSubmitter(change1);
    assertSubmitter(change2);

    // Check that the repo has the expected commits
    List<RevCommit> log = getRemoteLog();
    List<String> commitsInRepo = Lists.transform(log,
        new Function<RevCommit, String>() {
          @Override
          public String apply(RevCommit input) {
            return input.getShortMessage();
          }
        });
    int expectedCommitCount = getSubmitType() == SubmitType.MERGE_ALWAYS
        ? 5 // initial commit + 3 commits + merge commit
        : 4; // initial commit + 3 commits
    assertThat(log).hasSize(expectedCommitCount);

    assertThat(commitsInRepo).containsAllOf(
        "Initial empty repository", "Change 1", "Change 2", "Change 3");
    if (getSubmitType() == SubmitType.MERGE_ALWAYS) {
      assertThat(commitsInRepo).contains(
          "Merge changes from topic '" + topic + "'");
    }
  }

  @Test
  public void submitChangeWhenParentOfOtherBranchTip() throws Exception {
    // Chain of two commits
    // Push both to topic-branch
    // Push the first commit for review and submit
    //
    // C2 -- tip of topic branch
    //  |
    // C1 -- pushed for review
    //  |
    // C0 -- Master
    //
    ProjectConfig config = projectCache.checkedGet(project).getConfig();
    config.getProject().setCreateNewChangeForAllNotInTarget(
        InheritableBoolean.TRUE);
    saveProjectConfig(project, config);

    PushOneCommit push1 = pushFactory.create(db, admin.getIdent(), testRepo,
        PushOneCommit.SUBJECT, "a.txt", "content");
    PushOneCommit.Result c1 = push1.to("refs/heads/topic");
    c1.assertOkStatus();
    PushOneCommit push2 = pushFactory.create(db, admin.getIdent(), testRepo,
        PushOneCommit.SUBJECT, "b.txt", "anotherContent");
    PushOneCommit.Result c2 = push2.to("refs/heads/topic");
    c2.assertOkStatus();

    PushOneCommit.Result change1 = push1.to("refs/for/master");
    change1.assertOkStatus();

    approve(change1.getChangeId());
    submit(change1.getChangeId());
  }

  @Test
  public void submitMergeOfNonChangeBranchTip() throws Exception {
    // Merge a branch with commits that have not been submitted as
    // changes.
    //
    // M  -- mergeCommit (pushed for review and submitted)
    // | \
    // |  S -- stable (pushed directly to refs/heads/stable)
    // | /
    // I   -- master
    //
    RevCommit master = getRemoteHead(project, "master");
    PushOneCommit stableTip = pushFactory.create(db, admin.getIdent(), testRepo,
        "Tip of branch stable", "stable.txt", "");
    PushOneCommit.Result stable = stableTip.to("refs/heads/stable");
    PushOneCommit mergeCommit = pushFactory.create(db, admin.getIdent(),
        testRepo, "The merge commit", "merge.txt", "");
    mergeCommit.setParents(ImmutableList.of(master, stable.getCommit()));
    PushOneCommit.Result mergeReview = mergeCommit.to("refs/for/master");
    approve(mergeReview.getChangeId());
    submit(mergeReview.getChangeId());

    List<RevCommit> log = getRemoteLog();
    assertThat(log).contains(stable.getCommit());
    assertThat(log).contains(mergeReview.getCommit());
  }

  @Test
  public void submitMergeOfNonChangeBranchNonTip() throws Exception {
    // Merge a branch with commits that have not been submitted as
    // changes.
    //
    // MC  -- merge commit (pushed for review and submitted)
    // |\   S2 -- new stable tip (pushed directly to refs/heads/stable)
    // M \ /
    // |  S1 -- stable (pushed directly to refs/heads/stable)
    // | /
    // I -- master
    //
    RevCommit initial = getRemoteHead(project, "master");
    // push directly to stable to S1
    PushOneCommit.Result s1 = pushFactory.create(
      db, admin.getIdent(), testRepo, "new commit into stable", "stable1.txt", "")
      .to("refs/heads/stable");
    // move the stable tip ahead to S2
    pushFactory.create(
      db, admin.getIdent(), testRepo, "Tip of branch stable", "stable2.txt", "")
      .to("refs/heads/stable");

    testRepo.reset(initial);

    // move the master ahead
    PushOneCommit.Result m = pushFactory.create(
      db, admin.getIdent(), testRepo, "Move master ahead", "master.txt", "")
      .to("refs/heads/master");

    // create merge change
    PushOneCommit mc =
      pushFactory.create(db, admin.getIdent(), testRepo, "The merge commit", "merge.txt", "");
    mc.setParents(ImmutableList.of(m.getCommit(), s1.getCommit()));
    PushOneCommit.Result mergeReview = mc.to("refs/for/master");
    approve(mergeReview.getChangeId());
    submit(mergeReview.getChangeId());

    List<RevCommit> log = getRemoteLog();
    assertThat(log).contains(s1.getCommit());
    assertThat(log).contains(mergeReview.getCommit());
  }

  private void assertSubmitter(PushOneCommit.Result change) throws Exception {
    ChangeInfo info = get(change.getChangeId(), ListChangesOption.MESSAGES);
    assertThat(info.messages).isNotNull();
    assertThat(info.messages).hasSize(3);
    if (getSubmitType() == SubmitType.CHERRY_PICK) {
      assertThat(Iterables.getLast(info.messages).message).startsWith(
          "Change has been successfully cherry-picked as ");
    } else {
      assertThat(Iterables.getLast(info.messages).message).isEqualTo(
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

  protected PushOneCommit.Result createChange(String subject,
      String fileName, String content) throws Exception {
    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(), testRepo, subject, fileName, content);
    return push.to("refs/for/master");
  }

  protected PushOneCommit.Result createChange(String subject,
      String fileName, String content, String topic)
          throws Exception {
    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(), testRepo, subject, fileName, content);
    return push.to("refs/for/master/" + topic);
  }

  protected PushOneCommit.Result createChange(TestRepository<?> repo,
      String branch, String subject, String fileName, String content,
      String topic) throws Exception {
    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(), repo, subject, fileName, content);
    return push.to("refs/for/" + branch + "/" + name(topic));
  }

  protected void submit(String changeId) throws Exception {
    submit(changeId, HttpStatus.SC_OK, null);
  }

  protected void submitWithConflict(String changeId,
      String expectedError) throws Exception {
    submit(changeId, HttpStatus.SC_CONFLICT, expectedError);
  }

  private void submit(String changeId, int expectedStatus, String msg)
      throws Exception {
    approve(changeId);
    SubmitInput subm = new SubmitInput();
    RestResponse r =
        adminSession.post("/changes/" + changeId + "/submit", subm);
    assertThat(r.getStatusCode()).isEqualTo(expectedStatus);
    if (expectedStatus == HttpStatus.SC_OK) {
      checkArgument(msg == null, "msg must be null for successful submits");
      ChangeInfo change =
          newGson().fromJson(r.getReader(),
              new TypeToken<ChangeInfo>() {}.getType());
      assertThat(change.status).isEqualTo(ChangeStatus.MERGED);

      checkMergeResult(change);
    } else {
      checkArgument(!Strings.isNullOrEmpty(msg), "msg must be a valid string " +
          "containing an error message for unsuccessful submits");
      assertThat(r.getEntityContent()).isEqualTo(msg);
    }
    r.consume();
  }

  private void checkMergeResult(ChangeInfo change) throws IOException {
    // Get the revision of the branch after the submit to compare with the
    // newRev of the ChangeMergedEvent.
    RestResponse b =
        adminSession.get("/projects/" + change.project + "/branches/"
            + change.branch);
    if (b.getStatusCode() == HttpStatus.SC_OK) {
      BranchInfo branch =
          newGson().fromJson(b.getReader(),
              new TypeToken<BranchInfo>() {}.getType());
      assertThat(mergeResults).isNotEmpty();
      String newRev = mergeResults.get(Integer.toString(change._number));
      assertThat(newRev).isNotNull();
      assertThat(branch.revision).isEqualTo(newRev);
    }
    b.consume();
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

  protected void assertMerged(PushOneCommit.Result change)
      throws RestApiException {
    String changeId = change.getChangeId();
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
      throws OrmException {
    ChangeNotes cn = notesFactory.create(
        getOnlyElement(queryProvider.get().byKeyPrefix(changeId)).change());
    PatchSetApproval submitter = approvalsUtil.getSubmitter(
        db, cn, new PatchSet.Id(cn.getChangeId(), psId));
    assertThat(submitter.isSubmit()).isTrue();
    assertThat(submitter.getAccountId()).isEqualTo(admin.getId());
  }

  protected void assertNoSubmitter(String changeId, int psId)
      throws OrmException {
    ChangeNotes cn = notesFactory.create(
        getOnlyElement(queryProvider.get().byKeyPrefix(changeId)).change());
    PatchSetApproval submitter = approvalsUtil.getSubmitter(
        db, cn, new PatchSet.Id(cn.getChangeId(), psId));
    assertThat(submitter).isNull();
  }

  protected void assertCherryPick(TestRepository<?> testRepo,
      boolean contentMerge) throws IOException {
    assertRebase(testRepo, contentMerge);
    RevCommit remoteHead = getRemoteHead();
    assertThat(remoteHead.getFooterLines("Reviewed-On")).isNotEmpty();
    assertThat(remoteHead.getFooterLines("Reviewed-By")).isNotEmpty();
  }

  protected void assertRebase(TestRepository<?> testRepo, boolean contentMerge)
      throws IOException {
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

  private RevCommit getHead(Repository repo) throws IOException {
    return getHead(repo, "HEAD");
  }

  protected RevCommit getRemoteHead(Project.NameKey project, String branch)
      throws IOException {
    try (Repository repo = repoManager.openRepository(project)) {
      return getHead(repo, "refs/heads/" + branch);
    }
  }

  protected RevCommit getRemoteHead()
      throws IOException {
    return getRemoteHead(project, "master");
  }


  protected List<RevCommit> getRemoteLog(Project.NameKey project, String branch)
      throws IOException {
    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      rw.markStart(rw.parseCommit(
          repo.exactRef("refs/heads/" + branch).getObjectId()));
      return Lists.newArrayList(rw);
    }
  }

  protected List<RevCommit> getRemoteLog() throws IOException {
    return getRemoteLog(project, "master");
  }

  protected RefUpdateAttribute getOneRefUpdate(String key) {
    Collection<RefUpdateAttribute> refUpdates = refUpdatedEvents.get(key);
    assertThat(refUpdates).hasSize(1);
    return refUpdates.iterator().next();
  }

  private RevCommit getHead(Repository repo, String name) throws IOException {
    try (RevWalk rw = new RevWalk(repo)) {
      return rw.parseCommit(repo.exactRef(name).getObjectId());
    }
  }

  private String getLatestDiff(Repository repo) throws IOException {
    ObjectId oldTreeId = repo.resolve("HEAD~1^{tree}");
    ObjectId newTreeId = repo.resolve("HEAD^{tree}");
    return getLatestDiff(repo, oldTreeId, newTreeId);
  }

  private String getLatestRemoteDiff() throws IOException {
    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      ObjectId oldTreeId = repo.resolve("refs/heads/master~1^{tree}");
      ObjectId newTreeId = repo.resolve("refs/heads/master^{tree}");
      return getLatestDiff(repo, oldTreeId, newTreeId);
    }
  }

  private String getLatestDiff(Repository repo, ObjectId oldTreeId,
      ObjectId newTreeId) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (DiffFormatter fmt = new DiffFormatter(out)) {
      fmt.setRepository(repo);
      fmt.format(oldTreeId, newTreeId);
      fmt.flush();
      return out.toString();
    }
  }
}
