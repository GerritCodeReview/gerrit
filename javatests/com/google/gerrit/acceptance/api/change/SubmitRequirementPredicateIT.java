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

package com.google.gerrit.acceptance.api.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.testing.TestLabels.codeReview;
import static com.google.gerrit.server.project.testing.TestLabels.label;
import static com.google.gerrit.server.project.testing.TestLabels.value;
import static org.eclipse.jgit.lib.Constants.HEAD;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.UseTimezone;
import com.google.gerrit.acceptance.VerifyNoPiiInChangeNotes;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.entities.SubmitRequirementExpressionResult;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.server.account.ServiceUserClassifier;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.SubmitRequirementsEvaluatorImpl;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
@UseTimezone(timezone = "US/Eastern")
@VerifyNoPiiInChangeNotes(true)
public class SubmitRequirementPredicateIT extends AbstractDaemonTest {

  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private SubmitRequirementsEvaluatorImpl submitRequirementsEvaluator;
  @Inject private ChangeOperations changeOperations;
  @Inject private ProjectOperations projectOperations;
  @Inject private AccountOperations accountOperations;

  private final LabelType label =
      label("Custom-Label", value(1, "Positive"), value(0, "No score"), value(-1, "Negative"));

  private final LabelType pLabel =
      label("Custom-Label2", value(1, "Positive"), value(0, "No score"));

