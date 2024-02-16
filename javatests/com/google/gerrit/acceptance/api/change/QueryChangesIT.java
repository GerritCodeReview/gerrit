// Copyright (C) 2019 The Android Open Source Project
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
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.util.stream.Collectors.toList;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Correspondence;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.UseClockStep;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.AccessSection;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.restapi.change.QueryChanges;
import com.google.gerrit.truth.NullAwareCorrespondence;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.Arrays;
import java.util.List;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Test;

public class QueryChangesIT extends AbstractDaemonTest {
  @Inject private AccountOperations accountOperations;
  @Inject private ChangeOperations changeOperations;
  @Inject private ProjectOperations projectOperations;
  @Inject private Provider<QueryChanges> queryChangesProvider;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  @SuppressWarnings("unchecked")
  public void multipleQueriesInOneRequestCanContainSameChange() throws Exception {
    String cId1 = createChange().getChangeId();
    String cId2 = createChange().getChangeId();
    int numericId1 = gApi.changes().id(cId1).get()._number;
    int numericId2 = gApi.changes().id(cId2).get()._number;

    gApi.changes().id(cId2).setWorkInProgress();

    QueryChanges queryChanges = queryChangesProvider.get();

    queryChanges.addQuery("is:open repo:" + project.get());
    queryChanges.addQuery("is:wip repo:" + project.get());

    List<List<ChangeInfo>> result =
        (List<List<ChangeInfo>>) queryChanges.apply(TopLevelResource.INSTANCE).value();
    assertThat(result).hasSize(2);
    assertThat(result.get(0)).hasSize(2);
    assertThat(result.get(1)).hasSize(1);

    ImmutableList<Integer> firstResultIds =
        ImmutableList.of(result.get(0).get(0)._number, result.get(0).get(1)._number);
    assertThat(firstResultIds).containsExactly(numericId1, numericId2);
    assertThat(result.get(1).get(0)._number).isEqualTo(numericId2);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void moreChangesIndicatorDoesNotWronglyCopyToUnrelatedChanges() throws Exception {
    String queryWithMoreChanges = "is:wip limit:1 repo:" + project.get();
    String queryWithNoMoreChanges = "is:open limit:10 repo:" + project.get();
    createChange();
    String cId2 = createChange().getChangeId();
    String cId3 = createChange().getChangeId();
    gApi.changes().id(cId2).setWorkInProgress();
    gApi.changes().id(cId3).setWorkInProgress();

    // Run the capped query first
    QueryChanges queryChanges = queryChangesProvider.get();
    queryChanges.addQuery(queryWithMoreChanges);
    queryChanges.addQuery(queryWithNoMoreChanges);
    List<List<ChangeInfo>> result =
        (List<List<ChangeInfo>>) queryChanges.apply(TopLevelResource.INSTANCE).value();
    assertThat(result).hasSize(2);
    assertThat(result.get(0)).hasSize(1);
    assertThat(result.get(1)).hasSize(3);
    // _moreChanges is set on the first response, but not on the second.
    assertThat(result.get(0).get(0)._moreChanges).isTrue();
    assertNoChangeHasMoreChangesSet(result.get(1));

    // Run the capped query second
    QueryChanges queryChanges2 = queryChangesProvider.get();
    queryChanges2.addQuery(queryWithNoMoreChanges);
    queryChanges2.addQuery(queryWithMoreChanges);
    List<List<ChangeInfo>> result2 =
        (List<List<ChangeInfo>>) queryChanges2.apply(TopLevelResource.INSTANCE).value();
    assertThat(result2).hasSize(2);
    assertThat(result2.get(0)).hasSize(3);
    assertThat(result2.get(1)).hasSize(1);
    // _moreChanges is set on the second response, but not on the first.
    assertNoChangeHasMoreChangesSet(result2.get(0));
    assertThat(result2.get(1).get(0)._moreChanges).isTrue();
  }

  @Test
  @SuppressWarnings("unchecked")
  @GerritConfig(name = "operator-alias.change.numberaliastest", value = "change")
  public void aliasQuery() throws Exception {
    String cId1 = createChange().getChangeId();
    String cId2 = createChange().getChangeId();
    int numericId1 = gApi.changes().id(cId1).get()._number;
    int numericId2 = gApi.changes().id(cId2).get()._number;

    QueryChanges queryChanges = queryChangesProvider.get();
    queryChanges.addQuery("numberaliastest:12345");
    queryChanges.addQuery("numberaliastest:" + numericId1);
    queryChanges.addQuery("numberaliastest:" + numericId2);

    List<List<ChangeInfo>> result =
        (List<List<ChangeInfo>>) queryChanges.apply(TopLevelResource.INSTANCE).value();
    assertThat(result).hasSize(3);
    assertThat(result.get(0)).hasSize(0);
    assertThat(result.get(1)).hasSize(1);
    assertThat(result.get(2)).hasSize(1);

    assertThat(result.get(1).get(0)._number).isEqualTo(numericId1);
    assertThat(result.get(2).get(0)._number).isEqualTo(numericId2);
  }

  @Test
  @UseClockStep
  @SuppressWarnings("unchecked")
  public void withPagedResults() throws Exception {
    // Create 4 visible changes.
    createChange(testRepo);
    createChange(testRepo);
    int changeId3 = createChange(testRepo).getChange().getId().get();
    int changeId4 = createChange(testRepo).getChange().getId().get();

    // Create hidden project.
    Project.NameKey hiddenProject = projectOperations.newProject().create();
    TestRepository<InMemoryRepository> hiddenRepo = cloneProject(hiddenProject, admin);
    // Create 2 hidden changes.
    createChange(hiddenRepo);
    createChange(hiddenRepo);
    // Actually hide project
    projectOperations
        .project(hiddenProject)
        .forUpdate()
        .add(block(Permission.READ).ref("refs/*").group(REGISTERED_USERS))
        .update();

    // Create a change query that matches all changes (visible and hidden changes).
    // The index returns the changes ordered by last updated timestamp:
    // hiddenChange2, hiddenChange1, change4, change3, change2, change1
    QueryChanges queryChanges = queryChangesProvider.get();
    queryChanges.addQuery("branch:master");

    // Set a limit on the query so that we need to paginate over the results from the index.
    queryChanges.setLimit(2);

    // Execute the query and verify the results.
    // Since the limit is set to 2, at most 2 changes are returned to user, but the index query is
    // executed with limit 3 (+1 so that we can populate the _more_changes field on the last
    // result).
    // This means the index query with limit 3 returns these changes:
    // hiddenChange2, hiddenChange1, change4
    // The 2 hidden changes are filtered out because they are not visible to the caller.
    // This means we have only one matching result (change4) but the limit (3) is not exhausted
    // yet. Hence the next page is loaded from the index (startIndex is 3 to skip the results
    // that we already processed, limit is again 3). The results for the next page are:
    // change3, change2, change1
    // change2 and change1 are dropped because they are over the limit.
    List<ChangeInfo> result =
        (List<ChangeInfo>) queryChanges.apply(TopLevelResource.INSTANCE).value();
    assertThat(result.stream().map(i -> i._number).collect(toList()))
        .containsExactly(changeId3, changeId4);
  }

  @Test
  public void usingOutOfRangeLabelValuesDoesNotCauseError() throws Exception {
    for (String operator : ImmutableList.of("=", ">", ">=", "<", "<=")) {
      QueryChanges queryChanges = queryChangesProvider.get();
      queryChanges.addQuery("label:Code-Review" + operator + "10");
      queryChanges.addQuery("label:Code-Review" + operator + "-10");
      queryChanges.addQuery("Code-Review" + operator + "10");
      queryChanges.addQuery("Code-Review" + operator + "-10");
      assertThat(queryChanges.apply(TopLevelResource.INSTANCE).statusCode()).isEqualTo(SC_OK);
    }
  }

  @Test
  public void queryByFullNameEmailFormatWithEmptyFullNameWhenEmailMatchesSeveralAccounts()
      throws Exception {
    // Create 2 accounts with the same preferred email (both account must have no external ID for
    // the email because otherwise the account with the external ID takes precedence).
    String email = "foo.bar@example.com";
    Account.Id account1 = accountOperations.newAccount().create();
    accountOperations
        .account(account1)
        .forInvalidation()
        .preferredEmailWithoutExternalId(email)
        .invalidate();
    Account.Id account2 = accountOperations.newAccount().create();
    accountOperations
        .account(account2)
        .forInvalidation()
        .preferredEmailWithoutExternalId(email)
        .invalidate();

    // Search with "Full Name <email>" format, but without full name. Both created accounts match
    // the email. In this case Gerrit falls back to match on the full name. Check that this logic
    // doesn't fail if the full name in the input string is not present.
    QueryChanges queryChanges = queryChangesProvider.get();
    queryChanges.addQuery("<" + email + ">");
    assertThat(queryChanges.apply(TopLevelResource.INSTANCE).statusCode()).isEqualTo(SC_OK);
  }

  @Test
  public void defaultQueryCannotBeParsedDueToInvalidRegEx() throws Exception {
    QueryChanges queryChanges = queryChangesProvider.get();
    queryChanges.addQuery("^[A");
    BadRequestException e =
        assertThrows(
            BadRequestException.class, () -> queryChanges.apply(TopLevelResource.INSTANCE));
    assertThat(e).hasMessageThat().contains("no viable alternative at character '['");
  }

  @Test
  public void defaultQueryWithInvalidQuotedRegEx() throws Exception {
    QueryChanges queryChanges = queryChangesProvider.get();
    queryChanges.addQuery("\"^[A\"");
    BadRequestException e =
        assertThrows(
            BadRequestException.class, () -> queryChanges.apply(TopLevelResource.INSTANCE));
    assertThat(e).hasMessageThat().isEqualTo("invalid regular expression: [A");
  }

  @Test
  @SuppressWarnings("unchecked")
  @GerritConfig(name = "has-operand-alias.change.unaddressedaliastest", value = "unresolved")
  public void hasOperandAliasQuery() throws Exception {
    String cId1 = createChange().getChangeId();
    String cId2 = createChange().getChangeId();
    int numericId1 = gApi.changes().id(cId1).get()._number;
    int numericId2 = gApi.changes().id(cId2).get()._number;

    ReviewInput input = new ReviewInput();
    ReviewInput.CommentInput comment = new ReviewInput.CommentInput();
    comment.line = 1;
    comment.message = "comment";
    comment.unresolved = true;
    input.comments = ImmutableMap.of(Patch.COMMIT_MSG, ImmutableList.of(comment));
    gApi.changes().id(cId2).current().review(input);

    QueryChanges queryChanges = queryChangesProvider.get();
    queryChanges.addQuery("is:open repo:" + project.get());
    queryChanges.addQuery("has:unaddressedaliastest repo:" + project.get());

    List<List<ChangeInfo>> result =
        (List<List<ChangeInfo>>) queryChanges.apply(TopLevelResource.INSTANCE).value();
    assertThat(result).hasSize(2);
    assertThat(result.get(0)).hasSize(2);
    assertThat(result.get(1)).hasSize(1);

    ImmutableList<Integer> firstResultIds =
        ImmutableList.of(result.get(0).get(0)._number, result.get(0).get(1)._number);
    assertThat(firstResultIds).containsExactly(numericId1, numericId2);
    assertThat(result.get(1).get(0)._number).isEqualTo(numericId2);
  }

  @Test
  public void skipVisibility_rejectedForNonAdmin() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    final QueryChanges queryChanges = queryChangesProvider.get();
    String query = "is:open repo:" + project.get();
    queryChanges.addQuery(query);
    AuthException thrown =
        assertThrows(AuthException.class, () -> queryChanges.skipVisibility(true));
    assertThat(thrown).hasMessageThat().isEqualTo("administrate server not permitted");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void skipVisibility_noReadPermission() throws Exception {
    createChange();
    requestScopeOperations.setApiUser(admin.id());
    QueryChanges queryChanges = queryChangesProvider.get();

    queryChanges.addQuery("is:open repo:" + project.get());
    List<List<ChangeInfo>> result =
        (List<List<ChangeInfo>>) queryChanges.apply(TopLevelResource.INSTANCE).value();
    assertThat(result).hasSize(1);

    try (ProjectConfigUpdate u = updateProject(allProjects)) {
      ProjectConfig cfg = u.getConfig();
      removeAllBranchPermissions(cfg, Permission.READ);
      u.save();
    }

    queryChanges = queryChangesProvider.get();
    queryChanges.addQuery("is:open repo:" + project.get());
    List<List<ChangeInfo>> result2 =
        (List<List<ChangeInfo>>) queryChanges.apply(TopLevelResource.INSTANCE).value();
    assertThat(result2).hasSize(0);

    queryChanges = queryChangesProvider.get();
    queryChanges.addQuery("is:open repo:" + project.get());
    queryChanges.skipVisibility(true);
    List<List<ChangeInfo>> result3 =
        (List<List<ChangeInfo>>) queryChanges.apply(TopLevelResource.INSTANCE).value();
    assertThat(result3).hasSize(1);
  }

  @Test
  public void testInvalidListChangeOption() throws Exception {
    PushOneCommit.Result r = createChange();
    RestResponse rep = adminRestSession.get("/changes/" + r.getChange().getId() + "/?O=ffffffff");
    rep.assertBadRequest();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void skipVisibility_privateChange() throws Exception {
    TestRepository<InMemoryRepository> userRepo = cloneProject(project, user);
    PushOneCommit.Result result =
        pushFactory.create(user.newIdent(), userRepo).to("refs/for/master");
    requestScopeOperations.setApiUser(user.id());
    gApi.changes().id(result.getChangeId()).setPrivate(true);

    requestScopeOperations.setApiUser(admin.id());
    QueryChanges queryChanges = queryChangesProvider.get();

    queryChanges.addQuery("is:open repo:" + project.get());
    List<List<ChangeInfo>> result2 =
        (List<List<ChangeInfo>>) queryChanges.apply(TopLevelResource.INSTANCE).value();
    assertThat(result2).hasSize(0);

    queryChanges = queryChangesProvider.get();
    queryChanges.addQuery("is:open repo:" + project.get());
    queryChanges.skipVisibility(true);
    List<List<ChangeInfo>> result3 =
        (List<List<ChangeInfo>>) queryChanges.apply(TopLevelResource.INSTANCE).value();
    assertThat(result3).hasSize(1);
  }

  /**
   * This test verifies that querying by a non-visible account doesn't fail.
   *
   * <p>Change queries only return changes that are visible to the calling user. If a non-visible
   * account participated in such a change the existence of this account is known to everyone who
   * can see the change. Hence it's OK to that the account visibility check is skipped when querying
   * changes by non-visible accounts. If the account is visible through any visible change these
   * changes are returned, otherwise the result is empty (see
   * emptyResultWhenQueryingByNonVisibleAccountAndMatchingChangesAreNotVisible()), same as for
   * non-existing accounts (see test emptyResultWhenQueryingByNonExistingAccount()).
   */
  @Test
  @GerritConfig(name = "accounts.visibility", value = "NONE")
  public void changesCanBeQueriesByNonVisibleAccounts() throws Exception {
    String ownerEmail = "owner@example.com";
    Account.Id nonVisibleOwner = accountOperations.newAccount().preferredEmail(ownerEmail).create();

    String reviewerEmail = "reviewer@example.com";
    Account.Id nonVisibleReviewer =
        accountOperations.newAccount().preferredEmail(reviewerEmail).create();

    // Create the change.
    Change.Id changeId = changeOperations.newChange().owner(nonVisibleOwner).create();

    // Add a review.
    requestScopeOperations.setApiUser(nonVisibleReviewer);
    gApi.changes().id(changeId.get()).current().review(ReviewInput.recommend());

    requestScopeOperations.setApiUser(user.id());

    // Verify that user can see the change.
    assertThat(gApi.changes().query("change:" + changeId).get())
        .comparingElementsUsing(hasChangeId())
        .containsExactly(changeId);

    // Verify that user cannot see the other accounts.
    assertThrows(
        ResourceNotFoundException.class, () -> gApi.accounts().id(nonVisibleOwner.get()).get());
    assertThrows(
        ResourceNotFoundException.class, () -> gApi.accounts().id(nonVisibleReviewer.get()).get());

    // Verify that the change is also found if user queries for changes owned/uploaded by
    // nonVisibleOwner.
    assertThat(gApi.changes().query("owner:" + ownerEmail).get())
        .comparingElementsUsing(hasChangeId())
        .containsExactly(changeId);
    assertThat(gApi.changes().query("uploader:" + ownerEmail).get())
        .comparingElementsUsing(hasChangeId())
        .containsExactly(changeId);

    // Verify that the change is also found if user queries for changes reviewed by
    // nonVisibleReviewer.
    assertThat(gApi.changes().query("reviewer:" + reviewerEmail).get())
        .comparingElementsUsing(hasChangeId())
        .containsExactly(changeId);
    assertThat(gApi.changes().query("label:Code-Review+1,user=" + reviewerEmail).get())
        .comparingElementsUsing(hasChangeId())
        .containsExactly(changeId);
  }

  /**
   * This test verifies that an empty result is returned for a query by a non-existing account.
   *
   * <p>Such queries must not return an error so that users cannot probe whether an account exists.
   * Since we return an empty result for non-visible accounts if there are no matched changes or non
   * of the matched changes is visible, users could conclude the existence of a account if we would
   * return an error for non-existing accounts.
   */
  @Test
  public void emptyResultWhenQueryingByNonExistingAccount() throws Exception {
    assertThat(gApi.changes().query("owner:non-existing@example.com").get()).isEmpty();
    assertThat(gApi.changes().query("uploader:non-existing@example.com").get()).isEmpty();
    assertThat(gApi.changes().query("reviewer:non-existing@example.com").get()).isEmpty();
    assertThat(gApi.changes().query("label:Code-Review+1,user=non-existing@example.com").get())
        .isEmpty();
  }

  @Test
  @GerritConfig(name = "accounts.visibility", value = "NONE")
  public void emptyResultWhenQueryingByNonVisibleAccountAndMatchingChangesAreNotVisible()
      throws Exception {
    String ownerEmail = "owner@example.com";
    Account.Id nonVisibleOwner = accountOperations.newAccount().preferredEmail(ownerEmail).create();

    String reviewerEmail = "reviewer@example.com";
    Account.Id nonVisibleReviewer =
        accountOperations.newAccount().preferredEmail(reviewerEmail).create();

    // Create the change.
    Change.Id changeId = changeOperations.newChange().owner(nonVisibleOwner).create();

    // Add a review.
    requestScopeOperations.setApiUser(nonVisibleReviewer);
    gApi.changes().id(changeId.get()).current().review(ReviewInput.recommend());

    // Block read permission so that the change is not visible.
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref("refs/heads/master").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());

    // Verify that user cannot see the change.
    assertThat(gApi.changes().query("change:" + changeId).get()).isEmpty();

    // Verify that user cannot see the other accounts.
    assertThrows(
        ResourceNotFoundException.class, () -> gApi.accounts().id(nonVisibleOwner.get()).get());
    assertThrows(
        ResourceNotFoundException.class, () -> gApi.accounts().id(nonVisibleReviewer.get()).get());

    // Verify that the change is also found if user queries for changes owned/uploaded by
    // nonVisibleOwner.
    assertThat(gApi.changes().query("owner:" + ownerEmail).get()).isEmpty();
    assertThat(gApi.changes().query("uploader:" + ownerEmail).get()).isEmpty();

    // Verify that the change is also found if user queries for changes reviewed by
    // nonVisibleReviewer.
    assertThat(gApi.changes().query("reviewer:" + reviewerEmail).get()).isEmpty();
    assertThat(gApi.changes().query("label:Code-Review+1,user=" + reviewerEmail).get()).isEmpty();
  }

  @Test
  public void emptyResultWhenQueryingByNonVisibleSecondaryEmail() throws Exception {
    String secondaryOwnerEmail = "owner-secondary@example.com";
    Account.Id owner =
        accountOperations
            .newAccount()
            .preferredEmail("owner@example.com")
            .addSecondaryEmail(secondaryOwnerEmail)
            .create();

    String secondaryReviewerEmail = "reviewer-secondary@example.com";
    Account.Id reviewer =
        accountOperations
            .newAccount()
            .preferredEmail("reviewer@example.com")
            .addSecondaryEmail(secondaryReviewerEmail)
            .create();

    // Create the change.
    Change.Id changeId = changeOperations.newChange().owner(owner).create();

    // Add a review.
    requestScopeOperations.setApiUser(reviewer);
    gApi.changes().id(changeId.get()).current().review(ReviewInput.recommend());

    requestScopeOperations.setApiUser(user.id());

    // Verify that user can see the change.
    assertThat(gApi.changes().query("change:" + changeId).get())
        .comparingElementsUsing(hasChangeId())
        .containsExactly(changeId);

    // Verify that user cannot see the other accounts by their secondary email.
    assertThrows(
        ResourceNotFoundException.class, () -> gApi.accounts().id(secondaryOwnerEmail).get());
    assertThrows(
        ResourceNotFoundException.class, () -> gApi.accounts().id(secondaryReviewerEmail).get());

    // Verify that the change is not found if user queries for changes owned/uploaded by the
    // secondary email of the owner that is not visible to user.
    assertThat(gApi.changes().query("owner:" + secondaryOwnerEmail).get()).isEmpty();
    assertThat(gApi.changes().query("uploader:" + secondaryOwnerEmail).get()).isEmpty();

    // Verify that the change is not found if user queries for changes reviewed by the secondary
    // email of the reviewer that is not visible to user.
    assertThat(gApi.changes().query("reviewer:" + secondaryReviewerEmail).get()).isEmpty();
    assertThat(gApi.changes().query("label:Code-Review+1,user=" + secondaryReviewerEmail).get())
        .isEmpty();
  }

  @Test
  public void changesFoundWhenQueryingBySecondaryEmailWithModifyAccountCapability()
      throws Exception {
    testCangesFoundWhenQueryingBySecondaryEmailWithModifyAccountCapability(
        GlobalCapability.MODIFY_ACCOUNT);
  }

  @Test
  public void changesFoundWhenQueryingBySecondaryEmailWithViewSecondaryEmailsCapability()
      throws Exception {
    testCangesFoundWhenQueryingBySecondaryEmailWithModifyAccountCapability(
        GlobalCapability.VIEW_SECONDARY_EMAILS);
  }

  private void testCangesFoundWhenQueryingBySecondaryEmailWithModifyAccountCapability(
      String globalCapability) throws Exception {
    String secondaryOwnerEmail = "owner-secondary@example.com";
    Account.Id owner =
        accountOperations
            .newAccount()
            .preferredEmail("owner@example.com")
            .addSecondaryEmail(secondaryOwnerEmail)
            .create();

    String secondaryReviewerEmail = "reviewer-secondary@example.com";
    Account.Id reviewer =
        accountOperations
            .newAccount()
            .preferredEmail("reviewer@example.com")
            .addSecondaryEmail(secondaryReviewerEmail)
            .create();

    // Create the change.
    Change.Id changeId = changeOperations.newChange().owner(owner).create();

    // Add a review.
    requestScopeOperations.setApiUser(reviewer);
    gApi.changes().id(changeId.get()).current().review(ReviewInput.recommend());

    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(globalCapability).group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());

    // Verify that user can see the other accounts by their secondary email.
    assertThat(gApi.accounts().id(secondaryOwnerEmail).get()._accountId).isEqualTo(owner.get());
    assertThat(gApi.accounts().id(secondaryReviewerEmail).get()._accountId)
        .isEqualTo(reviewer.get());

    // Verify that the change is found if user queries for changes owned/uploaded by the secondary
    // email.
    assertThat(gApi.changes().query("owner:" + secondaryOwnerEmail).get())
        .comparingElementsUsing(hasChangeId())
        .containsExactly(changeId);
    assertThat(gApi.changes().query("uploader:" + secondaryOwnerEmail).get())
        .comparingElementsUsing(hasChangeId())
        .containsExactly(changeId);

    // Verify that the change is found if user queries for changes reviewed by the secondary email.
    assertThat(gApi.changes().query("reviewer:" + secondaryReviewerEmail).get())
        .comparingElementsUsing(hasChangeId())
        .containsExactly(changeId);
    assertThat(gApi.changes().query("label:Code-Review+1,user=" + secondaryReviewerEmail).get())
        .comparingElementsUsing(hasChangeId())
        .containsExactly(changeId);
  }

