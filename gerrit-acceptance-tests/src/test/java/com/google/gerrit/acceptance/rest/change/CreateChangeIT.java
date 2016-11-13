// Copyright (C) 2014 The Android Open Source Project
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
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.reviewdb.client.RefNames.changeMetaRef;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.eclipse.jgit.lib.Constants.SIGNED_OFF_BY_TAG;

import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.common.MergeInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.config.AnonymousCowardNameProvider;
import com.google.gerrit.server.git.ChangeAlreadyMergedException;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.gerrit.testutil.TestTimeUtil;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class CreateChangeIT extends AbstractDaemonTest {
  @ConfigSuite.Config
  public static Config allowDraftsDisabled() {
    return allowDraftsDisabledConfig();
  }

  @BeforeClass
  public static void setTimeForTesting() {
    TestTimeUtil.resetWithClockStep(1, SECONDS);
  }

  @AfterClass
  public static void restoreTime() {
    TestTimeUtil.useSystemTime();
  }

  @Test
  public void createEmptyChange_MissingBranch() throws Exception {
    ChangeInput ci = new ChangeInput();
    ci.project = project.get();
    assertCreateFails(ci, BadRequestException.class, "branch must be non-empty");
  }

  @Test
  public void createEmptyChange_MissingMessage() throws Exception {
    ChangeInput ci = new ChangeInput();
    ci.project = project.get();
    ci.branch = "master";
    assertCreateFails(ci, BadRequestException.class, "commit message must be non-empty");
  }

  @Test
  public void createEmptyChange_InvalidStatus() throws Exception {
    ChangeInput ci = newChangeInput(ChangeStatus.MERGED);
    assertCreateFails(ci, BadRequestException.class, "unsupported change status");
  }

  @Test
  public void createNewChange() throws Exception {
    assertCreateSucceeds(newChangeInput(ChangeStatus.NEW));
  }

  @Test
  public void createNewChangeSignedOffByFooter() throws Exception {
    assume().that(isAllowDrafts()).isTrue();
    setSignedOffByFooter();
    ChangeInfo info = assertCreateSucceeds(newChangeInput(ChangeStatus.NEW));
    String message = info.revisions.get(info.currentRevision).commit.message;
    assertThat(message)
        .contains(
            String.format(
                "%sAdministrator <%s>", SIGNED_OFF_BY_TAG, admin.getIdent().getEmailAddress()));
  }

  @Test
  public void createNewDraftChange() throws Exception {
    assume().that(isAllowDrafts()).isTrue();
    assertCreateSucceeds(newChangeInput(ChangeStatus.DRAFT));
  }

  @Test
  public void createNewDraftChangeNotAllowed() throws Exception {
    assume().that(isAllowDrafts()).isFalse();
    ChangeInput ci = newChangeInput(ChangeStatus.DRAFT);
    assertCreateFails(ci, MethodNotAllowedException.class, "draft workflow is disabled");
  }

  @Test
  public void noteDbCommit() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();

    ChangeInfo c = assertCreateSucceeds(newChangeInput(ChangeStatus.NEW));
    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      RevCommit commit =
          rw.parseCommit(repo.exactRef(changeMetaRef(new Change.Id(c._number))).getObjectId());

      assertThat(commit.getShortMessage()).isEqualTo("Create change");

      PersonIdent expectedAuthor =
          changeNoteUtil.newIdent(
              accountCache.get(admin.id).getAccount(),
              c.created,
              serverIdent.get(),
              AnonymousCowardNameProvider.DEFAULT);
      assertThat(commit.getAuthorIdent()).isEqualTo(expectedAuthor);

      assertThat(commit.getCommitterIdent())
          .isEqualTo(new PersonIdent(serverIdent.get(), c.created));
      assertThat(commit.getParentCount()).isEqualTo(0);
    }
  }

  @Test
  public void createMergeChange() throws Exception {
    changeInTwoBranches("branchA", "a.txt", "branchB", "b.txt");
    ChangeInput in = newMergeChangeInput("branchA", "branchB", "");
    assertCreateSucceeds(in);
  }

  @Test
  public void createMergeChange_Conflicts() throws Exception {
    changeInTwoBranches("branchA", "shared.txt", "branchB", "shared.txt");
    ChangeInput in = newMergeChangeInput("branchA", "branchB", "");
    assertCreateFails(in, RestApiException.class, "merge conflict");
  }

  @Test
  public void createMergeChange_Conflicts_Ours() throws Exception {
    changeInTwoBranches("branchA", "shared.txt", "branchB", "shared.txt");
    ChangeInput in = newMergeChangeInput("branchA", "branchB", "ours");
    assertCreateSucceeds(in);
  }

  @Test
  public void invalidSource() throws Exception {
    changeInTwoBranches("branchA", "a.txt", "branchB", "b.txt");
    ChangeInput in = newMergeChangeInput("branchA", "invalid", "");
    assertCreateFails(in, BadRequestException.class, "Cannot resolve 'invalid' to a commit");
  }

  @Test
  public void invalidStrategy() throws Exception {
    changeInTwoBranches("branchA", "a.txt", "branchB", "b.txt");
    ChangeInput in = newMergeChangeInput("branchA", "branchB", "octopus");
    assertCreateFails(in, BadRequestException.class, "invalid merge strategy: octopus");
  }

  @Test
  public void alreadyMerged() throws Exception {
    ObjectId c0 =
        testRepo
            .branch("HEAD")
            .commit()
            .insertChangeId()
            .message("first commit")
            .add("a.txt", "a contents ")
            .create();
    testRepo
        .git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/heads/master"))
        .call();

    testRepo
        .branch("HEAD")
        .commit()
        .insertChangeId()
        .message("second commit")
        .add("b.txt", "b contents ")
        .create();
    testRepo
        .git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/heads/master"))
        .call();

    ChangeInput in = newMergeChangeInput("master", c0.getName(), "");
    assertCreateFails(
        in, ChangeAlreadyMergedException.class, "'" + c0.getName() + "' has already been merged");
  }

  @Test
  public void onlyContentMerged() throws Exception {
    testRepo
        .branch("HEAD")
        .commit()
        .insertChangeId()
        .message("first commit")
        .add("a.txt", "a contents ")
        .create();
    testRepo
        .git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/heads/master"))
        .call();

    // create a change, and cherrypick into master
    PushOneCommit.Result cId = createChange();
    RevCommit commitId = cId.getCommit();
    CherryPickInput cpi = new CherryPickInput();
    cpi.destination = "master";
    cpi.message = "cherry pick the commit";
    ChangeApi orig = gApi.changes().id(cId.getChangeId());
    ChangeApi cherry = orig.current().cherryPick(cpi);
    cherry.current().review(ReviewInput.approve());
    cherry.current().submit();

    ObjectId remoteId = getRemoteHead();
    assertThat(remoteId).isNotEqualTo(commitId);

    ChangeInput in = newMergeChangeInput("master", commitId.getName(), "");
    assertCreateSucceeds(in);
  }

  private ChangeInput newChangeInput(ChangeStatus status) {
    ChangeInput in = new ChangeInput();
    in.project = project.get();
    in.branch = "master";
    in.subject = "Empty change";
    in.topic = "support-gerrit-workflow-in-browser";
    in.status = status;
    return in;
  }

  private ChangeInfo assertCreateSucceeds(ChangeInput in) throws Exception {
    ChangeInfo out = gApi.changes().create(in).get();
    assertThat(out.branch).isEqualTo(in.branch);
    assertThat(out.subject).isEqualTo(in.subject);
    assertThat(out.topic).isEqualTo(in.topic);
    assertThat(out.status).isEqualTo(in.status);
    assertThat(out.revisions).hasSize(1);
    assertThat(out.submitted).isNull();
    Boolean draft = Iterables.getOnlyElement(out.revisions.values()).draft;
    assertThat(booleanToDraftStatus(draft)).isEqualTo(in.status);
    return out;
  }

  private void assertCreateFails(
      ChangeInput in, Class<? extends RestApiException> errType, String errSubstring)
      throws Exception {
    exception.expect(errType);
    exception.expectMessage(errSubstring);
    gApi.changes().create(in);
  }

  private ChangeStatus booleanToDraftStatus(Boolean draft) {
    if (draft == null) {
      return ChangeStatus.NEW;
    }
    return draft ? ChangeStatus.DRAFT : ChangeStatus.NEW;
  }

  // TODO(davido): Expose setting of account preferences in the API
  private void setSignedOffByFooter() throws Exception {
    RestResponse r = adminRestSession.get("/accounts/" + admin.email + "/preferences");
    r.assertOK();
    GeneralPreferencesInfo i = newGson().fromJson(r.getReader(), GeneralPreferencesInfo.class);
    i.signedOffBy = true;

    r = adminRestSession.put("/accounts/" + admin.email + "/preferences", i);
    r.assertOK();
    GeneralPreferencesInfo o = newGson().fromJson(r.getReader(), GeneralPreferencesInfo.class);

    assertThat(o.signedOffBy).isTrue();
  }

  private ChangeInput newMergeChangeInput(String targetBranch, String sourceRef, String strategy) {
    // create a merge change from branchA to master in gerrit
    ChangeInput in = new ChangeInput();
    in.project = project.get();
    in.branch = targetBranch;
    in.subject = "merge " + sourceRef + " to " + targetBranch;
    in.status = ChangeStatus.NEW;
    MergeInput mergeInput = new MergeInput();
    mergeInput.source = sourceRef;
    in.merge = mergeInput;
    if (!Strings.isNullOrEmpty(strategy)) {
      in.merge.strategy = strategy;
    }
    return in;
  }

  private void changeInTwoBranches(String branchA, String fileA, String branchB, String fileB)
      throws Exception {
    // create a initial commit in master
    Result initialCommit =
        pushFactory
            .create(db, user.getIdent(), testRepo, "initial commit", "readme.txt", "initial commit")
            .to("refs/heads/master");
    initialCommit.assertOkStatus();

    // create two new branches
    createBranch(new Branch.NameKey(project, branchA));
    createBranch(new Branch.NameKey(project, branchB));

    // create a commit in branchA
    Result changeA =
        pushFactory
            .create(db, user.getIdent(), testRepo, "change A", fileA, "A content")
            .to("refs/heads/" + branchA);
    changeA.assertOkStatus();

    // create a commit in branchB
    PushOneCommit commitB =
        pushFactory.create(db, user.getIdent(), testRepo, "change B", fileB, "B content");
    commitB.setParent(initialCommit.getCommit());
    Result changeB = commitB.to("refs/heads/" + branchB);
    changeB.assertOkStatus();
  }
}
