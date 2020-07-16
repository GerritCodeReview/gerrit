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
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.acceptance.GitUtil.fetch;
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;
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
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdNotes;
import com.google.gerrit.server.account.externalids.ExternalIdReader;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.testing.ConfigSuite;
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
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FooterLine;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.eclipse.jgit.util.MutableInteger;
import org.junit.Test;

public class ExternalIdIT extends AbstractDaemonTest {
  @Inject @ServerInitiated private Provider<AccountsUpdate> accountsUpdateProvider;
  @Inject private ExternalIds externalIds;
  @Inject private ExternalIdReader externalIdReader;
  @Inject private ExternalIdNotes.Factory externalIdNotesFactory;
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @ConfigSuite.Default
  public static Config partialCacheReloadingEnabled() {
    Config cfg = new Config();
    cfg.setBoolean("cache", "external_ids_map", "enablePartialReloads", true);
    return cfg;
  }

  @ConfigSuite.Config
  public static Config partialCacheReloadingDisabled() {
    Config cfg = new Config();
    cfg.setBoolean("cache", "external_ids_map", "enablePartialReloads", false);
    return cfg;
  }

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
  public void getExternalIdsOfOtherUserNotAllowed() throws Exception {
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
  public void deleteExternalIdOfOtherUserUnderOwnAccount_UnprocessableEntity() throws Exception {
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
            ImmutableList.of(ExternalId.Key.create(SCHEME_MAILTO, preferredEmail).get()));
    assertThat(gApi.accounts().self().get().email).isNull();
  }

  @Test
  public void deleteExternalIds_Conflict() throws Exception {
    List<String> toDelete = new ArrayList<>();
    String externalIdStr = "username:" + user.username();
    toDelete.add(externalIdStr);
    RestResponse response = userRestSession.post("/accounts/self/external.ids:delete", toDelete);
    response.assertConflict();
    assertThat(response.getEntityContent())
        .isEqualTo(String.format("External id %s cannot be deleted", externalIdStr));
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
  public void checkConsistencyNotAllowed() throws Exception {
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
        ExternalId.createWithPassword(
            ExternalId.Key.parse(nextId(scheme, i)),
            admin.id(),
            "admin.other@example.com",
            "secret-password"));
    insertExtId(ExternalId.createEmail(admin.id(), "admin.other@example.com"));
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
    return ExternalId.createWithPassword(
        ExternalId.Key.parse(externalId),
        admin.id(),
        admin.email().toUpperCase(Locale.US),
        "password");
  }

  private ExternalId createExternalIdForNonExistingAccount(String externalId) {
    return ExternalId.create(ExternalId.Key.parse(externalId), Account.id(1));
  }

  private ExternalId createExternalIdWithInvalidEmail(String externalId) {
    return ExternalId.createWithEmail(
        ExternalId.Key.parse(externalId), admin.id(), "invalid-email");
  }

  private ExternalId createExternalIdWithDuplicateEmail(String externalId) {
    return ExternalId.createWithEmail(ExternalId.Key.parse(externalId), user.id(), admin.email());
  }

  private ExternalId createExternalIdWithBadPassword(String username) {
    return ExternalId.create(
        ExternalId.Key.create(SCHEME_USERNAME, username),
        admin.id(),
        null,
        "non-hashed-password-is-not-allowed");
  }

  private static String nextId(String scheme, MutableInteger i) {
    return scheme + ":foo" + ++i.value;
  }

  @Test
  public void readExternalIdWithAccountIdThatCanBeExpressedInKiB() throws Exception {
    ExternalId.Key extIdKey = ExternalId.Key.parse("foo:bar");
    Account.Id accountId = Account.id(1024 * 100);
    accountsUpdateProvider
        .get()
        .insert(
            "Create Account with Bad External ID",
            accountId,
            u -> u.addExternalId(ExternalId.create(extIdKey, accountId)));
    Optional<ExternalId> extId = externalIds.get(extIdKey);
    assertThat(extId.map(ExternalId::accountId)).hasValue(accountId);
  }

  @Test
  public void checkNoReloadAfterUpdate() throws Exception {
    Set<ExternalId> expectedExtIds = new HashSet<>(externalIds.byAccount(admin.id()));
    try (AutoCloseable ctx = createFailOnLoadContext()) {
      // insert external ID
      ExternalId extId = ExternalId.create("foo", "bar", admin.id());
      insertExtId(extId);
      expectedExtIds.add(extId);
      assertThat(externalIds.byAccount(admin.id())).containsExactlyElementsIn(expectedExtIds);

      // update external ID
      expectedExtIds.remove(extId);
      ExternalId extId2 =
          ExternalId.createWithEmail("foo", "bar", admin.id(), "foo.bar@example.com");
      accountsUpdateProvider
          .get()
          .update("Update External ID", admin.id(), u -> u.updateExternalId(extId2));
      expectedExtIds.add(extId2);
      assertThat(externalIds.byAccount(admin.id())).containsExactlyElementsIn(expectedExtIds);

      // delete external ID
      accountsUpdateProvider
          .get()
          .update("Delete External ID", admin.id(), u -> u.deleteExternalId(extId));
      expectedExtIds.remove(extId2);
      assertThat(externalIds.byAccount(admin.id())).containsExactlyElementsIn(expectedExtIds);
    }
  }

  @Test
  public void byAccountFailIfReadingExternalIdsFails() throws Exception {
    assume().that(isPartialCacheReloadingEnabled()).isFalse();

    try (AutoCloseable ctx = createFailOnLoadContext()) {
      // update external ID branch so that external IDs need to be reloaded
      insertExtIdBehindGerritsBack(ExternalId.create("foo", "bar", admin.id()));

      assertThrows(IOException.class, () -> externalIds.byAccount(admin.id()));
    }
  }