  @Test
  public void changesFoundWhenQueryingByOwnSecondaryEmail() throws Exception {
    String secondaryOwnerEmail = "owner-secondary@example.com";
    Account.Id owner =
        accountOperations
            .newAccount()
            .preferredEmail("owner@example.com")
            .addSecondaryEmail(secondaryOwnerEmail)
            .create();

    String secondaryReviewerEmail = "reviewer-secondary@example.com";
    Account.Id reviewer =
        accountOperations
            .newAccount()
            .preferredEmail("reviewer@example.com")
            .addSecondaryEmail(secondaryReviewerEmail)
            .create();

    // Create the change.
    Change.Id changeId = changeOperations.newChange().owner(owner).create();

    // Add a review.
    requestScopeOperations.setApiUser(reviewer);
    gApi.changes().id(changeId.get()).current().review(ReviewInput.recommend());

    // Verify that the change is found if owner queries for changes owned/uploaded by their
    // secondary email.
    requestScopeOperations.setApiUser(owner);
    assertThat(gApi.changes().query("owner:" + secondaryOwnerEmail).get())
        .comparingElementsUsing(hasChangeId())
        .containsExactly(changeId);
    assertThat(gApi.changes().query("uploader:" + secondaryOwnerEmail).get())
        .comparingElementsUsing(hasChangeId())
        .containsExactly(changeId);

    // Verify that the change is found if reviewer queries for changes reviewed by their secondary
    // email.
    requestScopeOperations.setApiUser(reviewer);
    assertThat(gApi.changes().query("reviewer:" + secondaryReviewerEmail).get())
        .comparingElementsUsing(hasChangeId())
        .containsExactly(changeId);
    assertThat(gApi.changes().query("label:Code-Review+1,user=" + secondaryReviewerEmail).get())
        .comparingElementsUsing(hasChangeId())
        .containsExactly(changeId);
  }

  private static void assertNoChangeHasMoreChangesSet(List<ChangeInfo> results) {
    for (ChangeInfo info : results) {
      assertThat(info._moreChanges).isNull();
    }
  }

  private static void removeAllBranchPermissions(ProjectConfig cfg, String... permissions) {
    for (AccessSection s : cfg.getAccessSections()) {
      if (s.getName().startsWith("refs/heads/")
          || s.getName().startsWith("refs/for/")
          || s.getName().equals("refs/*")) {
        cfg.upsertAccessSection(
            s.getName(),
            updatedSection -> {
              Arrays.stream(permissions).forEach(p -> updatedSection.remove(Permission.builder(p)));
            });
      }
    }
  }

  private static Correspondence<ChangeInfo, Change.Id> hasChangeId() {
    return NullAwareCorrespondence.transforming(
        changeInfo -> Change.id(changeInfo._number), "hasChangeId");
  }
}
