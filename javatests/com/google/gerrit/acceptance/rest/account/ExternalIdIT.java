// Copyright (C) 2017 The Android Open Source Project
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
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.acceptance.GitUtil.fetch;
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_GERRIT;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_GPGKEY;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_MAILTO;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_UUID;
import static com.google.gerrit.server.account.externalids.testing.ExternalIdTestUtil.insertExternalIdWithEmptyNote;
import static com.google.gerrit.server.account.externalids.testing.ExternalIdTestUtil.insertExternalIdWithInvalidConfig;
import static com.google.gerrit.server.account.externalids.testing.ExternalIdTestUtil.insertExternalIdWithKeyThatDoesntMatchNoteId;
import static com.google.gerrit.server.account.externalids.testing.ExternalIdTestUtil.insertExternalIdWithoutAccountId;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.DuplicateKeyException;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInput;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInput.CheckAccountExternalIdsInput;
import com.google.gerrit.extensions.common.AccountExternalIdInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.externalids.DeleteExternalIdRewriter;
import com.google.gerrit.server.account.externalids.DuplicateExternalIdKeyException;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdFactory;
import com.google.gerrit.server.account.externalids.ExternalIdKeyFactory;
import com.google.gerrit.server.account.externalids.ExternalIdNotes;
import com.google.gerrit.server.account.externalids.ExternalIdReader;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.eclipse.jgit.util.MutableInteger;
import org.junit.Test;

public class ExternalIdIT extends AbstractDaemonTest {
  private static final boolean IS_USER_NAME_CASE_INSENSITIVE_MIGRATION_MODE = false;
  private static final boolean CASE_SENSITIVE_USERNAME = false;
  private static final boolean CASE_INSENSITIVE_USERNAME = true;

  @Inject @ServerInitiated private Provider<AccountsUpdate> accountsUpdateProvider;
  @Inject private ExternalIds externalIds;
  @Inject private ExternalIdReader externalIdReader;
  @Inject private ExternalIdNotes.Factory externalIdNotesFactory;
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ExternalIdKeyFactory externalIdKeyFactory;
  @Inject private DeleteExternalIdRewriter.Factory deleteExternalIdRewriterFactory;
  @Inject private ExternalIdFactory externalIdFactory;

  @Test
  public void getExternalIds() throws Exception {
    Collection<ExternalId> expectedIds = getAccountState(user.id()).externalIds();
    List<AccountExternalIdInfo> expectedIdInfos = toExternalIdInfos(expectedIds);

    RestResponse response = userRestSession.get("/accounts/self/external.ids");
    response.assertOK();

    List<AccountExternalIdInfo> results =
        newGson()
            .fromJson(
                response.getReader(), new TypeToken<List<AccountExternalIdInfo>>() {}.getType());

    assertThat(results).containsExactlyElementsIn(expectedIdInfos);
  }

  @Test
  public void getExternalIdsOfOtherUserNotAllowed() {
    requestScopeOperations.setApiUser(user.id());
    AuthException thrown =
        assertThrows(
            AuthException.class, () -> gApi.accounts().id(admin.id().get()).getExternalIds());
    assertThat(thrown).hasMessageThat().contains("modify account not permitted");
  }

  @Test
  public void getExternalIdsOfOtherUserWithModifyAccount() throws Exception {
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(GlobalCapability.MODIFY_ACCOUNT).group(REGISTERED_USERS))
        .update();

    Collection<ExternalId> expectedIds = getAccountState(admin.id()).externalIds();
    List<AccountExternalIdInfo> expectedIdInfos = toExternalIdInfos(expectedIds);

    RestResponse response = userRestSession.get("/accounts/" + admin.id() + "/external.ids");
    response.assertOK();

    List<AccountExternalIdInfo> results =
        newGson()
            .fromJson(
                response.getReader(), new TypeToken<List<AccountExternalIdInfo>>() {}.getType());

