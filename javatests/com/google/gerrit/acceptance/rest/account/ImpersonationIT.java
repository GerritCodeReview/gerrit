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

package com.google.gerrit.acceptance.rest.account;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.capabilityKey;
import static com.google.gerrit.extensions.client.ListChangesOption.MESSAGES;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.RobotComment;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.DraftHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput.RobotCommentInput;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.account.AccountControl;
import com.google.gerrit.server.project.testing.TestLabels;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ImpersonationIT extends AbstractDaemonTest {
  @Inject private AccountControl.Factory accountControlFactory;
  @Inject private ApprovalsUtil approvalsUtil;
  @Inject private ChangeMessagesUtil cmUtil;
  @Inject private CommentsUtil commentsUtil;
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  private RestSession anonRestSession;
  private TestAccount admin2;
  private GroupInfo newGroup;

  @Before
  public void setUp() throws Exception {
    anonRestSession = new RestSession(server, null);
    admin2 = accountCreator.admin2();
    GroupInput gi = new GroupInput();
    gi.name = name("New-Group");
    gi.members = ImmutableList.of(user.id().toString());
    newGroup = gApi.groups().create(gi).get();
  }

  @After
  public void tearDown() throws Exception {
    removeRunAs();
  }

  @Test
  public void voteOnBehalfOf() throws Exception {
    allowCodeReviewOnBehalfOf();
    PushOneCommit.Result r = createChange();
    RevisionApi revision = gApi.changes().id(r.getChangeId()).current();

    ReviewInput in = ReviewInput.recommend();
    in.onBehalfOf = user.id().toString();
    in.message = "Message on behalf of";
    revision.review(in);

    PatchSetApproval psa = Iterables.getOnlyElement(r.getChange().approvals().values());
    assertThat(psa.patchSetId().get()).isEqualTo(1);
    assertThat(psa.label()).isEqualTo("Code-Review");
    assertThat(psa.accountId()).isEqualTo(user.id());
    assertThat(psa.value()).isEqualTo(1);
    assertThat(psa.realAccountId()).isEqualTo(admin.id());

    ChangeData cd = r.getChange();
    ChangeMessage m = Iterables.getLast(cmUtil.byChange(cd.notes()));
    assertThat(m.getMessage()).endsWith(in.message);
    assertThat(m.getAuthor()).isEqualTo(user.id());
    assertThat(m.getRealAuthor()).isEqualTo(admin.id());
  }

  @Test
  public void voteOnBehalfOfRequiresLabel() throws Exception {
    allowCodeReviewOnBehalfOf();
    PushOneCommit.Result r = createChange();
    RevisionApi revision = gApi.changes().id(r.getChangeId()).current();

    ReviewInput in = new ReviewInput();
    in.onBehalfOf = user.id().toString();
    in.message = "Message on behalf of";

    AuthException thrown = assertThrows(AuthException.class, () -> revision.review(in));
    assertThat(thrown)
        .hasMessageThat()
        .contains("label required to post review on behalf of \"" + in.onBehalfOf + '"');
  }

  @Test
  @GerritConfig(name = "change.strictLabels", value = "true")
  public void voteOnBehalfOfInvalidLabel() throws Exception {
    allowCodeReviewOnBehalfOf();

    String changeId = createChange().getChangeId();
    ReviewInput in = new ReviewInput().label("Not-A-Label", 5);
    in.onBehalfOf = user.id().toString();

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class, () -> gApi.changes().id(changeId).current().review(in));
    assertThat(thrown).hasMessageThat().contains("label \"Not-A-Label\" is not a configured label");
  }

  @Test
  public void voteOnBehalfOfInvalidLabelIgnoredWithoutStrictLabels() throws Exception {
    allowCodeReviewOnBehalfOf();

    String changeId = createChange().getChangeId();
    ReviewInput in = new ReviewInput().label("Code-Review", 1).label("Not-A-Label", 5);
    in.onBehalfOf = user.id().toString();
    gApi.changes().id(changeId).current().review(in);

    assertThat(gApi.changes().id(changeId).get().labels).doesNotContainKey("Not-A-Label");
  }

  @Test
  public void voteOnBehalfOfLabelNotPermitted() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      LabelType verified = TestLabels.verified();
      u.getConfig().upsertLabelType(verified);
      u.save();
    }

    PushOneCommit.Result r = createChange();
    RevisionApi revision = gApi.changes().id(r.getChangeId()).current();

    ReviewInput in = new ReviewInput();
    in.onBehalfOf = user.id().toString();
    in.label("Verified", 1);

    AuthException thrown = assertThrows(AuthException.class, () -> revision.review(in));
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            "not permitted to modify label \"Verified\" on behalf of \"" + in.onBehalfOf + '"');
  }

  @Test
  public void voteOnBehalfOfWithComment() throws Exception {
    testVoteOnBehalfOfWithComment();
  }

  @Test
  public void voteOnBehalfOfWithCommentWritingJson() throws Exception {
    testVoteOnBehalfOfWithComment();
  }

  private void testVoteOnBehalfOfWithComment() throws Exception {
    allowCodeReviewOnBehalfOf();
    PushOneCommit.Result r = createChange();

    ReviewInput in = new ReviewInput();
    in.onBehalfOf = user.id().toString();
    in.label("Code-Review", 1);
    CommentInput ci = new CommentInput();
    ci.path = Patch.COMMIT_MSG;
    ci.side = Side.REVISION;
    ci.line = 1;
    ci.message = "message";
    in.comments = ImmutableMap.of(ci.path, ImmutableList.of(ci));
    gApi.changes().id(r.getChangeId()).current().review(in);

    PatchSetApproval psa = Iterables.getOnlyElement(r.getChange().approvals().values());
    assertThat(psa.patchSetId().get()).isEqualTo(1);
    assertThat(psa.label()).isEqualTo("Code-Review");
    assertThat(psa.accountId()).isEqualTo(user.id());
    assertThat(psa.value()).isEqualTo(1);
    assertThat(psa.realAccountId()).isEqualTo(admin.id());

    ChangeData cd = r.getChange();
    HumanComment c =
        Iterables.getOnlyElement(commentsUtil.publishedHumanCommentsByChange(cd.notes()));
    assertThat(c.message).isEqualTo(ci.message);
    assertThat(c.author.getId()).isEqualTo(user.id());
    assertThat(c.getRealAuthor().getId()).isEqualTo(admin.id());
  }

  @Test
  public void voteOnBehalfOfWithRobotComment() throws Exception {
    allowCodeReviewOnBehalfOf();
    PushOneCommit.Result r = createChange();

    ReviewInput in = new ReviewInput();
    in.onBehalfOf = user.id().toString();
    in.label("Code-Review", 1);
    RobotCommentInput ci = new RobotCommentInput();
    ci.robotId = "my-robot";
    ci.robotRunId = "abcd1234";
    ci.path = Patch.COMMIT_MSG;
    ci.side = Side.REVISION;
    ci.line = 1;
    ci.message = "message";
    in.robotComments = ImmutableMap.of(ci.path, ImmutableList.of(ci));
    gApi.changes().id(r.getChangeId()).current().review(in);

    ChangeData cd = r.getChange();
    RobotComment c = Iterables.getOnlyElement(commentsUtil.robotCommentsByChange(cd.notes()));
    assertThat(c.message).isEqualTo(ci.message);
    assertThat(c.robotId).isEqualTo(ci.robotId);
    assertThat(c.robotRunId).isEqualTo(ci.robotRunId);
    assertThat(c.author.getId()).isEqualTo(user.id());
    assertThat(c.getRealAuthor().getId()).isEqualTo(admin.id());
  }

  @Test
  public void voteOnBehalfOfCannotModifyDrafts() throws Exception {
    allowCodeReviewOnBehalfOf();
    PushOneCommit.Result r = createChange();

    requestScopeOperations.setApiUser(user.id());
    DraftInput di = new DraftInput();
    di.path = Patch.COMMIT_MSG;
    di.side = Side.REVISION;
    di.line = 1;
    di.message = "message";
    gApi.changes().id(r.getChangeId()).current().createDraft(di);

    requestScopeOperations.setApiUser(admin.id());
    ReviewInput in = new ReviewInput();
    in.onBehalfOf = user.id().toString();
    in.label("Code-Review", 1);
    in.drafts = DraftHandling.PUBLISH;

    AuthException thrown =
        assertThrows(
            AuthException.class, () -> gApi.changes().id(r.getChangeId()).current().review(in));
    assertThat(thrown).hasMessageThat().contains("not allowed to modify other user's drafts");
  }

  @Test
  public void voteOnBehalfOfMissingUser() throws Exception {
    allowCodeReviewOnBehalfOf();
    PushOneCommit.Result r = createChange();
    RevisionApi revision = gApi.changes().id(r.getChangeId()).current();

    ReviewInput in = new ReviewInput();
    in.onBehalfOf = "doesnotexist";
    in.label("Code-Review", 1);

    UnprocessableEntityException thrown =
        assertThrows(UnprocessableEntityException.class, () -> revision.review(in));
    assertThat(thrown).hasMessageThat().contains("not found");
    assertThat(thrown).hasMessageThat().contains("doesnotexist");
  }

  @Test
  public void voteOnBehalfOfFailsWhenUserCannotSeeDestinationRef() throws Exception {
    blockRead(newGroup);

    allowCodeReviewOnBehalfOf();
    PushOneCommit.Result r = createChange();
    RevisionApi revision = gApi.changes().id(r.getChangeId()).current();

    ReviewInput in = new ReviewInput();
    in.onBehalfOf = user.id().toString();
    in.label("Code-Review", 1);

    UnprocessableEntityException thrown =
        assertThrows(UnprocessableEntityException.class, () -> revision.review(in));
    assertThat(thrown)
        .hasMessageThat()
        .contains("on_behalf_of account " + user.id() + " cannot see change");
  }

  @GerritConfig(name = "accounts.visibility", value = "SAME_GROUP")
  @Test
  public void voteOnBehalfOfInvisibleUserNotAllowed() throws Exception {
    allowCodeReviewOnBehalfOf();
    requestScopeOperations.setApiUser(accountCreator.user2().id());
    assertThat(accountControlFactory.get().canSee(user.id())).isFalse();

    PushOneCommit.Result r = createChange();
    RevisionApi revision = gApi.changes().id(r.getChangeId()).current();

    ReviewInput in = new ReviewInput();
    in.onBehalfOf = user.id().toString();
    in.label("Code-Review", 1);

    UnprocessableEntityException thrown =
        assertThrows(UnprocessableEntityException.class, () -> revision.review(in));
    assertThat(thrown).hasMessageThat().contains("not found");
    assertThat(thrown).hasMessageThat().contains(in.onBehalfOf);
  }

  @Test
  public void submitOnBehalfOf() throws Exception {
    allowSubmitOnBehalfOf();
    PushOneCommit.Result r = createChange();
    String changeId = project.get() + "~master~" + r.getChangeId();
    gApi.changes().id(changeId).current().review(ReviewInput.approve());
    SubmitInput in = new SubmitInput();
    in.onBehalfOf = admin2.email();
    gApi.changes().id(changeId).current().submit(in);

    ChangeData cd = r.getChange();
    assertThat(cd.change().isMerged()).isTrue();
    PatchSetApproval submitter =
        approvalsUtil.getSubmitter(cd.notes(), cd.change().currentPatchSetId());
    assertThat(submitter.accountId()).isEqualTo(admin2.id());
    assertThat(submitter.realAccountId()).isEqualTo(admin.id());
  }

  @Test
  public void submitOnBehalfOfInvalidUser() throws Exception {
    allowSubmitOnBehalfOf();
    PushOneCommit.Result r = createChange();
    String changeId = project.get() + "~master~" + r.getChangeId();
    gApi.changes().id(changeId).current().review(ReviewInput.approve());
    SubmitInput in = new SubmitInput();
    in.onBehalfOf = "doesnotexist";
    UnprocessableEntityException thrown =
        assertThrows(
            UnprocessableEntityException.class,
            () -> gApi.changes().id(changeId).current().submit(in));
    assertThat(thrown).hasMessageThat().contains("not found");
    assertThat(thrown).hasMessageThat().contains("doesnotexist");
  }

  @Test
  public void submitOnBehalfOfNotPermitted() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id(project.get() + "~master~" + r.getChangeId())
        .current()
        .review(ReviewInput.approve());
    SubmitInput in = new SubmitInput();
    in.onBehalfOf = admin2.email();
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () ->
                gApi.changes()
                    .id(project.get() + "~master~" + r.getChangeId())
                    .current()
                    .submit(in));
    assertThat(thrown).hasMessageThat().contains("submit on behalf of other users not permitted");
  }

  @Test
  public void submitOnBehalfOfFailsWhenUserCannotSeeDestinationRef() throws Exception {
    blockRead(newGroup);

    allowSubmitOnBehalfOf();
    PushOneCommit.Result r = createChange();
    String changeId = project.get() + "~master~" + r.getChangeId();
    gApi.changes().id(changeId).current().review(ReviewInput.approve());
    SubmitInput in = new SubmitInput();
    in.onBehalfOf = user.email();
    UnprocessableEntityException thrown =
        assertThrows(
            UnprocessableEntityException.class,
            () -> gApi.changes().id(changeId).current().submit(in));
    assertThat(thrown)
        .hasMessageThat()
        .contains("on_behalf_of account " + user.id() + " cannot see change");
  }

  @GerritConfig(name = "accounts.visibility", value = "SAME_GROUP")
  @Test
  public void submitOnBehalfOfInvisibleUserNotAllowed() throws Exception {
    allowSubmitOnBehalfOf();
    requestScopeOperations.setApiUser(accountCreator.user2().id());
    assertThat(accountControlFactory.get().canSee(user.id())).isFalse();

    PushOneCommit.Result r = createChange();
    String changeId = project.get() + "~master~" + r.getChangeId();
    gApi.changes().id(changeId).current().review(ReviewInput.approve());
    SubmitInput in = new SubmitInput();
    in.onBehalfOf = user.email();
    UnprocessableEntityException thrown =
        assertThrows(
            UnprocessableEntityException.class,
            () -> gApi.changes().id(changeId).current().submit(in));
    assertThat(thrown).hasMessageThat().contains("not found");
    assertThat(thrown).hasMessageThat().contains(in.onBehalfOf);
  }

  @Test
  public void runAsValidUser() throws Exception {
    allowRunAs();
    RestResponse res = adminRestSession.getWithHeader("/accounts/self", runAsHeader(user.id()));
    res.assertOK();
    AccountInfo account = newGson().fromJson(res.getEntityContent(), AccountInfo.class);
    assertThat(account._accountId).isEqualTo(user.id().get());
  }

  @GerritConfig(name = "auth.enableRunAs", value = "false")
  @Test
  public void runAsDisabledByConfig() throws Exception {
    allowRunAs();
    RestResponse res = adminRestSession.getWithHeader("/changes/", runAsHeader(user.id()));
    res.assertForbidden();
    assertThat(res.getEntityContent())
        .isEqualTo("X-Gerrit-RunAs disabled by auth.enableRunAs = false");
  }

  @Test
  public void runAsNotPermitted() throws Exception {
    RestResponse res = adminRestSession.getWithHeader("/changes/", runAsHeader(user.id()));
    res.assertForbidden();
    assertThat(res.getEntityContent()).isEqualTo("not permitted to use X-Gerrit-RunAs");
  }

  @Test
  public void runAsNeverPermittedForAnonymousUsers() throws Exception {
    allowRunAs();
    RestResponse res = anonRestSession.getWithHeader("/changes/", runAsHeader(user.id()));
    res.assertForbidden();
    assertThat(res.getEntityContent()).isEqualTo("not permitted to use X-Gerrit-RunAs");
  }

  @Test
  public void runAsInvalidUser() throws Exception {
    allowRunAs();
    RestResponse res = adminRestSession.getWithHeader("/changes/", runAsHeader("doesnotexist"));
    res.assertForbidden();
    assertThat(res.getEntityContent()).isEqualTo("no account matches X-Gerrit-RunAs");
  }

  @Test
  public void voteUsingRunAsAvoidsRestrictionsOfOnBehalfOf() throws Exception {
    allowRunAs();
    PushOneCommit.Result r = createChange();

    requestScopeOperations.setApiUser(user.id());
    DraftInput di = new DraftInput();
    di.path = Patch.COMMIT_MSG;
    di.side = Side.REVISION;
    di.line = 1;
    di.message = "inline comment";
    gApi.changes().id(r.getChangeId()).current().createDraft(di);
    requestScopeOperations.setApiUser(admin.id());

    // Things that aren't allowed with on_behalf_of:
    //  - no labels.
    //  - publish other user's drafts.
    ReviewInput in = new ReviewInput();
    in.message = "message";
    in.drafts = DraftHandling.PUBLISH;
    RestResponse res =
        adminRestSession.postWithHeader(
            "/changes/" + r.getChangeId() + "/revisions/current/review",
            runAsHeader(user.id()),
            in);
    res.assertOK();

    ChangeMessageInfo m = Iterables.getLast(gApi.changes().id(r.getChangeId()).get().messages);
    assertThat(m.message).endsWith(in.message);
    assertThat(m.author._accountId).isEqualTo(user.id().get());

    CommentInfo c =
        Iterables.getOnlyElement(gApi.changes().id(r.getChangeId()).comments(false).get(di.path));
    assertThat(c.author._accountId).isEqualTo(user.id().get());
    assertThat(c.message).isEqualTo(di.message);

    requestScopeOperations.setApiUser(user.id());
    assertThat(gApi.changes().id(r.getChangeId()).drafts()).isEmpty();
  }

  @Test
  public void runAsWithOnBehalfOf() throws Exception {
    // - Has the same restrictions as on_behalf_of (e.g. requires labels).
    // - Takes the effective user from on_behalf_of (user).
    // - Takes the real user from the real caller, not the intermediate
    //   X-Gerrit-RunAs user (user2).
    allowRunAs();
    allowCodeReviewOnBehalfOf();
    TestAccount user2 = accountCreator.user2();

    PushOneCommit.Result r = createChange();
    ReviewInput in = new ReviewInput();
    in.onBehalfOf = user.id().toString();
    in.message = "Message on behalf of";

    String endpoint = "/changes/" + r.getChangeId() + "/revisions/current/review";
    RestResponse res = adminRestSession.postWithHeader(endpoint, runAsHeader(user2.id()), in);
    res.assertForbidden();
    assertThat(res.getEntityContent())
        .isEqualTo("label required to post review on behalf of \"" + in.onBehalfOf + '"');

    in.label("Code-Review", 1);
    adminRestSession.postWithHeader(endpoint, runAsHeader(user2.id()), in).assertOK();

    PatchSetApproval psa = Iterables.getOnlyElement(r.getChange().approvals().values());
    assertThat(psa.patchSetId().get()).isEqualTo(1);
    assertThat(psa.label()).isEqualTo("Code-Review");
    assertThat(psa.accountId()).isEqualTo(user.id());
    assertThat(psa.value()).isEqualTo(1);
    assertThat(psa.realAccountId()).isEqualTo(admin.id()); // not user2

    ChangeData cd = r.getChange();
    ChangeMessage m = Iterables.getLast(cmUtil.byChange(cd.notes()));
    assertThat(m.getMessage()).endsWith(in.message);
    assertThat(m.getAuthor()).isEqualTo(user.id());
    assertThat(m.getRealAuthor()).isEqualTo(admin.id()); // not user2
  }

  @Test
  public void changeMessageCreatedOnBehalfOfHasRealUser() throws Exception {
    allowCodeReviewOnBehalfOf();

    PushOneCommit.Result r = createChange();
    ReviewInput in = new ReviewInput();
    in.onBehalfOf = user.id().toString();
    in.message = "Message on behalf of";
    in.label("Code-Review", 1);

    requestScopeOperations.setApiUser(accountCreator.user2().id());
    gApi.changes().id(r.getChangeId()).revision(r.getPatchSetId().getId()).review(in);

    ChangeInfo info = gApi.changes().id(r.getChangeId()).get(MESSAGES);
    assertThat(info.messages).hasSize(2);

    ChangeMessageInfo changeMessageInfo = Iterables.getLast(info.messages);
    assertThat(changeMessageInfo.realAuthor).isNotNull();
    assertThat(changeMessageInfo.realAuthor._accountId)
        .isEqualTo(accountCreator.user2().id().get());
  }

  private void allowCodeReviewOnBehalfOf() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(TestLabels.codeReview().getName())
                .impersonation(true)
                .ref("refs/heads/*")
                .group(REGISTERED_USERS)
                .range(-1, 1))
        .update();
  }

  private void allowSubmitOnBehalfOf() throws Exception {
    String heads = "refs/heads/*";
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.SUBMIT_AS).ref(heads).group(REGISTERED_USERS))
        .add(allow(Permission.SUBMIT).ref(heads).group(REGISTERED_USERS))
        .add(
            allowLabel(TestLabels.codeReview().getName())
                .ref(heads)
                .group(REGISTERED_USERS)
                .range(-2, 2))
        .update();
  }

  private void blockRead(GroupInfo group) throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref("refs/heads/master").group(AccountGroup.uuid(group.id)))
        .update();
  }

  private void allowRunAs() throws Exception {
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(GlobalCapability.RUN_AS).group(ANONYMOUS_USERS))
        .update();
  }

  private void removeRunAs() throws Exception {
    projectOperations
        .allProjectsForUpdate()
        .remove(capabilityKey(GlobalCapability.RUN_AS).group(ANONYMOUS_USERS))
        .update();
  }

  private static Header runAsHeader(Object user) {
    return new BasicHeader("X-Gerrit-RunAs", user.toString());
  }
}
