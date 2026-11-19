// Copyright (C) 2026 The Android Open Source Project
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
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static com.google.gerrit.gpg.PublicKeyStore.REFS_GPG_KEYS;
import static com.google.gerrit.gpg.testing.TestCommitSignatures.sign;
import static com.google.gerrit.gpg.testing.TestKeys.validKeyWithoutExpiration;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static com.google.gerrit.testing.TestActionRefUpdateContext.testRefAction;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.OBJ_COMMIT;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.config.GerritConfigs;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.extensions.common.CommitSignatureInfo;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.gpg.testing.TestKey;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.apache.http.HttpStatus;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.junit.Test;

public class CommitSignatureIT extends AbstractDaemonTest {
  @Inject private AccountOperations accountOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true")
  public void unsignedRevisionWithSignedPushDisabled() throws Exception {
    String changeId = createChange().getChangeId();
    var response = adminRestSession.get("/changes/" + changeId + "/revisions/current/signature");
    response.assertOK();
    assertThat(newGson().fromJson(response.getReader(), CommitSignatureInfo.class).status)
        .isEqualTo(CommitSignatureInfo.Status.UNSIGNED);
    assertThat(gApi.changes().id(changeId).current().signature().status)
        .isEqualTo(CommitSignatureInfo.Status.UNSIGNED);
  }

  @Test
  @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true")
  public void serverAdvertisesVerificationAndKeyRegistration() throws Exception {
    var response = adminRestSession.get("/config/server/info");
    response.assertOK();
    JsonObject gerrit =
        newGson().fromJson(response.getReader(), JsonObject.class).getAsJsonObject("gerrit");
    assertThat(gerrit.has("commit_signature_verification")).isTrue();
    assertThat(gerrit.get("commit_signature_verification").getAsBoolean()).isTrue();
    assertThat(gerrit.get("edit_gpg_keys").getAsBoolean()).isTrue();
  }

  @Test
  public void verificationDisabledByDefault() throws Exception {
    String changeId = createChange().getChangeId();
    adminRestSession
        .get("/changes/" + changeId + "/revisions/current/signature")
        .assertStatus(HttpStatus.SC_NOT_IMPLEMENTED);
    assertThrows(
        NotImplementedException.class, () -> gApi.changes().id(changeId).current().signature());
    var info =
        newGson()
            .fromJson(adminRestSession.get("/config/server/info").getReader(), JsonObject.class);
    assertThat(info.getAsJsonObject("gerrit").has("commit_signature_verification")).isFalse();
  }

  @Test
  @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true")
  public void signedRevisionUploaderMayDifferFromSigner() throws Exception {
    String changeId = createSignedRevision();
    var info = gApi.changes().id(changeId).current().signature();
    assertThat(info.status).isEqualTo(CommitSignatureInfo.Status.VERIFIED);
    assertThat(info.signer._accountId).isEqualTo(user.id().get());
    assertThat(info.signer.name).isEqualTo(user.fullName());
    assertThat(info.signer.email).isEqualTo(user.email());
    assertThat(gApi.changes().id(changeId).get().owner._accountId).isEqualTo(admin.id().get());
    var response = userRestSession.get("/changes/" + changeId + "/revisions/current/signature");
    response.assertOK();
    assertThat(newGson().fromJson(response.getReader(), CommitSignatureInfo.class).status)
        .isEqualTo(CommitSignatureInfo.Status.VERIFIED);
    assertThat(gApi.changes().id(changeId).revision(1).signature().status)
        .isEqualTo(CommitSignatureInfo.Status.UNSIGNED);
  }

  @Test
  @GerritConfigs({
    @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true"),
    @GerritConfig(name = "accounts.visibility", value = "NONE")
  })
  public void revisionReadersSeeStandardSignerInfoDespiteRestrictedDiscovery() throws Exception {
    String changeId = createSignedRevision();
    anonymousRestSession.get("/accounts/" + user.id().get()).assertNotFound();
    var response = anonymousRestSession.get("/changes/" + changeId + "/revisions/2/signature");
    response.assertOK();
    JsonObject json = newGson().fromJson(response.getReader(), JsonObject.class);
    assertThat(json.get("status").getAsString()).isEqualTo("VERIFIED");
    JsonObject signer = json.getAsJsonObject("signer");
    assertThat(signer.get("_account_id").getAsInt()).isEqualTo(user.id().get());
    assertThat(signer.get("name").getAsString()).isEqualTo(user.fullName());
    assertThat(signer.get("email").getAsString()).isEqualTo(user.email());
    assertThat(signer.has("secondary_emails")).isFalse();
    assertThat(signer.has("external_ids")).isFalse();
    assertThat(json.has("user_ids")).isFalse();
  }