    assertThat(results).containsExactlyElementsIn(expectedIdInfos);
  }

  @Test
  public void deleteExternalIds() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    List<AccountExternalIdInfo> externalIds = gApi.accounts().self().getExternalIds();

    List<String> toDelete = new ArrayList<>();
    List<AccountExternalIdInfo> expectedIds = new ArrayList<>();
    for (AccountExternalIdInfo id : externalIds) {
      if (id.canDelete != null && id.canDelete) {
        toDelete.add(id.identity);
        continue;
      }
      expectedIds.add(id);
    }

    assertThat(toDelete).hasSize(1);

    RestResponse response = userRestSession.post("/accounts/self/external.ids:delete", toDelete);
    response.assertNoContent();
    List<AccountExternalIdInfo> results = gApi.accounts().self().getExternalIds();
    // The external ID in WebSession will not be set for tests, resulting that
    // "mailto:user@example.com" can be deleted while "username:user" can't.
    assertThat(results).hasSize(1);
    assertThat(results).containsExactlyElementsIn(expectedIds);
  }

  @Test
  public void deleteExternalIds_externalIdNotFoundInAnyCommitAfterRewrite() throws Exception {
    requestScopeOperations.setApiUser(user.id());

    MutableInteger i = new MutableInteger();
    String scheme = "valid";

    insertExtId(
        externalIdFactory.createWithPassword(
            externalIdKeyFactory.parse(nextId(scheme, i)),
            user.id(),
            "foo.bar@example.com",
            "secret-password"));
    insertExtId(externalIdFactory.createEmail(user.id(), "bar.baz@example.com"));

    List<AccountExternalIdInfo> externalIds = gApi.accounts().self().getExternalIds();

    List<AccountExternalIdInfo> deletable = new ArrayList<>();
    List<AccountExternalIdInfo> expectedIds = new ArrayList<>();
    for (AccountExternalIdInfo id : externalIds) {
      if (id.canDelete != null && id.canDelete) {
        deletable.add(id);
        continue;
      }
      expectedIds.add(id);
    }

    assertThat(deletable).hasSize(3);

    List<RevCommit> commitsBeforeDelete = getExternalIdRefCommitsInReverseOrder();

    expectedIds.add(deletable.get(0));
    expectedIds.add(deletable.get(2));
    String identityToDelete = deletable.get(1).identity;
    RestResponse response =
        userRestSession.post(
            "/accounts/self/external.ids:delete", ImmutableList.of(identityToDelete));
    response.assertNoContent();
    List<AccountExternalIdInfo> results = gApi.accounts().self().getExternalIds();
    // The external ID in WebSession will not be set for tests, resulting that
    // "mailto:user@example.com" can be deleted while "username:user" can't.
    assertThat(results).hasSize(3);
    assertThat(results).containsExactlyElementsIn(expectedIds);

    assertNotContainedInAnyCommitAfterRewrite(
        commitsBeforeDelete, ExternalId.Key.parse(identityToDelete, true));
  }

  @Test
  public void deleteExternalIdsOfOtherUserNotAllowed() throws Exception {
    List<AccountExternalIdInfo> extIds = gApi.accounts().self().getExternalIds();
    requestScopeOperations.setApiUser(user.id());
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () ->
                gApi.accounts()
                    .id(admin.id().get())
                    .deleteExternalIds(extIds.stream().map(e -> e.identity).collect(toList())));
    assertThat(thrown).hasMessageThat().contains("modify account not permitted");
  }

  @Test
  public void deleteExternalIdOfOtherUserUnderOwnAccount_unprocessableEntity() throws Exception {
    List<AccountExternalIdInfo> extIds = gApi.accounts().self().getExternalIds();
    requestScopeOperations.setApiUser(user.id());
    UnprocessableEntityException thrown =
        assertThrows(
            UnprocessableEntityException.class,
            () ->
                gApi.accounts()
                    .self()
                    .deleteExternalIds(extIds.stream().map(e -> e.identity).collect(toList())));
    assertThat(thrown)
        .hasMessageThat()
        .contains(String.format("External id %s does not exist", extIds.get(0).identity));
  }

  @Test
  public void deleteExternalIdsOfOtherUserWithModifyAccount() throws Exception {
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(GlobalCapability.MODIFY_ACCOUNT).group(REGISTERED_USERS))
        .update();

    List<AccountExternalIdInfo> externalIds = gApi.accounts().self().getExternalIds();

    List<String> toDelete = new ArrayList<>();
    List<AccountExternalIdInfo> expectedIds = new ArrayList<>();
    for (AccountExternalIdInfo id : externalIds) {
      if (id.canDelete != null && id.canDelete) {
        toDelete.add(id.identity);
        continue;
      }
      expectedIds.add(id);
    }

    assertThat(toDelete).hasSize(1);

    requestScopeOperations.setApiUser(user.id());
    RestResponse response =
        userRestSession.post("/accounts/" + admin.id() + "/external.ids:delete", toDelete);
    response.assertNoContent();
    List<AccountExternalIdInfo> results = gApi.accounts().id(admin.id().get()).getExternalIds();
    // The external ID in WebSession will not be set for tests, resulting that
    // "mailto:user@example.com" can be deleted while "username:user" can't.
    assertThat(results).hasSize(1);
    assertThat(results).containsExactlyElementsIn(expectedIds);
  }

  @Test
  public void deleteExternalIdOfPreferredEmail() throws Exception {
    String preferredEmail = gApi.accounts().self().get().email;
    assertThat(preferredEmail).isNotNull();

    gApi.accounts()
        .self()
        .deleteExternalIds(
            ImmutableList.of(externalIdKeyFactory.create(SCHEME_MAILTO, preferredEmail).get()));
    assertThat(gApi.accounts().self().get().email).isNull();
  }

  @Test
  public void deleteExternalIdOfUsernameByNonAdminForbidden() throws Exception {
    List<String> toDelete = new ArrayList<>();
    String externalIdStr = "username:" + user.username();
    toDelete.add(externalIdStr);
    RestResponse response =
        userRestSession.post("/accounts/" + admin.id() + "/external.ids:delete", toDelete);
    response.assertForbidden();
  }

  @Test
  public void deleteExternalIdOfUsernameSelfForbidden() throws Exception {
    List<String> toDelete = new ArrayList<>();
    String externalIdStr = "username:" + admin.username();
    toDelete.add(externalIdStr);
    RestResponse response = adminRestSession.post("/accounts/self/external.ids:delete", toDelete);
    response.assertForbidden();
  }

  @Test
  public void deleteExternalIdOfUsernameByAdmin() throws Exception {
    List<String> toDelete = new ArrayList<>();
    String externalIdStr = "username:" + user.username();
    toDelete.add(externalIdStr);
    RestResponse response =
        adminRestSession.post("/accounts/" + user.id() + "/external.ids:delete", toDelete);
    response.assertNoContent();
    List<AccountExternalIdInfo> results = gApi.accounts().id(user.id().get()).getExternalIds();
    assertThat(results).hasSize(1);
    assertThat(results.get(0).identity).isEqualTo("mailto:user1@example.com");
  }

  @Test
  public void deleteExternalIdOfUsernameMaintainServer() throws Exception {
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(GlobalCapability.MAINTAIN_SERVER).group(REGISTERED_USERS))
        .add(allowCapability(GlobalCapability.MODIFY_ACCOUNT).group(REGISTERED_USERS))
        .update();

    List<String> toDelete = new ArrayList<>();
    TestAccount user2 = accountCreator.user2();
    String externalIdStr = "username:" + user2.username();
    toDelete.add(externalIdStr);
    RestResponse response =
        userRestSession.post("/accounts/" + user2.id() + "/external.ids:delete", toDelete);
    response.assertNoContent();
    List<AccountExternalIdInfo> results = gApi.accounts().id(user2.id().get()).getExternalIds();
    assertThat(results).hasSize(1);
    assertThat(results.get(0).identity).isEqualTo("mailto:user2@example.com");
  }

  @Test
  public void deleteExternalIds_UnprocessableEntity() throws Exception {
    List<String> toDelete = new ArrayList<>();
    String externalIdStr = "mailto:user@domain.com";
    toDelete.add(externalIdStr);
    RestResponse response = userRestSession.post("/accounts/self/external.ids:delete", toDelete);
    response.assertUnprocessableEntity();
    assertThat(response.getEntityContent())
        .isEqualTo(String.format("External id %s does not exist", externalIdStr));
  }

  @Test
  public void fetchExternalIdsBranch() throws Exception {
    final TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers, user);

    // refs/meta/external-ids is only visible to users with the 'Access Database' capability
    TransportException thrown =
        assertThrows(
            TransportException.class, () -> fetch(allUsersRepo, RefNames.REFS_EXTERNAL_IDS));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo("Remote does not have " + RefNames.REFS_EXTERNAL_IDS + " available for fetch.");

    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(GlobalCapability.ACCESS_DATABASE).group(REGISTERED_USERS))
        .update();

    // re-clone to get new request context, otherwise the old global capabilities are still cached
    // in the IdentifiedUser object
    TestRepository<InMemoryRepository> allUsersRepo2 = cloneProject(allUsers, user);
    fetch(allUsersRepo2, RefNames.REFS_EXTERNAL_IDS);
  }

  @Test
  public void pushToExternalIdsBranch() throws Exception {
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(GlobalCapability.ACCESS_DATABASE).group(REGISTERED_USERS))
        .update();

    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    fetch(allUsersRepo, RefNames.REFS_EXTERNAL_IDS + ":" + RefNames.REFS_EXTERNAL_IDS);
    allUsersRepo.reset(RefNames.REFS_EXTERNAL_IDS);

    // different case email is allowed
    ExternalId newExtId = createExternalIdWithOtherCaseEmail("foo:bar");
    addExtId(allUsersRepo, newExtId);
    allUsersRepo.reset(RefNames.REFS_EXTERNAL_IDS);

    List<AccountExternalIdInfo> extIdsBefore = gApi.accounts().self().getExternalIds();

    allowPushOfExternalIds();
    PushResult r = pushHead(allUsersRepo, RefNames.REFS_EXTERNAL_IDS);
    assertThat(r.getRemoteUpdate(RefNames.REFS_EXTERNAL_IDS).getStatus()).isEqualTo(Status.OK);

    List<AccountExternalIdInfo> extIdsAfter = gApi.accounts().self().getExternalIds();
    assertThat(extIdsAfter)
        .containsExactlyElementsIn(
            Iterables.concat(extIdsBefore, ImmutableSet.of(toExternalIdInfo(newExtId))));
  }

  @Test
  public void pushToExternalIdsBranchRejectsExternalIdWithoutAccountId() throws Exception {
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(GlobalCapability.ACCESS_DATABASE).group(REGISTERED_USERS))
        .update();

    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    fetch(allUsersRepo, RefNames.REFS_EXTERNAL_IDS + ":" + RefNames.REFS_EXTERNAL_IDS);
    allUsersRepo.reset(RefNames.REFS_EXTERNAL_IDS);

    insertExternalIdWithoutAccountId(
        allUsersRepo.getRepository(),
        allUsersRepo.getRevWalk(),
        admin.newIdent(),
        admin.id(),
        "foo:bar");
    allUsersRepo.reset(RefNames.REFS_EXTERNAL_IDS);

    allowPushOfExternalIds();
    PushResult r = pushHead(allUsersRepo, RefNames.REFS_EXTERNAL_IDS);
    assertRefUpdateFailure(r.getRemoteUpdate(RefNames.REFS_EXTERNAL_IDS), "invalid external IDs");
  }

  @Test
  public void pushToExternalIdsBranchRejectsExternalIdWithKeyThatDoesntMatchTheNoteId()
      throws Exception {
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(GlobalCapability.ACCESS_DATABASE).group(REGISTERED_USERS))
        .update();

    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    fetch(allUsersRepo, RefNames.REFS_EXTERNAL_IDS + ":" + RefNames.REFS_EXTERNAL_IDS);
    allUsersRepo.reset(RefNames.REFS_EXTERNAL_IDS);

    insertExternalIdWithKeyThatDoesntMatchNoteId(
        allUsersRepo.getRepository(),
        allUsersRepo.getRevWalk(),
        admin.newIdent(),
        admin.id(),
        "foo:bar");
    allUsersRepo.reset(RefNames.REFS_EXTERNAL_IDS);

    allowPushOfExternalIds();
    PushResult r = pushHead(allUsersRepo, RefNames.REFS_EXTERNAL_IDS);
    assertRefUpdateFailure(r.getRemoteUpdate(RefNames.REFS_EXTERNAL_IDS), "invalid external IDs");
  }

  @Test
  public void pushToExternalIdsBranchRejectsExternalIdWithInvalidConfig() throws Exception {
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(GlobalCapability.ACCESS_DATABASE).group(REGISTERED_USERS))
        .update();

    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    fetch(allUsersRepo, RefNames.REFS_EXTERNAL_IDS + ":" + RefNames.REFS_EXTERNAL_IDS);
    allUsersRepo.reset(RefNames.REFS_EXTERNAL_IDS);

    insertExternalIdWithInvalidConfig(
        allUsersRepo.getRepository(), allUsersRepo.getRevWalk(), admin.newIdent(), "foo:bar");
    allUsersRepo.reset(RefNames.REFS_EXTERNAL_IDS);

    allowPushOfExternalIds();
    PushResult r = pushHead(allUsersRepo, RefNames.REFS_EXTERNAL_IDS);
    assertRefUpdateFailure(r.getRemoteUpdate(RefNames.REFS_EXTERNAL_IDS), "invalid external IDs");
  }

  @Test
  public void pushToExternalIdsBranchRejectsExternalIdWithEmptyNote() throws Exception {
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(GlobalCapability.ACCESS_DATABASE).group(REGISTERED_USERS))
        .update();

    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    fetch(allUsersRepo, RefNames.REFS_EXTERNAL_IDS + ":" + RefNames.REFS_EXTERNAL_IDS);
    allUsersRepo.reset(RefNames.REFS_EXTERNAL_IDS);

    insertExternalIdWithEmptyNote(
        allUsersRepo.getRepository(), allUsersRepo.getRevWalk(), admin.newIdent(), "foo:bar");
    allUsersRepo.reset(RefNames.REFS_EXTERNAL_IDS);

    allowPushOfExternalIds();
    PushResult r = pushHead(allUsersRepo, RefNames.REFS_EXTERNAL_IDS);
    assertRefUpdateFailure(r.getRemoteUpdate(RefNames.REFS_EXTERNAL_IDS), "invalid external IDs");
  }

  @Test
  public void pushToExternalIdsBranchRejectsExternalIdForNonExistingAccount() throws Exception {
    testPushToExternalIdsBranchRejectsInvalidExternalId(
        createExternalIdForNonExistingAccount("foo:bar"));
  }

  @Test
  public void pushToExternalIdsBranchRejectsExternalIdWithInvalidEmail() throws Exception {
    testPushToExternalIdsBranchRejectsInvalidExternalId(
        createExternalIdWithInvalidEmail("foo:bar"));
  }

  @Test
  public void pushToExternalIdsBranchRejectsDuplicateEmails() throws Exception {
    testPushToExternalIdsBranchRejectsInvalidExternalId(
        createExternalIdWithDuplicateEmail("foo:bar"));
  }

  @Test
  public void pushToExternalIdsBranchRejectsBadPassword() throws Exception {
    testPushToExternalIdsBranchRejectsInvalidExternalId(createExternalIdWithBadPassword("foo"));
  }

  private void testPushToExternalIdsBranchRejectsInvalidExternalId(ExternalId invalidExtId)
      throws Exception {
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(GlobalCapability.ACCESS_DATABASE).group(REGISTERED_USERS))
        .update();

    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    fetch(allUsersRepo, RefNames.REFS_EXTERNAL_IDS + ":" + RefNames.REFS_EXTERNAL_IDS);
    allUsersRepo.reset(RefNames.REFS_EXTERNAL_IDS);

    addExtId(allUsersRepo, invalidExtId);
    allUsersRepo.reset(RefNames.REFS_EXTERNAL_IDS);

    allowPushOfExternalIds();
    PushResult r = pushHead(allUsersRepo, RefNames.REFS_EXTERNAL_IDS);
    assertRefUpdateFailure(r.getRemoteUpdate(RefNames.REFS_EXTERNAL_IDS), "invalid external IDs");
  }

  @Test
  public void readExternalIdsWhenInvalidExternalIdsExist() throws Exception {
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(GlobalCapability.MODIFY_ACCOUNT).group(REGISTERED_USERS))
        .update();
    requestScopeOperations.resetCurrentApiUser();

    insertValidExternalIds();
    insertInvalidButParsableExternalIds();

    Set<ExternalId> parseableExtIds = externalIds.all();

    insertNonParsableExternalIds();

    Set<ExternalId> extIds = externalIds.all();
    assertThat(extIds).containsExactlyElementsIn(parseableExtIds);

    for (ExternalId parseableExtId : parseableExtIds) {
      Optional<ExternalId> extId = externalIds.get(parseableExtId.key());
      assertThat(extId).hasValue(parseableExtId);
    }
  }

  @Test
  public void checkConsistency() throws Exception {
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(GlobalCapability.ACCESS_DATABASE).group(REGISTERED_USERS))
        .update();
    requestScopeOperations.resetCurrentApiUser();

    insertValidExternalIds();

    ConsistencyCheckInput input = new ConsistencyCheckInput();
    input.checkAccountExternalIds = new CheckAccountExternalIdsInput();
    ConsistencyCheckInfo checkInfo = gApi.config().server().checkConsistency(input);
    assertThat(checkInfo.checkAccountExternalIdsResult.problems).isEmpty();

    Set<ConsistencyProblemInfo> expectedProblems = new HashSet<>();
    expectedProblems.addAll(insertInvalidButParsableExternalIds());
    expectedProblems.addAll(insertNonParsableExternalIds());

    checkInfo = gApi.config().server().checkConsistency(input);
    assertThat(checkInfo.checkAccountExternalIdsResult.problems).hasSize(expectedProblems.size());
    assertThat(checkInfo.checkAccountExternalIdsResult.problems)
        .containsExactlyElementsIn(expectedProblems);
  }

  @Test
  public void checkConsistencyNotAllowed() {
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () -> gApi.config().server().checkConsistency(new ConsistencyCheckInput()));
    assertThat(thrown).hasMessageThat().contains("access database not permitted");
  }

  private ConsistencyProblemInfo consistencyError(String message) {
    return new ConsistencyProblemInfo(ConsistencyProblemInfo.Status.ERROR, message);
  }

  private void insertValidExternalIds() throws Exception {
    MutableInteger i = new MutableInteger();
    String scheme = "valid";

    // create valid external IDs
    insertExtId(
        externalIdFactory.createWithPassword(
            externalIdKeyFactory.parse(nextId(scheme, i)),
            admin.id(),
            "admin.other@example.com",
            "secret-password"));
    insertExtId(externalIdFactory.createEmail(admin.id(), "admin.other@example.com"));
    insertExtId(createExternalIdWithOtherCaseEmail(nextId(scheme, i)));
  }

  private Set<ConsistencyProblemInfo> insertInvalidButParsableExternalIds() throws Exception {
    MutableInteger i = new MutableInteger();
    String scheme = "invalid";

    Set<ConsistencyProblemInfo> expectedProblems = new HashSet<>();
    ExternalId extIdForNonExistingAccount =
        createExternalIdForNonExistingAccount(nextId(scheme, i));
    insertExtIdForNonExistingAccount(extIdForNonExistingAccount);
    expectedProblems.add(
        consistencyError(
            "External ID '"
                + extIdForNonExistingAccount.key().get()
                + "' belongs to account that doesn't exist: "
                + extIdForNonExistingAccount.accountId().get()));

    ExternalId extIdWithInvalidEmail = createExternalIdWithInvalidEmail(nextId(scheme, i));
    insertExtId(extIdWithInvalidEmail);
    expectedProblems.add(
        consistencyError(
            "External ID '"
                + extIdWithInvalidEmail.key().get()
                + "' has an invalid email: "
                + extIdWithInvalidEmail.email()));

    ExternalId extIdWithDuplicateEmail = createExternalIdWithDuplicateEmail(nextId(scheme, i));
    insertExtId(extIdWithDuplicateEmail);
    expectedProblems.add(
        consistencyError(
            "Email '"
                + extIdWithDuplicateEmail.email()
                + "' is not unique, it's used by the following external IDs: '"
                + extIdWithDuplicateEmail.key().get()
                + "', 'mailto:"
                + extIdWithDuplicateEmail.email()
                + "'"));

    ExternalId extIdWithBadPassword = createExternalIdWithBadPassword("admin-username");
    insertExtId(extIdWithBadPassword);
    expectedProblems.add(
        consistencyError(
            "External ID '"
                + extIdWithBadPassword.key().get()
                + "' has an invalid password: unrecognized algorithm"));

    return expectedProblems;
  }

  private Set<ConsistencyProblemInfo> insertNonParsableExternalIds() throws IOException {
    MutableInteger i = new MutableInteger();
    String scheme = "corrupt";

    Set<ConsistencyProblemInfo> expectedProblems = new HashSet<>();
    try (Repository repo = repoManager.openRepository(allUsers);
        RevWalk rw = new RevWalk(repo)) {
      String externalId = nextId(scheme, i);
      String noteId =
          insertExternalIdWithoutAccountId(repo, rw, admin.newIdent(), admin.id(), externalId);
      expectedProblems.add(
          consistencyError(
              "Invalid external ID config for note '"
                  + noteId
                  + "': Value for 'externalId."
                  + externalId
                  + ".accountId' is missing, expected account ID"));

      externalId = nextId(scheme, i);
      noteId =
          insertExternalIdWithKeyThatDoesntMatchNoteId(
              repo, rw, admin.newIdent(), admin.id(), externalId);
      expectedProblems.add(
          consistencyError(
              "Invalid external ID config for note '"
                  + noteId
                  + "': SHA1 of external ID '"
                  + externalId
                  + "' does not match note ID '"
                  + noteId
                  + "'"));

      noteId = insertExternalIdWithInvalidConfig(repo, rw, admin.newIdent(), nextId(scheme, i));
      expectedProblems.add(
          consistencyError(
              "Invalid external ID config for note '" + noteId + "': Invalid line in config file"));

      noteId = insertExternalIdWithEmptyNote(repo, rw, admin.newIdent(), nextId(scheme, i));
      expectedProblems.add(
          consistencyError(
              "Invalid external ID config for note '"
                  + noteId
                  + "': Expected exactly 1 'externalId' section, found 0"));
    }

    return expectedProblems;
  }

  private ExternalId createExternalIdWithOtherCaseEmail(String externalId) {
    return externalIdFactory.createWithPassword(
        externalIdKeyFactory.parse(externalId),
        admin.id(),
        admin.email().toUpperCase(Locale.US),
        "password");
  }

  private ExternalId createExternalIdForNonExistingAccount(String externalId) {
    return externalIdFactory.create(externalIdKeyFactory.parse(externalId), Account.id(1));
  }

  private ExternalId createExternalIdWithInvalidEmail(String externalId) {
    return externalIdFactory.createWithEmail(
        externalIdKeyFactory.parse(externalId), admin.id(), "invalid-email");
  }

  private ExternalId createExternalIdWithDuplicateEmail(String externalId) {
    return externalIdFactory.createWithEmail(
        externalIdKeyFactory.parse(externalId), user.id(), admin.email());
  }

  private ExternalId createExternalIdWithBadPassword(String username) {
    return externalIdFactory.create(
        externalIdKeyFactory.create(SCHEME_USERNAME, username),
        admin.id(),
        null,
        "non-hashed-password-is-not-allowed");
  }

  private static String nextId(String scheme, MutableInteger i) {
    return scheme + ":foo" + ++i.value;
  }

  @Test
  public void readExternalIdWithAccountIdThatCanBeExpressedInKiB() throws Exception {
    ExternalId.Key extIdKey = externalIdKeyFactory.parse("foo:bar");
    Account.Id accountId = Account.id(1024 * 100);
    accountsUpdateProvider
        .get()
        .insert(
            "Create Account with Bad External ID",
            accountId,
            u -> u.addExternalId(externalIdFactory.create(extIdKey, accountId)));
    Optional<ExternalId> extId = externalIds.get(extIdKey);
    assertThat(extId.map(ExternalId::accountId)).hasValue(accountId);
  }

  @Test
  public void byAccountFailIfReadingExternalIdsFails() throws Exception {
    try (AutoCloseable ctx = createFailOnLoadContext()) {
      // update external ID branch so that external IDs need to be reloaded
      insertExtIdBehindGerritsBack(externalIdFactory.create("foo", "bar", admin.id()));

      assertThrows(IOException.class, () -> externalIds.byAccount(admin.id()));
    }
  }

  @Test
  public void byEmailFailIfReadingExternalIdsFails() throws Exception {
    try (AutoCloseable ctx = createFailOnLoadContext()) {
      // update external ID branch so that external IDs need to be reloaded
      insertExtIdBehindGerritsBack(externalIdFactory.create("foo", "bar", admin.id()));

      assertThrows(IOException.class, () -> externalIds.byEmail(admin.email()));
    }
  }

  @Test
  public void byAccountUpdateExternalIdsBehindGerritsBack() throws Exception {
    Set<ExternalId> expectedExternalIds = new HashSet<>(externalIds.byAccount(admin.id()));
    ExternalId newExtId = externalIdFactory.create("foo", "bar", admin.id());
    insertExtIdBehindGerritsBack(newExtId);
    expectedExternalIds.add(newExtId);
    assertThat(externalIds.byAccount(admin.id())).containsExactlyElementsIn(expectedExternalIds);
  }

  @Test
  public void unsetEmail() throws Exception {
    ExternalId extId = externalIdFactory.createWithEmail("x", "1", user.id(), "x@example.com");
    insertExtId(extId);

    ExternalId extIdWithoutEmail = externalIdFactory.create("x", "1", user.id());
    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);
      extIdNotes.upsert(extIdWithoutEmail);
      extIdNotes.commit(md);

      assertThat(extIdNotes.get(extId.key())).hasValue(extIdWithoutEmail);
    }
  }

  @Test
  public void unsetHttpPassword() throws Exception {
    ExternalId extId =
        externalIdFactory.createWithPassword(
            externalIdKeyFactory.create("y", "1"), user.id(), null, "secret");
    insertExtId(extId);

    ExternalId extIdWithoutPassword = externalIdFactory.create("y", "1", user.id());
    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);
      extIdNotes.upsert(extIdWithoutPassword);
      extIdNotes.commit(md);

      assertThat(extIdNotes.get(extId.key())).hasValue(extIdWithoutPassword);
    }
  }

  @Test
  @GerritConfig(name = "auth.userNameCaseInsensitive", value = "true")
  public void createCaseInsensitiveExternalId_DuplicateKey() throws Exception {
    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);
      testCaseInsensitiveExternalIdKey(md, extIdNotes, SCHEME_USERNAME, "JohnDoe", Account.id(42));
      assertThrows(
          DuplicateExternalIdKeyException.class,
          () ->
              extIdNotes.insert(
                  externalIdFactory.create(SCHEME_USERNAME, "johndoe", Account.id(23))));
    }
  }

  @Test
  @GerritConfig(name = "auth.userNameCaseInsensitive", value = "true")
  public void createCaseInsensitiveExternalId_SchemeWithUsername() throws Exception {
    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);

      testCaseInsensitiveExternalIdKey(md, extIdNotes, SCHEME_USERNAME, "janedoe", Account.id(66));
      testCaseInsensitiveExternalIdKey(md, extIdNotes, SCHEME_GERRIT, "JaneDoe", Account.id(66));
    }
  }

  @Test
  @GerritConfig(name = "auth.userNameCaseInsensitive", value = "true")
  @GerritConfig(name = "auth.userNameCaseInsensitiveMigrationMode", value = "true")
  public void createCaseInsensitiveMigrationModeExternalIdBeforeTheMigration() throws Exception {
    Account.Id accountId = Account.id(66);
    boolean isUserNameCaseInsensitive = false;

    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);

      createExternalId(
          md, extIdNotes, SCHEME_GERRIT, "JaneDoe", accountId, isUserNameCaseInsensitive);
      createExternalId(
          md, extIdNotes, SCHEME_USERNAME, "JaneDoe", accountId, isUserNameCaseInsensitive);

      assertThat(getAccountId(extIdNotes, SCHEME_GERRIT, "JaneDoe")).isEqualTo(accountId.get());
      assertThat(getExternalId(extIdNotes, SCHEME_GERRIT, "janedoe").isPresent()).isFalse();

      assertThat(getAccountId(extIdNotes, SCHEME_USERNAME, "JaneDoe")).isEqualTo(accountId.get());
      assertThat(getExternalId(extIdNotes, SCHEME_USERNAME, "janedoe").isPresent()).isFalse();
    }
  }

  @Test
  @GerritConfig(name = "auth.userNameCaseInsensitive", value = "true")
  @GerritConfig(name = "auth.userNameCaseInsensitiveMigrationMode", value = "true")
  public void createCaseInsensitiveMigrationModeExternalIdAccountAfterTheMigration()
      throws Exception {
    Account.Id accountId = Account.id(66);
    boolean isUserNameCaseInsensitive = true;

    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);

      createExternalId(
          md, extIdNotes, SCHEME_GERRIT, "JaneDoe", accountId, isUserNameCaseInsensitive);
      createExternalId(
          md, extIdNotes, SCHEME_USERNAME, "JaneDoe", accountId, isUserNameCaseInsensitive);

      assertThat(getAccountId(extIdNotes, SCHEME_GERRIT, "JaneDoe")).isEqualTo(accountId.get());
      assertThat(getAccountId(extIdNotes, SCHEME_GERRIT, "janedoe")).isEqualTo(accountId.get());

      assertThat(getAccountId(extIdNotes, SCHEME_USERNAME, "JaneDoe")).isEqualTo(accountId.get());
      assertThat(getAccountId(extIdNotes, SCHEME_USERNAME, "janedoe")).isEqualTo(accountId.get());
    }
  }

  @Test
  @GerritConfig(name = "auth.userNameCaseInsensitive", value = "true")
  @GerritConfig(name = "auth.userNameCaseInsensitiveMigrationMode", value = "true")
  public void shouldTolerateDuplicateExternalIdsWhenInMigrationMode() throws Exception {
    Account.Id firstAccountId = Account.id(1);
    Account.Id secondAccountId = Account.id(2);

    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);

      createExternalId(
          md, extIdNotes, SCHEME_GERRIT, "janedoe", firstAccountId, CASE_SENSITIVE_USERNAME);
      createExternalId(
          md, extIdNotes, SCHEME_GERRIT, "JaneDoe", secondAccountId, CASE_SENSITIVE_USERNAME);

      ExternalId.Key firstAccountExternalId =
          externalIdKeyFactory.create(SCHEME_GERRIT, "janedoe", CASE_INSENSITIVE_USERNAME);
      assertThat(externalIds.get(firstAccountExternalId).get().accountId())
          .isEqualTo(firstAccountId);

      ExternalId.Key secondAccountExternalId =
          externalIdKeyFactory.create(SCHEME_GERRIT, "JaneDoe", CASE_INSENSITIVE_USERNAME);
      assertThat(externalIds.get(secondAccountExternalId).get().accountId())
          .isEqualTo(secondAccountId);
    }
  }

  @Test
  @GerritConfig(name = "auth.userNameCaseInsensitive", value = "true")
  @GerritConfig(name = "auth.userNameCaseInsensitiveMigrationMode", value = "true")
  public void createCaseInsensitiveMigrationModeExternalIdAccountDuringTheMigration()
      throws Exception {
    Account.Id accountId = Account.id(66);
    boolean userNameCaseInsensitive = true;

    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);

      createExternalId(
          md, extIdNotes, SCHEME_GERRIT, "JonDoe", accountId, !userNameCaseInsensitive);
      createExternalId(
          md, extIdNotes, SCHEME_USERNAME, "JonDoe", accountId, !userNameCaseInsensitive);

      createExternalId(
          md, extIdNotes, SCHEME_GERRIT, "JaneDoe", accountId, userNameCaseInsensitive);
      createExternalId(
          md, extIdNotes, SCHEME_USERNAME, "JaneDoe", accountId, userNameCaseInsensitive);

      assertThat(getAccountId(extIdNotes, SCHEME_GERRIT, "JonDoe")).isEqualTo(accountId.get());
      assertThat(getExternalId(extIdNotes, SCHEME_GERRIT, "jondoe").isPresent()).isFalse();

      assertThat(getAccountId(extIdNotes, SCHEME_USERNAME, "JonDoe")).isEqualTo(accountId.get());

      assertThat(getExternalId(extIdNotes, SCHEME_USERNAME, "jondoe").isPresent()).isFalse();

      assertThat(getAccountId(extIdNotes, SCHEME_GERRIT, "JaneDoe")).isEqualTo(accountId.get());

      assertThat(getAccountId(extIdNotes, SCHEME_GERRIT, "janedoe")).isEqualTo(accountId.get());

      assertThat(getAccountId(extIdNotes, SCHEME_USERNAME, "JaneDoe")).isEqualTo(accountId.get());
      assertThat(getAccountId(extIdNotes, SCHEME_USERNAME, "janedoe")).isEqualTo(accountId.get());
    }
  }

  protected int getAccountId(ExternalIdNotes extIdNotes, String scheme, String id)
      throws IOException, ConfigInvalidException {
    return getExternalId(extIdNotes, scheme, id).get().accountId().get();
  }

  protected Optional<ExternalId> getExternalId(ExternalIdNotes extIdNotes, String scheme, String id)
      throws IOException, ConfigInvalidException {
    return extIdNotes.get(externalIdKeyFactory.create(scheme, id));
  }

  @Test
  @GerritConfig(name = "auth.userNameCaseInsensitive", value = "true")
  public void createCaseSensitiveExternalId_SchemeWithoutUsername() throws Exception {
    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);

      testCaseSensitiveExternalIdKey(md, extIdNotes, SCHEME_MAILTO, "Jane@doe.com", Account.id(66));
      testCaseSensitiveExternalIdKey(md, extIdNotes, SCHEME_UUID, "1234ABCD", Account.id(66));
      testCaseSensitiveExternalIdKey(md, extIdNotes, SCHEME_GPGKEY, "1234ABCD", Account.id(66));
    }
  }

  private void testCaseSensitiveExternalIdKey(
      MetaDataUpdate md, ExternalIdNotes extIdNotes, String scheme, String id, Account.Id accountId)
      throws DuplicateExternalIdKeyException, IOException, ConfigInvalidException {
    ExternalId extId = externalIdFactory.create(scheme, id, accountId);
    extIdNotes.insert(extId);
    extIdNotes.commit(md);
    assertThat(getAccountId(extIdNotes, scheme, id)).isEqualTo(accountId.get());
    assertThat(getExternalId(extIdNotes, scheme, id.toLowerCase()).isPresent()).isFalse();
  }

  private void testCaseInsensitiveExternalIdKey(
      MetaDataUpdate md, ExternalIdNotes extIdNotes, String scheme, String id, Account.Id accountId)
      throws DuplicateExternalIdKeyException, IOException, ConfigInvalidException {
    ExternalId extId = externalIdFactory.create(scheme, id, accountId);
    extIdNotes.insert(extId);
    extIdNotes.commit(md);
    assertThat(getAccountId(extIdNotes, scheme, id)).isEqualTo(accountId.get());
    assertThat(getAccountId(extIdNotes, scheme, id.toLowerCase())).isEqualTo(accountId.get());
  }

  /**
   * Create external id object
   *
   * <p>This method skips gerrit.config auth.userNameCaseInsensitiveMigrationMode and allow to
   * create case sensitive/insensitive external id
   */
  protected void createExternalId(
      MetaDataUpdate md,
      ExternalIdNotes extIdNotes,
      String scheme,
      String id,
      Account.Id accountId,
      boolean isUserNameCaseInsensitive)
      throws IOException {
    ExternalId extId =
        externalIdFactory.create(
            externalIdKeyFactory.create(scheme, id, isUserNameCaseInsensitive), accountId);
    extIdNotes.insert(extId);
    extIdNotes.commit(md);
  }

  private void insertExtId(ExternalId extId) throws Exception {
    accountsUpdateProvider
        .get()
        .update("Add External ID", extId.accountId(), u -> u.addExternalId(extId));
  }

  private void insertExtIdForNonExistingAccount(ExternalId extId) throws Exception {
    // Cannot use AccountsUpdate to insert an external ID for a non-existing account.
    try (Repository repo = repoManager.openRepository(allUsers);
        MetaDataUpdate update = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(repo);
      extIdNotes.insert(extId);
      extIdNotes.commit(update);
      externalIdNotesFactory.updateExternalIdCacheAndMaybeReindexAccounts(
          extIdNotes, ImmutableList.of());
    }
  }

  private void insertExtIdBehindGerritsBack(ExternalId extId) throws Exception {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      // Inserting an external ID "behind Gerrit's back" means that the caches are not updated.
      ExternalIdNotes extIdNotes =
          ExternalIdNotes.load(
              allUsers,
              repo,
              externalIdFactory,
              deleteExternalIdRewriterFactory,
              IS_USER_NAME_CASE_INSENSITIVE_MIGRATION_MODE);
      extIdNotes.insert(extId);
      try (MetaDataUpdate metaDataUpdate =
          new MetaDataUpdate(GitReferenceUpdated.DISABLED, null, repo)) {
        metaDataUpdate.getCommitBuilder().setAuthor(admin.newIdent());
        metaDataUpdate.getCommitBuilder().setCommitter(admin.newIdent());
        extIdNotes.commit(metaDataUpdate);
      }
    }
  }

  private void addExtId(TestRepository<?> testRepo, ExternalId... extIds)
      throws IOException, DuplicateKeyException, ConfigInvalidException {
    ExternalIdNotes extIdNotes = externalIdNotesFactory.load(testRepo.getRepository());
    extIdNotes.insert(Arrays.asList(extIds));
    try (MetaDataUpdate metaDataUpdate =
        new MetaDataUpdate(GitReferenceUpdated.DISABLED, null, testRepo.getRepository())) {
      metaDataUpdate.getCommitBuilder().setAuthor(admin.newIdent());
      metaDataUpdate.getCommitBuilder().setCommitter(admin.newIdent());
      extIdNotes.commit(metaDataUpdate);
      externalIdNotesFactory.updateExternalIdCacheAndMaybeReindexAccounts(
          extIdNotes, ImmutableList.of());
    }
  }

  private List<AccountExternalIdInfo> toExternalIdInfos(Collection<ExternalId> extIds) {
    return extIds.stream().map(this::toExternalIdInfo).collect(toList());
  }

  private AccountExternalIdInfo toExternalIdInfo(ExternalId extId) {
    AccountExternalIdInfo info = new AccountExternalIdInfo();
    info.identity = extId.key().get();
    info.emailAddress = extId.email();
    info.canDelete = !extId.isScheme(SCHEME_USERNAME) ? true : null;
    info.trusted =
        extId.isScheme(SCHEME_MAILTO)
                || extId.isScheme(SCHEME_UUID)
                || extId.isScheme(SCHEME_USERNAME)
            ? true
            : null;
    return info;
  }

  private void allowPushOfExternalIds() {
    projectOperations
        .project(allUsers)
        .forUpdate()
        .add(allow(Permission.READ).ref(RefNames.REFS_EXTERNAL_IDS).group(adminGroupUuid()))
        .add(allow(Permission.PUSH).ref(RefNames.REFS_EXTERNAL_IDS).group(adminGroupUuid()))
        .update();
  }

  private void assertRefUpdateFailure(RemoteRefUpdate update, String msg) {
    assertThat(update.getStatus()).isEqualTo(Status.REJECTED_OTHER_REASON);
    assertThat(update.getMessage()).contains(msg);
  }

  private AutoCloseable createFailOnLoadContext() {
    externalIdReader.setFailOnLoad(true);
    return () -> externalIdReader.setFailOnLoad(false);
  }
}
