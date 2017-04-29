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
import static com.google.gerrit.acceptance.GitUtil.fetch;
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_MAILTO;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_UUID;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.junit.Assert.fail;

import com.github.rholder.retry.BlockStrategy;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInput;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInput.CheckAccountExternalIdsInput;
import com.google.gerrit.extensions.common.AccountExternalIdInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.account.externalids.DisabledExternalIdCache;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdReader;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.account.externalids.ExternalIdsUpdate;
import com.google.gerrit.server.account.externalids.ExternalIdsUpdate.RefsMetaExternalIdsUpdate;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.LockFailureException;
import com.google.gson.reflect.TypeToken;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.eclipse.jgit.util.MutableInteger;
import org.junit.Test;

@Sandboxed
public class ExternalIdIT extends AbstractDaemonTest {
  @Inject private AllUsersName allUsers;
  @Inject private ExternalIdsUpdate.Server extIdsUpdate;
  @Inject private ExternalIds externalIds;
  @Inject private ExternalIdReader externalIdReader;
  @Inject private MetricMaker metricMaker;

  @Test
  public void getExternalIds() throws Exception {
    Collection<ExternalId> expectedIds = accountCache.get(user.getId()).getExternalIds();
    List<AccountExternalIdInfo> expectedIdInfos = toExternalIdInfos(expectedIds);

    RestResponse response = userRestSession.get("/accounts/self/external.ids");
    response.assertOK();

    List<AccountExternalIdInfo> results =
        newGson()
            .fromJson(
                response.getReader(), new TypeToken<List<AccountExternalIdInfo>>() {}.getType());

    Collections.sort(expectedIdInfos);
    Collections.sort(results);
    assertThat(results).containsExactlyElementsIn(expectedIdInfos);
  }

  @Test
  public void getExternalIdsOfOtherUserNotAllowed() throws Exception {
    setApiUser(user);
    exception.expect(AuthException.class);
    exception.expectMessage("not allowed to get external IDs");
    gApi.accounts().id(admin.id.get()).getExternalIds();
  }

  @Test
  public void getExternalIdsOfOtherUserWithAccessDatabase() throws Exception {
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);

    Collection<ExternalId> expectedIds = accountCache.get(admin.getId()).getExternalIds();
    List<AccountExternalIdInfo> expectedIdInfos = toExternalIdInfos(expectedIds);

    RestResponse response = userRestSession.get("/accounts/" + admin.id + "/external.ids");
    response.assertOK();

    List<AccountExternalIdInfo> results =
        newGson()
            .fromJson(
                response.getReader(), new TypeToken<List<AccountExternalIdInfo>>() {}.getType());