  @Test
  @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true")
  public void verificationReflectsCurrentKeyRegistration() throws Exception {
    String changeId = createSignedRevision();
    assertThat(gApi.changes().id(changeId).current().signature().status)
        .isEqualTo(CommitSignatureInfo.Status.VERIFIED);
    requestScopeOperations.setApiUser(user.id());
    gApi.accounts().self().gpgKey(validKeyWithoutExpiration().getKeyIdString()).delete();
    requestScopeOperations.setApiUser(admin.id());
    var info = gApi.changes().id(changeId).current().signature();
    assertThat(info.status).isEqualTo(CommitSignatureInfo.Status.UNVERIFIED);
    assertThat(info.reason).isEqualTo(CommitSignatureInfo.Reason.UNKNOWN_KEY);
  }

  @Test
  @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true")
  public void signatureRequiresRevisionAccess() throws Exception {
    String changeId = createSignedRevision();
    gApi.changes().id(changeId).setPrivate(true);
    anonymousRestSession
        .get("/changes/" + changeId + "/revisions/current/signature")
        .assertNotFound();
    adminRestSession.get("/changes/" + changeId + "/revisions/999/signature").assertNotFound();
  }

  @Test
  @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true")
  public void editsDoNotHaveStoredRevisionSignatureResults() throws Exception {
    String changeId = createChange().getChangeId();
    gApi.changes().id(changeId).edit().modifyFile("edited.txt", RawInputUtil.create("edited"));
    adminRestSession.get("/changes/" + changeId + "/revisions/edit/signature").assertNotFound();
    assertThat(gApi.changes().id(changeId).current().signature().status)
        .isEqualTo(CommitSignatureInfo.Status.UNSIGNED);
  }

  @Test
  @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true")
  public void keyStoreFailureIsARestError() throws Exception {
    String changeId = createSignedRevision();
    try (Repository repo = repoManager.openRepository(allUsers)) {
      ObjectId original = repo.exactRef(REFS_GPG_KEYS).getObjectId();
      ObjectId corrupt;
      try (ObjectInserter inserter = repo.newObjectInserter()) {
        corrupt = inserter.insert(OBJ_BLOB, "Not a key-store commit".getBytes(UTF_8));
        inserter.flush();
      }
      RefUpdate update = repo.updateRef(REFS_GPG_KEYS);
      update.setForceUpdate(true);
      update.setNewObjectId(corrupt);
      testRefAction(() -> assertThat(update.update()).isEqualTo(RefUpdate.Result.FORCED));
      try {
        adminRestSession
            .get("/changes/" + changeId + "/revisions/current/signature")
            .assertStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        RestApiException failure =
            assertThrows(
                RestApiException.class, () -> gApi.changes().id(changeId).current().signature());
        assertThat(failure).hasCauseThat().isInstanceOf(IOException.class);
      } finally {
        RefUpdate restore = repo.updateRef(REFS_GPG_KEYS);
        restore.setForceUpdate(true);
        restore.setNewObjectId(original);
        testRefAction(() -> assertThat(restore.update()).isEqualTo(RefUpdate.Result.FORCED));
      }
    }
  }

  @Test
  @GerritConfigs({
    @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true"),
    @GerritConfig(name = "gerrit.editGpgKeys", value = "false")
  })
  public void keyEditingCanBeDisabledIndependently() throws Exception {
    String changeId = createChange().getChangeId();
    assertThat(gApi.changes().id(changeId).current().signature().status)
        .isEqualTo(CommitSignatureInfo.Status.UNSIGNED);
    assertThrows(
        NotImplementedException.class,
        () ->
            gApi.accounts()
                .self()
                .putGpgKeys(List.of(validKeyWithoutExpiration().getPublicKeyArmored()), List.of()));
    assertThat(gApi.config().server().getInfo().gerrit.commitSignatureVerification).isTrue();
    assertThat(gApi.config().server().getInfo().gerrit.editGpgKeys).isNull();
  }

  private String createSignedRevision() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    accountOperations
        .account(user.id())
        .forUpdate()
        .addSecondaryEmail("test1@example.com")
        .update();
    requestScopeOperations.setApiUser(user.id());
    gApi.accounts().self().putGpgKeys(List.of(key.getPublicKeyArmored()), List.of());
    requestScopeOperations.setApiUser(admin.id());
    var original = createChange();
    CommitBuilder commit = new CommitBuilder();
    commit.setTreeId(original.getCommit().getTree());
    commit.setParentIds(Arrays.asList(original.getCommit().getParents()));
    commit.setAuthor(admin.newIdent());
    commit.setCommitter(new PersonIdent("Test Signer", "test1@example.com"));
    commit.setMessage(original.getCommit().getFullMessage());
    ObjectId id;
    try (ObjectInserter inserter = testRepo.getRepository().newObjectInserter()) {
      id = inserter.insert(OBJ_COMMIT, sign(commit.build(), key, Instant.now()));
      inserter.flush();
    }
    testRepo.update("HEAD", id);
    assertThat(pushHead(testRepo, "refs/for/master").getRemoteUpdate("refs/for/master").getStatus())
        .isEqualTo(RemoteRefUpdate.Status.OK);
    return original.getChangeId();
  }
}
