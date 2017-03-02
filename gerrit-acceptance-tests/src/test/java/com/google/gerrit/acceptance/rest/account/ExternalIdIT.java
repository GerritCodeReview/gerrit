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
import static com.google.gerrit.server.account.ExternalId.SCHEME_MAILTO;
import static com.google.gerrit.server.account.ExternalId.SCHEME_USERNAME;
import static com.google.gerrit.server.account.ExternalId.SCHEME_UUID;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.junit.Assert.fail;

import com.github.rholder.retry.BlockStrategy;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.common.AccountExternalIdInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.account.ExternalId;
import com.google.gerrit.server.account.ExternalIdCache;
import com.google.gerrit.server.account.ExternalIds;
import com.google.gerrit.server.account.ExternalIdsUpdate;
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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.junit.Test;

@Sandboxed
public class ExternalIdIT extends AbstractDaemonTest {
  @Inject private AllUsersName allUsers;

  @Inject private ExternalIdsUpdate.Server extIdsUpdate;

  @Inject private ExternalIds externalIds;

  @Inject private ExternalIdCache externalIdCache;

  @Test
  public void getExternalIDs() throws Exception {
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
  public void deleteExternalIDs() throws Exception {
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
  public void deleteExternalIDs_Conflict() throws Exception {
    List<String> toDelete = new ArrayList<>();
    String externalIdStr = "username:" + user.username;
    toDelete.add(externalIdStr);
    RestResponse response = userRestSession.post("/accounts/self/external.ids:delete", toDelete);
    response.assertConflict();
    assertThat(response.getEntityContent())
        .isEqualTo(String.format("External id %s cannot be deleted", externalIdStr));
  }

  @Test
  public void deleteExternalIDs_UnprocessableEntity() throws Exception {
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
    ExternalId newExtId =
        ExternalId.createWithPassword(
            ExternalId.Key.parse("foo:bar"),
            admin.id,
            admin.email.toUpperCase(Locale.US),
            "password");
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

    ObjectId rev = ExternalIds.readRevision(allUsersRepo.getRepository());

    NoteMap noteMap = ExternalIds.readNoteMap(allUsersRepo.getRevWalk(), rev);

    ExternalId extId = ExternalId.create(ExternalId.Key.parse("foo:bar"), admin.id);

    try (ObjectInserter ins = allUsersRepo.getRepository().newObjectInserter()) {
      ObjectId noteId = extId.key().sha1();
      Config c = new Config();
      extId.writeToConfig(c);
      c.unset("externalId", extId.key().get(), "accountId");
      byte[] raw = c.toText().getBytes(UTF_8);
      ObjectId dataBlob = ins.insert(OBJ_BLOB, raw);
      noteMap.set(noteId, dataBlob);

      ExternalIdsUpdate.commit(
          allUsersRepo.getRepository(),
          allUsersRepo.getRevWalk(),
          ins,
          rev,
          noteMap,
          "Add external ID",
          admin.getIdent(),
          admin.getIdent());
      allUsersRepo.reset(RefNames.REFS_EXTERNAL_IDS);
    }

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

    ObjectId rev = ExternalIds.readRevision(allUsersRepo.getRepository());

    NoteMap noteMap = ExternalIds.readNoteMap(allUsersRepo.getRevWalk(), rev);

    ExternalId extId = ExternalId.create(ExternalId.Key.parse("foo:bar"), admin.id);

    try (ObjectInserter ins = allUsersRepo.getRepository().newObjectInserter()) {
      ObjectId noteId = ExternalId.Key.parse("other:baz").sha1();
      Config c = new Config();
      extId.writeToConfig(c);
      byte[] raw = c.toText().getBytes(UTF_8);
      ObjectId dataBlob = ins.insert(OBJ_BLOB, raw);
      noteMap.set(noteId, dataBlob);

      ExternalIdsUpdate.commit(
          allUsersRepo.getRepository(),
          allUsersRepo.getRevWalk(),
          ins,
          rev,
          noteMap,
          "Add external ID",
          admin.getIdent(),
          admin.getIdent());
      allUsersRepo.reset(RefNames.REFS_EXTERNAL_IDS);
    }

    allowPushOfExternalIds();
    PushResult r = pushHead(allUsersRepo, RefNames.REFS_EXTERNAL_IDS);
    assertRefUpdateFailure(r.getRemoteUpdate(RefNames.REFS_EXTERNAL_IDS), "invalid external IDs");
  }

  @Test
  public void pushToExternalIdsBranchRejectsExternalIdWithInvalidConfig() throws Exception {
    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    fetch(allUsersRepo, RefNames.REFS_EXTERNAL_IDS + ":" + RefNames.REFS_EXTERNAL_IDS);
    allUsersRepo.reset(RefNames.REFS_EXTERNAL_IDS);

    ObjectId rev = ExternalIds.readRevision(allUsersRepo.getRepository());

    NoteMap noteMap = ExternalIds.readNoteMap(allUsersRepo.getRevWalk(), rev);

    try (ObjectInserter ins = allUsersRepo.getRepository().newObjectInserter()) {
      ObjectId noteId = ExternalId.Key.parse("foo:bar").sha1();
      byte[] raw = "bad-config".getBytes(UTF_8);
      ObjectId dataBlob = ins.insert(OBJ_BLOB, raw);
      noteMap.set(noteId, dataBlob);

      ExternalIdsUpdate.commit(
          allUsersRepo.getRepository(),
          allUsersRepo.getRevWalk(),
          ins,
          rev,
          noteMap,
          "Add external ID",
          admin.getIdent(),
          admin.getIdent());
      allUsersRepo.reset(RefNames.REFS_EXTERNAL_IDS);
    }

    allowPushOfExternalIds();
    PushResult r = pushHead(allUsersRepo, RefNames.REFS_EXTERNAL_IDS);
    assertRefUpdateFailure(r.getRemoteUpdate(RefNames.REFS_EXTERNAL_IDS), "invalid external IDs");
  }

  @Test
  public void pushToExternalIdsBranchRejectsExternalIdForNonExistingAccount() throws Exception {
    testPushToExternalIdsBranchRejectsInvalidExternalId(
        ExternalId.create(ExternalId.Key.parse("foo:bar"), new Account.Id(1)));
  }

  @Test
  public void pushToExternalIdsBranchRejectsExternalIdWithInvalidEmail() throws Exception {
    testPushToExternalIdsBranchRejectsInvalidExternalId(
        ExternalId.createWithEmail(ExternalId.Key.parse("foo:bar"), admin.id, "invalid-email"));
  }

  @Test
  public void pushToExternalIdsBranchRejectsDuplicateEmails() throws Exception {
    testPushToExternalIdsBranchRejectsInvalidExternalId(
        ExternalId.createWithEmail(ExternalId.Key.parse("foo:bar"), admin.id, admin.email));
  }

  @Test
  public void pushToExternalIdsBranchRejectsBadPassword() throws Exception {
    testPushToExternalIdsBranchRejectsInvalidExternalId(
        ExternalId.create(
            ExternalId.Key.create(SCHEME_USERNAME, "foo"),
            admin.id,
            null,
            "non-hashed-password-is-not-allowed"));
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
  public void retryOnLockFailure() throws Exception {
    Retryer<ObjectId> retryer =
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
            allUsers,
            externalIdCache,
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
            allUsers,
            externalIdCache,
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
            RetryerBuilder.<ObjectId>newBuilder()
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

  private void addExtId(TestRepository<?> testRepo, ExternalId... extIds)
      throws IOException, OrmDuplicateKeyException, ConfigInvalidException {
    ObjectId rev = ExternalIds.readRevision(testRepo.getRepository());

    try (ObjectInserter ins = testRepo.getRepository().newObjectInserter()) {
      NoteMap noteMap = ExternalIds.readNoteMap(testRepo.getRevWalk(), rev);
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
    grant(Permission.READ, allUsers, RefNames.REFS_EXTERNAL_IDS);
    grant(Permission.PUSH, allUsers, RefNames.REFS_EXTERNAL_IDS);
  }

  private void assertRefUpdateFailure(RemoteRefUpdate update, String msg) {
    assertThat(update.getStatus()).isEqualTo(Status.REJECTED_OTHER_REASON);
    assertThat(update.getMessage()).isEqualTo(msg);
  }
}