  @Before
  public void setUp() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allowLabel(label.getName()).ref("refs/heads/*").group(ANONYMOUS_USERS).range(-1, 1))
        .add(allowLabel(pLabel.getName()).ref("refs/heads/*").group(ANONYMOUS_USERS).range(0, 1))
        .update();
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().upsertLabelType(label);
      u.getConfig().upsertLabelType(pLabel);
      u.save();
    }
  }

  @Test
  public void labelVote_greaterThan_withManyMaxVotes() throws Exception {
    TestRepository<InMemoryRepository> clonedRepo = cloneProject(project, admin);
    PushOneCommit.Result r1 =
        pushFactory
            .create(user.newIdent(), clonedRepo, "Subject", "file.txt", "text")
            .to("refs/for/master");

    Account.Id user11 = accountCreator.create("user11").id();
    Account.Id user12 = accountCreator.create("user12").id();
    Account.Id user13 = accountCreator.create("user13").id();
    Account.Id user14 = accountCreator.create("user14").id();
    Account.Id user15 = accountCreator.create("user15").id();
    Account.Id user16 = accountCreator.create("user16").id();
    Account.Id user17 = accountCreator.create("user17").id();
    ImmutableList<Account.Id> allUsers =
        ImmutableList.of(user11, user12, user13, user14, user15, user16, user17);

    // Give voting permissions to all users
    requestScopeOperations.setApiUser(admin.id());
    allowLabelPermission(
        codeReview().getName(), RefNames.REFS_HEADS + "*", REGISTERED_USERS, -2, +2);

    // The predicate uses the MAX_COUNT_INTERNAL in label predicate, and the SR expression matches
    // even if the change has more than 5 votes.
    for (Account.Id aId : allUsers) {
      approveAsUser(r1.getChangeId(), aId);
      assertMatching("label:Code-Review=+2,count>=1", r1.getChange().getId());
    }
  }

  @Test
  public void messagePredicate_ignoresPunctuationPreservesOrder() throws Exception {
    Change.Id c1 =
        changeOperations
            .newChange()
            .commitMessage("Hello Earth, from planet Mars")
            .project(project)
            .create();
    // The punctuation and capitalisation is ignored.
    assertMatching("message:\"earth from planet\"", c1);
    // The punctuation and capitalisation is ignored.
    assertNotMatching("message:\"planet from earth\"", c1);
  }

  @Test
  public void distinctVoters_sameUserVotesOnDifferentLabels_fails() throws Exception {
    Change.Id c1 = changeOperations.newChange().project(project).create();
    requestScopeOperations.setApiUser(admin.id());
    approve(c1.toString());
    assertNotMatching("distinctvoters:\"[Code-Review,Custom-Label],value=MAX,count>1\"", c1);

    // Same user votes on both labels
    gApi.changes()
        .id(c1.toString())
        .current()
        .review(ReviewInput.create().label("Custom-Label", 1));
    assertNotMatching("distinctvoters:\"[Code-Review,Custom-Label],value=MAX,count>1\"", c1);
  }

  @Test
  public void distinctVoters_distinctUsersOnDifferentLabels_passes() throws Exception {
    Change.Id c1 = changeOperations.newChange().project(project).create();
    requestScopeOperations.setApiUser(admin.id());
    approve(c1.toString());
    requestScopeOperations.setApiUser(user.id());
    gApi.changes()
        .id(c1.toString())
        .current()
        .review(ReviewInput.create().label("Custom-Label", 1));
    assertMatching("distinctvoters:\"[Code-Review,Custom-Label],value=MAX,count>1\"", c1);
  }

  @Test
  public void distinctVoters_onlyMaxVotesRespected() throws Exception {
    Change.Id c1 = changeOperations.newChange().project(project).create();
    requestScopeOperations.setApiUser(user.id());
    gApi.changes()
        .id(c1.toString())
        .current()
        .review(ReviewInput.create().label("Custom-Label", 1));
    requestScopeOperations.setApiUser(admin.id());
    recommend(c1.toString());
    assertNotMatching("distinctvoters:\"[Code-Review,Custom-Label],value=MAX,count>1\"", c1);
    requestScopeOperations.setApiUser(admin.id());
    approve(c1.toString());
    assertMatching("distinctvoters:\"[Code-Review,Custom-Label],value=MAX,count>1\"", c1);
  }

  @Test
  public void distinctVoters_onlyMinVotesRespected() throws Exception {
    Change.Id c1 = changeOperations.newChange().project(project).create();
    requestScopeOperations.setApiUser(user.id());
    gApi.changes()
        .id(c1.toString())
        .current()
        .review(ReviewInput.create().label("Custom-Label", -1));
    requestScopeOperations.setApiUser(admin.id());
    recommend(c1.toString());
    assertNotMatching("distinctvoters:\"[Code-Review,Custom-Label],value=MIN,count>1\"", c1);
    requestScopeOperations.setApiUser(admin.id());
    gApi.changes().id(c1.toString()).current().review(ReviewInput.reject());
    assertMatching("distinctvoters:\"[Code-Review,Custom-Label],value=MIN,count>1\"", c1);
  }

  @Test
  public void distinctVoters_onlyExactValueRespected() throws Exception {
    Change.Id c1 = changeOperations.newChange().project(project).create();
    requestScopeOperations.setApiUser(user.id());
    gApi.changes()
        .id(c1.toString())
        .current()
        .review(ReviewInput.create().label("Custom-Label", 1));
    requestScopeOperations.setApiUser(admin.id());
    approve(c1.toString());
    assertNotMatching("distinctvoters:\"[Code-Review,Custom-Label],value=1,count>1\"", c1);
    requestScopeOperations.setApiUser(admin.id());
    recommend(c1.toString());
    assertMatching("distinctvoters:\"[Code-Review,Custom-Label],value=1,count>1\"", c1);
  }

  @Test
  public void distinctVoters_valueIsOptional() throws Exception {
    Change.Id c1 = changeOperations.newChange().project(project).create();
    requestScopeOperations.setApiUser(user.id());
    gApi.changes()
        .id(c1.toString())
        .current()
        .review(ReviewInput.create().label("Custom-Label", -1));
    requestScopeOperations.setApiUser(admin.id());
    assertNotMatching("distinctvoters:\"[Code-Review,Custom-Label],count>1\"", c1);
    recommend(c1.toString());
    assertMatching("distinctvoters:\"[Code-Review,Custom-Label],count>1\"", c1);
  }

  @Test
  public void distinctVoters_moreThanTwoLabels() throws Exception {
    Change.Id c1 = changeOperations.newChange().project(project).create();
    requestScopeOperations.setApiUser(user.id());
    gApi.changes()
        .id(c1.toString())
        .current()
        .review(ReviewInput.create().label("Custom-Label2", 1));
    requestScopeOperations.setApiUser(admin.id());
    recommend(c1.toString());
    assertMatching(
        "distinctvoters:\"[Code-Review,Custom-Label,Custom-Label2],value=1,count>1\"", c1);
  }

  @Test
  public void distinctVoters_moreThanTwoLabels_moreThanTwoUsers() throws Exception {
    Change.Id c1 = changeOperations.newChange().project(project).create();
    requestScopeOperations.setApiUser(user.id());
    gApi.changes()
        .id(c1.toString())
        .current()
        .review(ReviewInput.create().label("Custom-Label2", 1));
    requestScopeOperations.setApiUser(admin.id());
    recommend(c1.toString());
    assertNotMatching(
        "distinctvoters:\"[Code-Review,Custom-Label,Custom-Label2],value=1,count>2\"", c1);
    Account.Id tester = accountOperations.newAccount().create();
    requestScopeOperations.setApiUser(tester);
    gApi.changes()
        .id(c1.toString())
        .current()
        .review(ReviewInput.create().label("Custom-Label", 1));
    assertMatching(
        "distinctvoters:\"[Code-Review,Custom-Label,Custom-Label2],value=1,count>2\"", c1);
  }

  @Test
  public void hasSubmoduleUpdate_withSubmoduleChangeInParent1() throws Exception {
    ObjectId initial = repo().exactRef(HEAD).getLeaf().getObjectId();
    PushOneCommit.Result r1 = createGitSubmoduleCommit("refs/for/master");
    testRepo.reset(initial);
    PushOneCommit.Result r2 = createNormalCommit("refs/for/master", "file1");
    PushOneCommit.Result merge =
        createMergeCommitChange(
            "refs/for/master",
            r1.getCommit(),
            r2.getCommit(),
            mergeAndGetTreeId(r1.getCommit(), r2.getCommit()));

    assertNotMatching("has:submodule-update,base=1", merge.getChange().getId());
    assertMatching("has:submodule-update,base=2", merge.getChange().getId());
    assertNotMatching("has:submodule-update", merge.getChange().getId());
  }

  @Test
  public void hasSubmoduleUpdate_withSubmoduleChangeInParent2() throws Exception {
    ObjectId initial = repo().exactRef(HEAD).getLeaf().getObjectId();
    PushOneCommit.Result r1 = createNormalCommit("refs/for/master", "file1");
    testRepo.reset(initial);
    PushOneCommit.Result r2 = createGitSubmoduleCommit("refs/for/master");
    PushOneCommit.Result merge =
        createMergeCommitChange(
            "refs/for/master",
            r1.getCommit(),
            r2.getCommit(),
            mergeAndGetTreeId(r1.getCommit(), r2.getCommit()));

    assertMatching("has:submodule-update,base=1", merge.getChange().getId());
    assertNotMatching("has:submodule-update,base=2", merge.getChange().getId());
    assertNotMatching("has:submodule-update", merge.getChange().getId());
  }

  @Test
  public void hasSubmoduleUpdate_withoutSubmoduleChange_doesNotMatch() throws Exception {
    ObjectId initial = repo().exactRef(HEAD).getLeaf().getObjectId();
    PushOneCommit.Result r1 = createNormalCommit("refs/for/master", "file1");
    testRepo.reset(initial);
    PushOneCommit.Result r2 = createNormalCommit("refs/for/master", "file2");
    PushOneCommit.Result merge =
        createMergeCommitChange(
            "refs/for/master",
            r1.getCommit(),
            r2.getCommit(),
            mergeAndGetTreeId(r1.getCommit(), r2.getCommit()));

    assertNotMatching("has:submodule-update,base=1", merge.getChange().getId());
    assertNotMatching("has:submodule-update,base=2", merge.getChange().getId());
    assertNotMatching("has:submodule-update", merge.getChange().getId());
  }

  @Test
  public void hasSubmoduleUpdate_withBaseParamGreaterThanParentCount_doesNotMatch()
      throws Exception {
    ObjectId initial = repo().exactRef(HEAD).getLeaf().getObjectId();
    PushOneCommit.Result r1 = createNormalCommit("refs/for/master", "file1");
    testRepo.reset(initial);
    PushOneCommit.Result r2 = createGitSubmoduleCommit("refs/for/master");
    PushOneCommit.Result merge =
        createMergeCommitChange(
            "refs/for/master",
            r1.getCommit(),
            r2.getCommit(),
            mergeAndGetTreeId(r1.getCommit(), r2.getCommit()));

    assertNotMatching("has:submodule-update,base=3", merge.getChange().getId());
  }

  @Test
  public void hasSubmoduleUpdate_withWrongArgs_throws() {
    assertError(
        "has:submodule-update,base=xyz",
        changeOperations.newChange().project(project).create(),
        "failed to parse the parent number xyz: For input string: \"xyz\"");
    assertError(
        "has:submodule-update,base=1,arg=foo",
        changeOperations.newChange().project(project).create(),
        "wrong number of arguments for the has:submodule-update operator");
    assertError(
        "has:submodule-update,base",
        changeOperations.newChange().project(project).create(),
        "unexpected base value format");
  }

  @Test
  public void nonContributorLabelVote_match() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    TestRepository<InMemoryRepository> clonedRepo = cloneProject(project, user);
    PushOneCommit.Result r1 =
        pushFactory
            .create(user.newIdent(), clonedRepo, "Subject", "file.txt", "text")
            .to("refs/for/master");

    Change.Id cId = r1.getChange().getId();

    ChangeInfo changeInfo = gApi.changes().id(r1.getChangeId()).get();

    // Assert on uploader, committer and author
    assertUploader(changeInfo, user.email());
    assertCommitter(changeInfo, user.email());
    assertAuthor(changeInfo, user.email());

    // Vote from admin (a.k.a. non uploader/committer/author) matches
    requestScopeOperations.setApiUser(admin.id());
    approve(cId.toString());
    assertMatching("label:Code-Review=+2,user=non_contributor", cId);
    // Also make sure magic label votes and > operator work
    assertMatching("label:Code-Review=MAX,user=non_contributor", cId);
    assertMatching("label:Code-Review>+1,user=non_contributor", cId);
  }

  @Test
  public void nonContributorLabelVote_voteFromUploader_doesNotMatch() throws Exception {
    PushOneCommit.Result r1 = createNormalCommit(user.newIdent(), "refs/for/master", "file1");

    ChangeInfo changeInfo = gApi.changes().id(r1.getChangeId()).get();
    assertUploader(changeInfo, admin.email());

    // Vote from admin (a.k.a. uploader) does not match
    requestScopeOperations.setApiUser(admin.id());
    approve(r1.getChangeId());
    assertNotMatching("label:Code-Review=+2,user=non_contributor", r1.getChange().getId());
  }

  @Test
  @Sandboxed
  public void nonContributorLabelVote_voteFromAuthor_doesNotMatch() throws Exception {
    Account.Id authorId =
        accountOperations
            .newAccount()
            .fullname("author")
            .preferredEmail("authoremail@example.com")
            .create();
    Account.Id committerId =
        accountOperations
            .newAccount()
            .fullname("committer")
            .preferredEmail("committeremail@example.com")
            .create();

    Change.Id changeId =
        changeOperations.newChange().author(authorId).committer(committerId).create();
    ChangeInfo changeInfo = gApi.changes().id(changeId.get()).get();
    assertAuthor(changeInfo, "authoremail@example.com");

    allowLabelPermission(
        codeReview().getName(), RefNames.REFS_HEADS + "*", REGISTERED_USERS, -2, +2);

    // Vote from author does not match
    requestScopeOperations.setApiUser(authorId);
    approve(changeId.toString());
    assertNotMatching("label:Code-Review=+2,user=non_contributor", changeId);
  }

  @Test
  public void nonContributorLabelVote_voteFromCommitter_doesNotMatch() throws Exception {
    Account.Id authorId =
        accountOperations
            .newAccount()
            .fullname("author")
            .preferredEmail("authoremail@example.com")
            .create();
    Account.Id committerId =
        accountOperations
            .newAccount()
            .fullname("committer")
            .preferredEmail("committeremail@example.com")
            .create();

    Change.Id changeId =
        changeOperations.newChange().author(authorId).committer(committerId).create();
    ChangeInfo changeInfo = gApi.changes().id(changeId.get()).get();
    assertCommitter(changeInfo, "committeremail@example.com");

    allowLabelPermission(
        codeReview().getName(), RefNames.REFS_HEADS + "*", REGISTERED_USERS, -2, +2);

    // Vote from committer does not match
    requestScopeOperations.setApiUser(committerId);
    approve(changeId.toString());
    assertNotMatching("label:Code-Review=+2,user=non_contributor", changeId);
  }

  @Test
  public void nonContributorLabelVote_uploaderAndAuthorDifferent() throws Exception {
    TestRepository<InMemoryRepository> clonedRepo = cloneProject(project, admin);
    PushOneCommit.Result r1 =
        pushFactory
            .create(user.newIdent(), clonedRepo, "Subject", "file.txt", "text")
            .to("refs/for/master");

    requestScopeOperations.setApiUser(admin.id());
    ChangeInfo changeInfo = gApi.changes().id(r1.getChangeId()).get();
    assertUploader(changeInfo, admin.email());
    assertAuthor(changeInfo, user.email());

    allowLabelPermission(
        codeReview().getName(), RefNames.REFS_HEADS + "*", REGISTERED_USERS, -2, +2);

    // Vote from admin (a.k.a. uploader) does not match
    requestScopeOperations.setApiUser(user.id());
    approve(r1.getChangeId());
    assertNotMatching("label:Code-Review=+2,user=non_contributor", r1.getChange().getId());

    // Vote from user (a.k.a. author) does not match
    requestScopeOperations.setApiUser(admin.id());
    approve(r1.getChangeId());
    assertNotMatching("label:Code-Review=+2,user=non_contributor", r1.getChange().getId());

    // Vote from user2 (a.k.a. non-author and non-uploader) matches
    TestAccount user2 = accountCreator.create();
    requestScopeOperations.setApiUser(user2.id());
    approve(r1.getChangeId());
    assertMatching("label:Code-Review=+2,user=non_contributor", r1.getChange().getId());
  }

  @Test
  public void label_requireVoteFromHumanReviewers() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allowLabel("Code-Review").ref("refs/*").group(REGISTERED_USERS).range(-2, 2))
        .update();

    Account.Id owner = accountCreator.create("owner").id();
    Account.Id reviewer1 = accountCreator.create("reviewer1").id();
    Account.Id reviewer2 = accountCreator.create("reviewer2").id();
    Account.Id reviewer3 = accountCreator.create("reviewer3").id();

    Account.Id serviceUser = accountCreator.create("serviceUser").id();
    gApi.groups().id(ServiceUserClassifier.SERVICE_USERS).addMembers(serviceUser.toString());

    Change.Id changeApprovedByAllReviewers =
        changeOperations.newChange().project(project).owner(owner).create();
    addReviewers(project, changeApprovedByAllReviewers, reviewer1, reviewer2, reviewer3);
    addReviews(
        project,
        changeApprovedByAllReviewers,
        ReviewInput.approve(),
        reviewer1,
        reviewer2,
        reviewer3);

    Change.Id changeApprovedBySomeReviewers =
        changeOperations.newChange().project(project).owner(owner).create();
    addReviewers(project, changeApprovedBySomeReviewers, reviewer1, reviewer2, reviewer3);
    addReviews(project, changeApprovedBySomeReviewers, ReviewInput.approve(), reviewer1, reviewer2);

    Change.Id changeRecommendedByAllReviewers =
        changeOperations.newChange().project(project).owner(owner).create();
    addReviewers(project, changeRecommendedByAllReviewers, reviewer1, reviewer2, reviewer3);
    addReviews(
        project,
        changeRecommendedByAllReviewers,
        ReviewInput.recommend(),
        reviewer1,
        reviewer2,
        reviewer3);

    Change.Id changeRecommendedBySomeReviewers =
        changeOperations.newChange().project(project).owner(owner).create();
    addReviewers(project, changeRecommendedBySomeReviewers, reviewer1, reviewer2, reviewer3);
    addReviews(
        project, changeRecommendedBySomeReviewers, ReviewInput.recommend(), reviewer1, reviewer2);

    Change.Id changeNoVotesByReviewers =
        changeOperations.newChange().project(project).owner(owner).create();
    addReviewers(project, changeNoVotesByReviewers, reviewer1, reviewer2, reviewer3);

    Change.Id changeWithoutReviewers =
        changeOperations.newChange().project(project).owner(owner).create();

    requestScopeOperations.setApiUser(user.id());

    // change without reviewers doesn't match
    assertNotMatching("label:Code-Review=MAX,users=human_reviewers", changeWithoutReviewers);

    // match changes where all reviewers have the same vote
    assertRequirement(
        "label:Code-Review=MAX,users=human_reviewers",
        ImmutableList.of(changeApprovedByAllReviewers),
        ImmutableList.of(
            changeApprovedBySomeReviewers,
            changeRecommendedByAllReviewers,
            changeRecommendedBySomeReviewers,
            changeNoVotesByReviewers));
    assertRequirement(
        "label:Code-Review=2,users=human_reviewers",
        ImmutableList.of(changeApprovedByAllReviewers),
        ImmutableList.of(
            changeApprovedBySomeReviewers,
            changeRecommendedByAllReviewers,
            changeRecommendedBySomeReviewers,
            changeNoVotesByReviewers));
    assertRequirement(
        "label:Code-Review=1,users=human_reviewers",
        ImmutableList.of(changeRecommendedByAllReviewers),
        ImmutableList.of(
            changeApprovedBySomeReviewers,
            changeApprovedByAllReviewers,
            changeRecommendedBySomeReviewers,
            changeNoVotesByReviewers));

    // match changes where no reviewer voted (same as "label:Code-Review=0")
    assertRequirement(
        "label:Code-Review=0,users=human_reviewers",
        ImmutableList.of(changeNoVotesByReviewers),
        ImmutableList.of(
            changeApprovedByAllReviewers,
            changeApprovedBySomeReviewers,
            changeRecommendedByAllReviewers,
            changeRecommendedBySomeReviewers));

    // match changes where all reviewers have a vote <=, >=, < or >
    assertRequirement(
        "label:Code-Review<=2,users=human_reviewers",
        ImmutableList.of(
            changeApprovedByAllReviewers,
            changeApprovedBySomeReviewers,
            changeRecommendedByAllReviewers,
            changeRecommendedBySomeReviewers,
            changeNoVotesByReviewers),
        ImmutableList.of());
    assertRequirement(
        "label:Code-Review<=1,users=human_reviewers",
        ImmutableList.of(
            changeRecommendedByAllReviewers,
            changeRecommendedByAllReviewers,
            changeRecommendedBySomeReviewers,
            changeNoVotesByReviewers),
        ImmutableList.of(changeApprovedByAllReviewers));
    assertRequirement(
        "label:Code-Review>=1,users=human_reviewers",
        ImmutableList.of(changeApprovedByAllReviewers, changeRecommendedByAllReviewers),
        ImmutableList.of(
            changeApprovedBySomeReviewers,
            changeRecommendedBySomeReviewers,
            changeNoVotesByReviewers));
    assertRequirement(
        "label:Code-Review<1,users=human_reviewers",
        ImmutableList.of(changeNoVotesByReviewers),
        ImmutableList.of(
            changeApprovedByAllReviewers,
            changeApprovedBySomeReviewers,
            changeRecommendedByAllReviewers,
            changeRecommendedBySomeReviewers));
    assertRequirement(
        "label:Code-Review>1,users=human_reviewers",
        ImmutableList.of(changeApprovedByAllReviewers),
        ImmutableList.of(
            changeApprovedBySomeReviewers,
            changeRecommendedByAllReviewers,
            changeRecommendedBySomeReviewers,
            changeNoVotesByReviewers));

    // match changes where all reviewers have any (non-zero) vote
    assertRequirement(
        "label:Code-Review=ANY,users=human_reviewers",
        ImmutableList.of(changeApprovedByAllReviewers, changeRecommendedByAllReviewers),
        ImmutableList.of(
            changeApprovedBySomeReviewers,
            changeRecommendedBySomeReviewers,
            changeNoVotesByReviewers));

    // votes of the change owners are ignored (as the change owner is not considered as a reviewer)
    addReviews(project, changeApprovedByAllReviewers, ReviewInput.dislike(), owner);
    assertRequirement(
        "label:Code-Review=MAX,users=human_reviewers",
        ImmutableList.of(changeApprovedByAllReviewers),
        ImmutableList.of(
            changeApprovedBySomeReviewers,
            changeRecommendedByAllReviewers,
            changeRecommendedBySomeReviewers,
            changeNoVotesByReviewers));

    // missing votes from service users are fine
    addReviewers(project, changeApprovedByAllReviewers, serviceUser);
    assertRequirement(
        "label:Code-Review=MAX,users=human_reviewers",
        ImmutableList.of(changeApprovedByAllReviewers),
        ImmutableList.of(
            changeApprovedBySomeReviewers,
            changeRecommendedByAllReviewers,
            changeRecommendedBySomeReviewers,
            changeNoVotesByReviewers));

    // votes from service users are ignored
    addReviews(project, changeApprovedByAllReviewers, ReviewInput.dislike(), serviceUser);
    assertRequirement(
        "label:Code-Review=MAX,users=human_reviewers",
        ImmutableList.of(changeApprovedByAllReviewers),
        ImmutableList.of(
            changeApprovedBySomeReviewers,
            changeRecommendedByAllReviewers,
            changeRecommendedBySomeReviewers,
            changeNoVotesByReviewers));

    // when reviewers by email are present changes do not match, unless the expected value is 0
    try (MetaDataUpdate md = metaDataUpdateFactory.create(project)) {
      ProjectConfig cfg = projectConfigFactory.create(project);
      cfg.load(md);
      cfg.updateProject(
          update ->
              update.setBooleanConfig(
                  BooleanProjectConfig.ENABLE_REVIEWER_BY_EMAIL, InheritableBoolean.TRUE));
      cfg.commit(md);
    }
    projectCache.evictAndReindex(project);
    Change.Id changeRecommendedByAllReviewersWithReviewersByEmail =
        changeOperations.newChange().project(project).owner(owner).create();
    addReviewers(
        project,
        changeRecommendedByAllReviewersWithReviewersByEmail,
        reviewer1,
        reviewer2,
        reviewer3);
    addReviews(
        project,
        changeRecommendedByAllReviewersWithReviewersByEmail,
        ReviewInput.recommend(),
        reviewer1,
        reviewer2,
        reviewer3);
    addReviewer(
        project,
        changeRecommendedByAllReviewersWithReviewersByEmail,
        "email-without-account@example.com");
    Change.Id changeNoVotesByReviewersWithReviewersByEmail =
        changeOperations.newChange().project(project).owner(owner).create();
    addReviewers(
        project, changeNoVotesByReviewersWithReviewersByEmail, reviewer1, reviewer2, reviewer3);
    addReviewer(
        project, changeNoVotesByReviewersWithReviewersByEmail, "email-without-account@example.com");
    assertRequirement(
        "label:Code-Review=MAX,users=human_reviewers",
        ImmutableList.of(),
        ImmutableList.of(
            changeRecommendedByAllReviewersWithReviewersByEmail,
            changeNoVotesByReviewersWithReviewersByEmail));
    assertRequirement(
        "label:Code-Review=2,users=human_reviewers",
        ImmutableList.of(),
        ImmutableList.of(
            changeRecommendedByAllReviewersWithReviewersByEmail,
            changeNoVotesByReviewersWithReviewersByEmail));
    assertRequirement(
        "label:Code-Review=ANY,users=human_reviewers",
        ImmutableList.of(),
        ImmutableList.of(
            changeRecommendedByAllReviewersWithReviewersByEmail,
            changeNoVotesByReviewersWithReviewersByEmail));
    assertRequirement(
        "label:Code-Review=0,users=human_reviewers",
        ImmutableList.of(changeNoVotesByReviewersWithReviewersByEmail),
        ImmutableList.of(changeRecommendedByAllReviewersWithReviewersByEmail));
    assertRequirement(
        "label:Code-Review<=2,users=human_reviewers",
        ImmutableList.of(
            changeRecommendedByAllReviewersWithReviewersByEmail,
            changeNoVotesByReviewersWithReviewersByEmail),
        ImmutableList.of());
    assertRequirement(
        "label:Code-Review<=0,users=human_reviewers",
        ImmutableList.of(changeNoVotesByReviewersWithReviewersByEmail),
        ImmutableList.of(changeRecommendedByAllReviewersWithReviewersByEmail));
    assertRequirement(
        "label:Code-Review<=-1,users=human_reviewers",
        ImmutableList.of(),
        ImmutableList.of(
            changeRecommendedByAllReviewersWithReviewersByEmail,
            changeNoVotesByReviewersWithReviewersByEmail));
    assertRequirement(
        "label:Code-Review<2,users=human_reviewers",
        ImmutableList.of(
            changeRecommendedByAllReviewersWithReviewersByEmail,
            changeNoVotesByReviewersWithReviewersByEmail),
        ImmutableList.of());
    assertRequirement(
        "label:Code-Review<1,users=human_reviewers",
        ImmutableList.of(changeNoVotesByReviewersWithReviewersByEmail),
        ImmutableList.of(changeRecommendedByAllReviewersWithReviewersByEmail));
    assertRequirement(
        "label:Code-Review<0,users=human_reviewers",
        ImmutableList.of(),
        ImmutableList.of(
            changeRecommendedByAllReviewersWithReviewersByEmail,
            changeNoVotesByReviewersWithReviewersByEmail));
    assertRequirement(
        "label:Code-Review>=0,users=human_reviewers",
        ImmutableList.of(
            changeRecommendedByAllReviewersWithReviewersByEmail,
            changeNoVotesByReviewersWithReviewersByEmail),
        ImmutableList.of());
    assertRequirement(
        "label:Code-Review>=1,users=human_reviewers",
        ImmutableList.of(),
        ImmutableList.of(
            changeRecommendedByAllReviewersWithReviewersByEmail,
            changeNoVotesByReviewersWithReviewersByEmail));
    assertRequirement(
        "label:Code-Review>-1,users=human_reviewers",
        ImmutableList.of(
            changeRecommendedByAllReviewersWithReviewersByEmail,
            changeNoVotesByReviewersWithReviewersByEmail),
        ImmutableList.of());
    assertRequirement(
        "label:Code-Review>0,users=human_reviewers",
        ImmutableList.of(),
        ImmutableList.of(
            changeRecommendedByAllReviewersWithReviewersByEmail,
            changeNoVotesByReviewersWithReviewersByEmail));

    // cannot combine users=human_reviewers" with submit record status
    assertError(
        "label:Code-Review=ok,users=human_reviewers",
        changeApprovedByAllReviewers,
        "Cannot use the 'users=human_reviewers' argument in conjunction with a submit record label"
            + " status");

    // cannot combine "users" arg with a "user" arg
    assertError(
        "label:Code-Review=MAX,users=human_reviewers,user=reviewer1",
        changeApprovedByAllReviewers,
        "Cannot use the 'users' argument in conjunction with other arguments ('count', 'user',"
            + " group')");

    // cannot combine "users" arg with a "group" arg
    assertError(
        "label:Code-Review=MAX,users=human_reviewers,group=foo",
        changeApprovedByAllReviewers,
        "Cannot use the 'users' argument in conjunction with other arguments ('count', 'user',"
            + " group')");

    // cannot combine "users" arg with a positional arg
    assertError(
        "label:Code-Review=MAX,users=human_reviewers,reviewer1",
        changeApprovedByAllReviewers,
        "Cannot use the 'users' argument in conjunction with other arguments ('count', 'user',"
            + " group')");
    assertError(
        "label:Code-Review=MAX,reviewer1,users=human_reviewers",
        changeApprovedByAllReviewers,
        "Cannot use the 'users' argument in conjunction with other arguments ('count', 'user',"
            + " group')");

    // label without "users=human_reviewers" still works
    assertRequirement(
        "label:Code-Review=MAX,user=reviewer1",
        ImmutableList.of(changeApprovedByAllReviewers, changeApprovedBySomeReviewers),
        ImmutableList.of(
            changeRecommendedByAllReviewers,
            changeRecommendedBySomeReviewers,
            changeNoVotesByReviewers));
    assertRequirement(
        "label:Code-Review=MAX,reviewer1",
        ImmutableList.of(changeApprovedByAllReviewers, changeApprovedBySomeReviewers),
        ImmutableList.of(
            changeRecommendedByAllReviewers,
            changeRecommendedBySomeReviewers,
            changeNoVotesByReviewers));
  }

  private void addReviewers(Project.NameKey project, Change.Id changeId, Account.Id... reviewers)
      throws Exception {
    for (Account.Id reviewer : reviewers) {
      addReviewer(project, changeId, reviewer.toString());
    }
  }

  private void addReviewer(Project.NameKey project, Change.Id changeId, String reviewer)
      throws Exception {
    gApi.changes().id(project.get(), changeId.get()).addReviewer(reviewer);
  }

  private void addReviews(
      Project.NameKey project, Change.Id changeId, ReviewInput reviewInput, Account.Id... reviewers)
      throws Exception {
    for (Account.Id reviewer : reviewers) {
      requestScopeOperations.setApiUser(reviewer);
      gApi.changes().id(project.get(), changeId.get()).current().review(reviewInput);
    }
  }

  private void approveAsUser(String changeId, Account.Id userId) throws Exception {
    requestScopeOperations.setApiUser(userId);
    approve(changeId);
  }

  private static void assertUploader(ChangeInfo changeInfo, String email) {
    assertThat(changeInfo.revisions.get(changeInfo.currentRevision).uploader.email)
        .isEqualTo(email);
  }

  private static void assertCommitter(ChangeInfo changeInfo, String email) {
    assertThat(changeInfo.revisions.get(changeInfo.currentRevision).commit.committer.email)
        .isEqualTo(email);
  }

  private static void assertAuthor(ChangeInfo changeInfo, String email) {
    assertThat(changeInfo.revisions.get(changeInfo.currentRevision).commit.author.email)
        .isEqualTo(email);
  }

  private void allowLabelPermission(
      String labelName, String refPattern, AccountGroup.UUID group, int minVote, int maxVote) {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allowLabel(labelName).ref(refPattern).group(group).range(minVote, maxVote))
        .update();
  }

  private PushOneCommit.Result createGitSubmoduleCommit(String ref) throws Exception {
    return pushFactory
        .create(admin.newIdent(), testRepo, "subject", ImmutableMap.of())
        .addGitSubmodule(
            "modules/module-a", ObjectId.fromString("19f1787342cb15d7e82a762f6b494e91ccb4dd34"))
        .to(ref);
  }

  private PushOneCommit.Result createNormalCommit(
      PersonIdent personIdent, String ref, String fileName) throws Exception {
    return pushFactory
        .create(personIdent, testRepo, "subject", ImmutableMap.of(fileName, fileName))
        .to(ref);
  }

  private PushOneCommit.Result createNormalCommit(String ref, String fileName) throws Exception {
    return pushFactory
        .create(admin.newIdent(), testRepo, "subject", ImmutableMap.of(fileName, fileName))
        .to(ref);
  }

  private PushOneCommit.Result createMergeCommitChange(
      String ref, RevCommit parent1, RevCommit parent2, @Nullable ObjectId treeId)
      throws Exception {
    PushOneCommit m =
        pushFactory
            .create(admin.newIdent(), testRepo)
            .setParents(ImmutableList.of(parent1, parent2));
    if (treeId != null) {
      m.setTopLevelTreeId(treeId);
    }
    PushOneCommit.Result result = m.to(ref);
    result.assertOkStatus();
    return result;
  }

  private ObjectId mergeAndGetTreeId(RevCommit c1, RevCommit c2) throws Exception {
    ThreeWayMerger threeWayMerger = MergeStrategy.RESOLVE.newMerger(repo(), true);
    threeWayMerger.setBase(c1.getParent(0));
    boolean mergeResult = threeWayMerger.merge(c1, c2);
    assertThat(mergeResult).isTrue();
    return threeWayMerger.getResultTreeId();
  }

  private void assertRequirement(
      String requirement,
      ImmutableList<Change.Id> matchingChanges,
      ImmutableList<Change.Id> nonMatchingChanges) {
    for (Change.Id matchingChange : matchingChanges) {
      assertMatching(requirement, matchingChange);
    }

    for (Change.Id nonMatchingChange : nonMatchingChanges) {
      assertNotMatching(requirement, nonMatchingChange);
    }
  }

  private void assertMatching(String requirement, Change.Id change) {
    assertWithMessage("requirement \"%s\" doesn't match change %s", requirement, change)
        .that(evaluate(requirement, change).status())
        .isEqualTo(SubmitRequirementExpressionResult.Status.PASS);
  }

  private void assertNotMatching(String requirement, Change.Id change) {
    assertWithMessage("requirement \"%s\" matches change %s", requirement, change)
        .that(evaluate(requirement, change).status())
        .isEqualTo(SubmitRequirementExpressionResult.Status.FAIL);
  }

  private void assertError(String requirement, Change.Id change, String errorMessage) {
    SubmitRequirementExpressionResult result = evaluate(requirement, change);
    assertThat(result.status()).isEqualTo(SubmitRequirementExpressionResult.Status.ERROR);
    assertThat(result.errorMessage().get()).isEqualTo(errorMessage);
  }

  private SubmitRequirementExpressionResult evaluate(String requirement, Change.Id change) {
    ChangeData cd = changeDataFactory.create(project, change);
    return submitRequirementsEvaluator.evaluateExpression(
        SubmitRequirementExpression.create(requirement), cd);
  }
}