  @Test
  public void byEmailFailIfReadingExternalIdsFails() throws Exception {
    assume().that(isPartialCacheReloadingEnabled()).isFalse();

    try (AutoCloseable ctx = createFailOnLoadContext()) {
      // update external ID branch so that external IDs need to be reloaded
      insertExtIdBehindGerritsBack(ExternalId.create("foo", "bar", admin.id()));

      assertThrows(IOException.class, () -> externalIds.byEmail(admin.email()));
    }
  }

  @Test
  public void byAccountUpdateExternalIdsBehindGerritsBack() throws Exception {
    Set<ExternalId> expectedExternalIds = new HashSet<>(externalIds.byAccount(admin.id()));
    ExternalId newExtId = ExternalId.create("foo", "bar", admin.id());
    insertExtIdBehindGerritsBack(newExtId);
    expectedExternalIds.add(newExtId);
    assertThat(externalIds.byAccount(admin.id())).containsExactlyElementsIn(expectedExternalIds);
  }

  @Test
  public void unsetEmail() throws Exception {
    ExternalId extId = ExternalId.createWithEmail("x", "1", user.id(), "x@example.com");
    insertExtId(extId);

    ExternalId extIdWithoutEmail = ExternalId.create("x", "1", user.id());
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
        ExternalId.createWithPassword(ExternalId.Key.create("y", "1"), user.id(), null, "secret");
    insertExtId(extId);

    ExternalId extIdWithoutPassword = ExternalId.create("y", "1", user.id());
    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);
      extIdNotes.upsert(extIdWithoutPassword);
      extIdNotes.commit(md);

      assertThat(extIdNotes.get(extId.key())).hasValue(extIdWithoutPassword);
    }
  }

  @Test
  public void footers() throws Exception {
    // Insert external ID for different accounts
    TestAccount user1 = accountCreator.create("user1");
    TestAccount user2 = accountCreator.create("user2");
    ExternalId extId1 = ExternalId.create("foo", "1", user1.id());
    ExternalId extId2 = ExternalId.create("foo", "2", user1.id());
    ExternalId extId3 = ExternalId.create("foo", "3", user2.id());
    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);
      extIdNotes.insert(ImmutableSet.of(extId1, extId2, extId3));
      RevCommit c = extIdNotes.commit(md);
      assertThat(getFooters(c))
          .containsExactly("Account: " + user1.id(), "Account: " + user2.id())
          .inOrder();
    }

    // Insert external ID with different emails
    ExternalId extId4 = ExternalId.createWithEmail("foo", "4", user1.id(), "foo4@example.com");
    ExternalId extId5 = ExternalId.createWithEmail("foo", "5", user2.id(), "foo5@example.com");
    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);
      extIdNotes.insert(ImmutableSet.of(extId4, extId5));
      RevCommit c = extIdNotes.commit(md);
      assertThat(getFooters(c))
          .containsExactly(
              "Account: " + user1.id(),
              "Account: " + user2.id(),
              "Email: foo4@example.com",
              "Email: foo5@example.com")
          .inOrder();
    }

    // Update external ID - Add Email
    ExternalId extId1a = ExternalId.createWithEmail("foo", "1", user1.id(), "foo1@example.com");
    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);
      extIdNotes.upsert(extId1a);
      RevCommit c = extIdNotes.commit(md);
      assertThat(getFooters(c))
          .containsExactly("Account: " + user1.id(), "Email: foo1@example.com")
          .inOrder();
    }

    // Update external ID - Remove Email
    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);
      extIdNotes.upsert(extId1);
      RevCommit c = extIdNotes.commit(md);
      assertThat(getFooters(c))
          .containsExactly("Account: " + user1.id(), "Email: foo1@example.com")
          .inOrder();
    }

    // Delete external IDs
    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);
      extIdNotes.delete(ImmutableSet.of(extId1, extId5));
      RevCommit c = extIdNotes.commit(md);
      assertThat(getFooters(c))
          .containsExactly(
              "Account: " + user1.id(), "Account: " + user2.id(), "Email: foo5@example.com")
          .inOrder();
    }

    // Delete external ID by key without email
    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);
      extIdNotes.delete(extId2.accountId(), extId2.key());
      RevCommit c = extIdNotes.commit(md);
      assertThat(getFooters(c)).containsExactly("Account: " + user1.id()).inOrder();
    }

    // Delete external ID by key with email
    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);
      extIdNotes.delete(extId4.accountId(), extId4.key());
      RevCommit c = extIdNotes.commit(md);
      assertThat(getFooters(c))
          .containsExactly("Account: " + user1.id(), "Email: foo4@example.com")
          .inOrder();
    }
  }

  private boolean isPartialCacheReloadingEnabled() {
    return cfg.getBoolean("cache", "external_ids_map", "enablePartialReloads", true);
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
      extIdNotes.updateCaches();
    }
  }

  private void insertExtIdBehindGerritsBack(ExternalId extId) throws Exception {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      // Inserting an external ID "behind Gerrit's back" means that the caches are not updated.
      ExternalIdNotes extIdNotes = ExternalIdNotes.loadNoCacheUpdate(allUsers, repo);
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
      extIdNotes.updateCaches();
    }
  }

  private List<String> getFooters(RevCommit c) {
    return c.getFooterLines().stream().map(FooterLine::toString).collect(toList());
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
