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
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.util.stream.Collectors.toList;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.UseClockStep;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.restapi.change.QueryChanges;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.List;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Test;

@NoHttpd
public class QueryChangesIT extends AbstractDaemonTest {
  @Inject private AccountOperations accountOperations;
  @Inject private ProjectOperations projectOperations;
  @Inject private Provider<QueryChanges> queryChangesProvider;

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

    List<Integer> firstResultIds =
        ImmutableList.of(result.get(0).get(0)._number, result.get(0).get(1)._number);
    assertThat(firstResultIds).containsExactly(numericId1, numericId2);
    assertThat(result.get(1).get(0)._number).isEqualTo(numericId2);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void moreChangesIndicatorDoesNotWronglyCopyToUnrelatedChanges() throws Exception {
    String queryWithMoreChanges = "is:wip limit:1 repo:" + project.get();
    String queryWithNoMoreChanges = "is:open limit:10 repo:" + project.get();
    createChange().getChangeId();
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
    createChange(testRepo).getChange().getId().get();
    createChange(testRepo).getChange().getId().get();
    int changeId3 = createChange(testRepo).getChange().getId().get();
    int changeId4 = createChange(testRepo).getChange().getId().get();

    // Create hidden project.
    Project.NameKey hiddenProject = projectOperations.newProject().create();
    projectOperations
        .project(hiddenProject)
        .forUpdate()
        .add(block(Permission.READ).ref("refs/*").group(REGISTERED_USERS))
        .update();
    TestRepository<InMemoryRepository> hiddenRepo = cloneProject(hiddenProject, admin);

    // Create 2 hidden changes.
    createChange(hiddenRepo);
    createChange(hiddenRepo);

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

    List<Integer> firstResultIds =
        ImmutableList.of(result.get(0).get(0)._number, result.get(0).get(1)._number);
    assertThat(firstResultIds).containsExactly(numericId1, numericId2);
    assertThat(result.get(1).get(0)._number).isEqualTo(numericId2);
  }

  private static void assertNoChangeHasMoreChangesSet(List<ChangeInfo> results) {
    for (ChangeInfo info : results) {
      assertThat(info._moreChanges).isNull();
    }
  }
}