    Collections.sort(expectedIdInfos);
    Collections.sort(results);
    assertThat(results).containsExactlyElementsIn(expectedIdInfos);
  }

  @Test
  public void deleteExternalIds() throws Exception {
    setApiUser(user);
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
    setApiUser(user);
    exception.expect(AuthException.class);
    exception.expectMessage("not allowed to delete external IDs");
    gApi.accounts()
        .id(admin.id.get())
        .deleteExternalIds(extIds.stream().map(e -> e.identity).collect(toList()));
  }

  @Test
  public void deleteExternalIdsOfOtherUserWithAccessDatabase() throws Exception {
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);

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

    setApiUser(user);
    RestResponse response =
        userRestSession.post("/accounts/" + admin.id + "/external.ids:delete", toDelete);
    response.assertNoContent();
    List<AccountExternalIdInfo> results = gApi.accounts().id(admin.id.get()).getExternalIds();
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
    String externalIdStr = "username:" + user.username;
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
    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers, user);

    // refs/meta/external-ids is only visible to users with the 'Access Database' capability
    try {
      fetch(allUsersRepo, RefNames.REFS_EXTERNAL_IDS);
      fail("expected TransportException");
    } catch (TransportException e) {
      assertThat(e.getMessage())
          .isEqualTo(
              "Remote does not have " + RefNames.REFS_EXTERNAL_IDS + " available for fetch.");
    }

    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);

    // re-clone to get new request context, otherwise the old global capabilities are still cached
    // in the IdentifiedUser object
    allUsersRepo = cloneProject(allUsers, user);
    fetch(allUsersRepo, RefNames.REFS_EXTERNAL_IDS);
  }

  @Test
  public void pushToExternalIdsBranch() throws Exception {
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
    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    fetch(allUsersRepo, RefNames.REFS_EXTERNAL_IDS + ":" + RefNames.REFS_EXTERNAL_IDS);
    allUsersRepo.reset(RefNames.REFS_EXTERNAL_IDS);

    insertExternalIdWithoutAccountId(
        allUsersRepo.getRepository(), allUsersRepo.getRevWalk(), "foo:bar");
    allUsersRepo.reset(RefNames.REFS_EXTERNAL_IDS);

    allowPushOfExternalIds();
    PushResult r = pushHead(allUsersRepo, RefNames.REFS_EXTERNAL_IDS);
    assertRefUpdateFailure(r.getRemoteUpdate(RefNames.REFS_EXTERNAL_IDS), "invalid external IDs");
  }

  @Test
  public void pushToExternalIdsBranchRejectsExternalIdWithKeyThatDoesntMatchTheNoteId()
      throws Exception {
    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    fetch(allUsersRepo, RefNames.REFS_EXTERNAL_IDS + ":" + RefNames.REFS_EXTERNAL_IDS);
    allUsersRepo.reset(RefNames.REFS_EXTERNAL_IDS);

    insertExternalIdWithKeyThatDoesntMatchNoteId(
        allUsersRepo.getRepository(), allUsersRepo.getRevWalk(), "foo:bar");
    allUsersRepo.reset(RefNames.REFS_EXTERNAL_IDS);

    allowPushOfExternalIds();
    PushResult r = pushHead(allUsersRepo, RefNames.REFS_EXTERNAL_IDS);
    assertRefUpdateFailure(r.getRemoteUpdate(RefNames.REFS_EXTERNAL_IDS), "invalid external IDs");
  }

  @Test
  public void pushToExternalIdsBranchRejectsExternalIdWithInvalidConfig() throws Exception {
    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    fetch(allUsersRepo, RefNames.REFS_EXTERNAL_IDS + ":" + RefNames.REFS_EXTERNAL_IDS);
    allUsersRepo.reset(RefNames.REFS_EXTERNAL_IDS);

    insertExternalIdWithInvalidConfig(
        allUsersRepo.getRepository(), allUsersRepo.getRevWalk(), "foo:bar");
    allUsersRepo.reset(RefNames.REFS_EXTERNAL_IDS);

    allowPushOfExternalIds();
    PushResult r = pushHead(allUsersRepo, RefNames.REFS_EXTERNAL_IDS);
    assertRefUpdateFailure(r.getRemoteUpdate(RefNames.REFS_EXTERNAL_IDS), "invalid external IDs");
  }

  @Test
  public void pushToExternalIdsBranchRejectsExternalIdWithEmptyNote() throws Exception {
    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    fetch(allUsersRepo, RefNames.REFS_EXTERNAL_IDS + ":" + RefNames.REFS_EXTERNAL_IDS);
    allUsersRepo.reset(RefNames.REFS_EXTERNAL_IDS);

    insertExternalIdWithEmptyNote(
        allUsersRepo.getRepository(), allUsersRepo.getRevWalk(), "foo:bar");
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
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);
    resetCurrentApiUser();

    insertValidExternalIds();
    insertInvalidButParsableExternalIds();

    Set<ExternalId> parseableExtIds = externalIds.all();

    insertNonParsableExternalIds();

    Set<ExternalId> extIds = externalIds.all();
    assertThat(extIds).containsExactlyElementsIn(parseableExtIds);

    for (ExternalId parseableExtId : parseableExtIds) {
      ExternalId extId = externalIds.get(parseableExtId.key());
      assertThat(extId).isEqualTo(parseableExtId);
    }
  }

  @Test
  public void checkConsistency() throws Exception {
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);
    resetCurrentApiUser();

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
    exception.expect(AuthException.class);
    exception.expectMessage("access database not permitted");
    gApi.config().server().checkConsistency(new ConsistencyCheckInput());
  }

  private ConsistencyProblemInfo consistencyError(String message) {
    return new ConsistencyProblemInfo(ConsistencyProblemInfo.Status.ERROR, message);
  }

  private void insertValidExternalIds() throws IOException, ConfigInvalidException, OrmException {
    MutableInteger i = new MutableInteger();
    String scheme = "valid";
    ExternalIdsUpdate u = extIdsUpdate.create();

    // create valid external IDs
    u.insert(
        ExternalId.createWithPassword(
            ExternalId.Key.parse(nextId(scheme, i)),
            admin.id,
            "admin.other@example.com",
            "secret-password"));
    u.insert(createExternalIdWithOtherCaseEmail(nextId(scheme, i)));
  }

  private Set<ConsistencyProblemInfo> insertInvalidButParsableExternalIds()
      throws IOException, ConfigInvalidException, OrmException {
    MutableInteger i = new MutableInteger();
    String scheme = "invalid";
    ExternalIdsUpdate u = extIdsUpdate.create();

    Set<ConsistencyProblemInfo> expectedProblems = new HashSet<>();
    ExternalId extIdForNonExistingAccount =
        createExternalIdForNonExistingAccount(nextId(scheme, i));
    u.insert(extIdForNonExistingAccount);
    expectedProblems.add(
        consistencyError(
            "External ID '"
                + extIdForNonExistingAccount.key().get()
                + "' belongs to account that doesn't exist: "
                + extIdForNonExistingAccount.accountId().get()));

    ExternalId extIdWithInvalidEmail = createExternalIdWithInvalidEmail(nextId(scheme, i));
    u.insert(extIdWithInvalidEmail);
    expectedProblems.add(
        consistencyError(
            "External ID '"
                + extIdWithInvalidEmail.key().get()
                + "' has an invalid email: "
                + extIdWithInvalidEmail.email()));

    ExternalId extIdWithDuplicateEmail = createExternalIdWithDuplicateEmail(nextId(scheme, i));
    u.insert(extIdWithDuplicateEmail);
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
    u.insert(extIdWithBadPassword);
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
      String noteId = insertExternalIdWithoutAccountId(repo, rw, externalId);
      expectedProblems.add(
          consistencyError(
              "Invalid external ID config for note '"
                  + noteId
                  + "': Value for 'externalId."
                  + externalId
                  + ".accountId' is missing, expected account ID"));

      externalId = nextId(scheme, i);
      noteId = insertExternalIdWithKeyThatDoesntMatchNoteId(repo, rw, externalId);
      expectedProblems.add(
          consistencyError(
              "Invalid external ID config for note '"
                  + noteId
                  + "': SHA1 of external ID '"
                  + externalId
                  + "' does not match note ID '"
                  + noteId
                  + "'"));

      noteId = insertExternalIdWithInvalidConfig(repo, rw, nextId(scheme, i));
      expectedProblems.add(
          consistencyError(
              "Invalid external ID config for note '" + noteId + "': Invalid line in config file"));

      noteId = insertExternalIdWithEmptyNote(repo, rw, nextId(scheme, i));
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
        ExternalId.Key.parse(externalId), admin.id, admin.email.toUpperCase(Locale.US), "password");
  }

  private String insertExternalIdWithoutAccountId(Repository repo, RevWalk rw, String externalId)
      throws IOException {
    ObjectId rev = ExternalIdReader.readRevision(repo);
    NoteMap noteMap = ExternalIdReader.readNoteMap(rw, rev);

    ExternalId extId = ExternalId.create(ExternalId.Key.parse(externalId), admin.id);

    try (ObjectInserter ins = repo.newObjectInserter()) {
      ObjectId noteId = extId.key().sha1();
      Config c = new Config();
      extId.writeToConfig(c);
      c.unset("externalId", extId.key().get(), "accountId");
      byte[] raw = c.toText().getBytes(UTF_8);
      ObjectId dataBlob = ins.insert(OBJ_BLOB, raw);
      noteMap.set(noteId, dataBlob);

      ExternalIdsUpdate.commit(
          repo, rw, ins, rev, noteMap, "Add external ID", admin.getIdent(), admin.getIdent());
      return noteId.getName();
    }
  }

  private String insertExternalIdWithKeyThatDoesntMatchNoteId(
      Repository repo, RevWalk rw, String externalId) throws IOException {
    ObjectId rev = ExternalIdReader.readRevision(repo);
    NoteMap noteMap = ExternalIdReader.readNoteMap(rw, rev);

    ExternalId extId = ExternalId.create(ExternalId.Key.parse(externalId), admin.id);

    try (ObjectInserter ins = repo.newObjectInserter()) {
      ObjectId noteId = ExternalId.Key.parse(externalId + "x").sha1();
      Config c = new Config();
      extId.writeToConfig(c);
      byte[] raw = c.toText().getBytes(UTF_8);
      ObjectId dataBlob = ins.insert(OBJ_BLOB, raw);
      noteMap.set(noteId, dataBlob);

      ExternalIdsUpdate.commit(
          repo, rw, ins, rev, noteMap, "Add external ID", admin.getIdent(), admin.getIdent());
      return noteId.getName();
    }
  }

  private String insertExternalIdWithInvalidConfig(Repository repo, RevWalk rw, String externalId)
      throws IOException {
    ObjectId rev = ExternalIdReader.readRevision(repo);
    NoteMap noteMap = ExternalIdReader.readNoteMap(rw, rev);

    try (ObjectInserter ins = repo.newObjectInserter()) {
      ObjectId noteId = ExternalId.Key.parse(externalId).sha1();
      byte[] raw = "bad-config".getBytes(UTF_8);
      ObjectId dataBlob = ins.insert(OBJ_BLOB, raw);
      noteMap.set(noteId, dataBlob);

      ExternalIdsUpdate.commit(
          repo, rw, ins, rev, noteMap, "Add external ID", admin.getIdent(), admin.getIdent());
      return noteId.getName();
    }
  }

  private String insertExternalIdWithEmptyNote(Repository repo, RevWalk rw, String externalId)
      throws IOException {
    ObjectId rev = ExternalIdReader.readRevision(repo);
    NoteMap noteMap = ExternalIdReader.readNoteMap(rw, rev);

    try (ObjectInserter ins = repo.newObjectInserter()) {
      ObjectId noteId = ExternalId.Key.parse(externalId).sha1();
      byte[] raw = "".getBytes(UTF_8);
      ObjectId dataBlob = ins.insert(OBJ_BLOB, raw);
      noteMap.set(noteId, dataBlob);

      ExternalIdsUpdate.commit(
          repo, rw, ins, rev, noteMap, "Add external ID", admin.getIdent(), admin.getIdent());
      return noteId.getName();
    }
  }

  private ExternalId createExternalIdForNonExistingAccount(String externalId) {
    return ExternalId.create(ExternalId.Key.parse(externalId), new Account.Id(1));
  }

  private ExternalId createExternalIdWithInvalidEmail(String externalId) {
    return ExternalId.createWithEmail(ExternalId.Key.parse(externalId), admin.id, "invalid-email");
  }

  private ExternalId createExternalIdWithDuplicateEmail(String externalId) {
    return ExternalId.createWithEmail(ExternalId.Key.parse(externalId), admin.id, admin.email);
  }

  private ExternalId createExternalIdWithBadPassword(String username) {
    return ExternalId.create(
        ExternalId.Key.create(SCHEME_USERNAME, username),
        admin.id,
        null,
        "non-hashed-password-is-not-allowed");
  }

  private static String nextId(String scheme, MutableInteger i) {
    return scheme + ":foo" + ++i.value;
  }

  @Test
  public void retryOnLockFailure() throws Exception {
    Retryer<RefsMetaExternalIdsUpdate> retryer =
        ExternalIdsUpdate.retryerBuilder()
            .withBlockStrategy(
                new BlockStrategy() {
                  @Override
                  public void block(long sleepTime) {
                    // Don't sleep in tests.
                  }
                })
            .build();

    ExternalId.Key fooId = ExternalId.Key.create("foo", "foo");
    ExternalId.Key barId = ExternalId.Key.create("bar", "bar");

    final AtomicBoolean doneBgUpdate = new AtomicBoolean(false);
    ExternalIdsUpdate update =
        new ExternalIdsUpdate(
            repoManager,
            accountCache,
            allUsers,
            metricMaker,
            externalIds,
            new DisabledExternalIdCache(),
            serverIdent.get(),
            serverIdent.get(),
            () -> {
              if (!doneBgUpdate.getAndSet(true)) {
                try {
                  extIdsUpdate.create().insert(ExternalId.create(barId, admin.id));
                } catch (IOException | ConfigInvalidException | OrmException e) {
                  // Ignore, the successful insertion of the external ID is asserted later
                }
              }
            },
            retryer);
    assertThat(doneBgUpdate.get()).isFalse();
    update.insert(ExternalId.create(fooId, admin.id));
    assertThat(doneBgUpdate.get()).isTrue();

    assertThat(externalIds.get(fooId)).isNotNull();
    assertThat(externalIds.get(barId)).isNotNull();
  }

  @Test
  public void failAfterRetryerGivesUp() throws Exception {
    ExternalId.Key[] extIdsKeys = {
      ExternalId.Key.create("foo", "foo"),
      ExternalId.Key.create("bar", "bar"),
      ExternalId.Key.create("baz", "baz")
    };
    final AtomicInteger bgCounter = new AtomicInteger(0);
    ExternalIdsUpdate update =
        new ExternalIdsUpdate(
            repoManager,
            accountCache,
            allUsers,
            metricMaker,
            externalIds,
            new DisabledExternalIdCache(),
            serverIdent.get(),
            serverIdent.get(),
            () -> {
              try {
                extIdsUpdate
                    .create()
                    .insert(ExternalId.create(extIdsKeys[bgCounter.getAndAdd(1)], admin.id));
              } catch (IOException | ConfigInvalidException | OrmException e) {
                // Ignore, the successful insertion of the external ID is asserted later
              }
            },
            RetryerBuilder.<RefsMetaExternalIdsUpdate>newBuilder()
                .retryIfException(e -> e instanceof LockFailureException)
                .withStopStrategy(StopStrategies.stopAfterAttempt(extIdsKeys.length))
                .build());
    assertThat(bgCounter.get()).isEqualTo(0);
    try {
      update.insert(ExternalId.create(ExternalId.Key.create("abc", "abc"), admin.id));
      fail("expected LockFailureException");
    } catch (LockFailureException e) {
      // Ignore, expected
    }
    assertThat(bgCounter.get()).isEqualTo(extIdsKeys.length);
    for (ExternalId.Key extIdKey : extIdsKeys) {
      assertThat(externalIds.get(extIdKey)).isNotNull();
    }
  }

  @Test
  public void readExternalIdWithAccountIdThatCanBeExpressedInKiB() throws Exception {
    ExternalId.Key extIdKey = ExternalId.Key.parse("foo:bar");
    Account.Id accountId = new Account.Id(1024 * 100);
    extIdsUpdate.create().insert(ExternalId.create(extIdKey, accountId));
    ExternalId extId = externalIds.get(extIdKey);
    assertThat(extId.accountId()).isEqualTo(accountId);
  }

  @Test
  public void checkNoReloadAfterUpdate() throws Exception {
    Set<ExternalId> expectedExtIds = new HashSet<>(externalIds.byAccount(admin.id));
    externalIdReader.setFailOnLoad(true);

    // insert external ID
    ExternalId extId = ExternalId.create("foo", "bar", admin.id);
    extIdsUpdate.create().insert(extId);
    expectedExtIds.add(extId);
    assertThat(externalIds.byAccount(admin.id)).containsExactlyElementsIn(expectedExtIds);

    // update external ID
    expectedExtIds.remove(extId);
    extId = ExternalId.createWithEmail("foo", "bar", admin.id, "foo.bar@example.com");
    extIdsUpdate.create().upsert(extId);
    expectedExtIds.add(extId);
    assertThat(externalIds.byAccount(admin.id)).containsExactlyElementsIn(expectedExtIds);

    // delete external ID
    extIdsUpdate.create().delete(extId);
    expectedExtIds.remove(extId);
    assertThat(externalIds.byAccount(admin.id)).containsExactlyElementsIn(expectedExtIds);
  }

  @Test
  public void byAccountFailIfReadingExternalIdsFails() throws Exception {
    externalIdReader.setFailOnLoad(true);

    // update external ID branch so that external IDs need to be reloaded
    insertExtIdBehindGerritsBack(ExternalId.create("foo", "bar", admin.id));

    exception.expect(IOException.class);
    externalIds.byAccount(admin.id);
  }

  @Test
  public void byEmailFailIfReadingExternalIdsFails() throws Exception {
    externalIdReader.setFailOnLoad(true);

    // update external ID branch so that external IDs need to be reloaded
    insertExtIdBehindGerritsBack(ExternalId.create("foo", "bar", admin.id));

    exception.expect(IOException.class);
    externalIds.byEmail(admin.email);
  }

  @Test
  public void byAccountUpdateExternalIdsBehindGerritsBack() throws Exception {
    Set<ExternalId> expectedExternalIds = new HashSet<>(externalIds.byAccount(admin.id));
    ExternalId newExtId = ExternalId.create("foo", "bar", admin.id);
    insertExtIdBehindGerritsBack(newExtId);
    expectedExternalIds.add(newExtId);
    assertThat(externalIds.byAccount(admin.id)).containsExactlyElementsIn(expectedExternalIds);
  }

  private void insertExtIdBehindGerritsBack(ExternalId extId) throws Exception {
    try (Repository repo = repoManager.openRepository(allUsers);
        RevWalk rw = new RevWalk(repo);
        ObjectInserter ins = repo.newObjectInserter()) {
      ObjectId rev = ExternalIdReader.readRevision(repo);
      NoteMap noteMap = ExternalIdReader.readNoteMap(rw, rev);
      ExternalIdsUpdate.insert(rw, ins, noteMap, extId);
      ExternalIdsUpdate.commit(
          repo, rw, ins, rev, noteMap, "insert new ID", serverIdent.get(), serverIdent.get());
    }
  }

  private void addExtId(TestRepository<?> testRepo, ExternalId... extIds)
      throws IOException, OrmDuplicateKeyException, ConfigInvalidException {
    ObjectId rev = ExternalIdReader.readRevision(testRepo.getRepository());

    try (ObjectInserter ins = testRepo.getRepository().newObjectInserter()) {
      NoteMap noteMap = ExternalIdReader.readNoteMap(testRepo.getRevWalk(), rev);
      for (ExternalId extId : extIds) {
        ExternalIdsUpdate.insert(testRepo.getRevWalk(), ins, noteMap, extId);
      }

      ExternalIdsUpdate.commit(
          testRepo.getRepository(),
          testRepo.getRevWalk(),
          ins,
          rev,
          noteMap,
          "Add external ID",
          admin.getIdent(),
          admin.getIdent());
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

  private void allowPushOfExternalIds() throws IOException, ConfigInvalidException {
    grant(allUsers, RefNames.REFS_EXTERNAL_IDS, Permission.READ);
    grant(allUsers, RefNames.REFS_EXTERNAL_IDS, Permission.PUSH);
  }

  private void assertRefUpdateFailure(RemoteRefUpdate update, String msg) {
    assertThat(update.getStatus()).isEqualTo(Status.REJECTED_OTHER_REASON);
    assertThat(update.getMessage()).isEqualTo(msg);
  }
}
