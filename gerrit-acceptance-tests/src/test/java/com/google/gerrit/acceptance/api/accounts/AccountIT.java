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

package com.google.gerrit.acceptance.api.accounts;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static com.google.gerrit.server.git.gpg.PublicKeyStore.fingerprintToString;
import static com.google.gerrit.server.git.gpg.PublicKeyStore.keyIdToString;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.extensions.api.accounts.EmailInput;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.GpgKeyInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.gpg.PublicKeyStore;
import com.google.gerrit.server.git.gpg.TestKey;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PushCertificateIdent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class AccountIT extends AbstractDaemonTest {
  @Inject
  private Provider<PublicKeyStore> publicKeyStoreProvider;

  @Inject
  private AllUsersName allUsers;

  private List<AccountExternalId> savedExternalIds;

  @Before
  public void saveExternalIds() throws Exception {
    savedExternalIds = new ArrayList<>();
    savedExternalIds.addAll(getExternalIds(admin));
    savedExternalIds.addAll(getExternalIds(user));
  }

  @After
  public void restoreExternalIds() throws Exception {
    db.accountExternalIds().delete(getExternalIds(admin));
    db.accountExternalIds().delete(getExternalIds(user));
    db.accountExternalIds().insert(savedExternalIds);
  }

  private List<AccountExternalId> getExternalIds(TestAccount account)
      throws Exception {
    return db.accountExternalIds().byAccount(account.getId()).toList();
  }

  @After
  public void deleteGpgKeys() throws Exception {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      String ref = RefNames.REFS_GPG_KEYS;
      if (repo.getRefDatabase().exactRef(ref) != null) {
        RefUpdate ru = repo.updateRef(ref);
        ru.setForceUpdate(true);
        assert_().withFailureMessage("Failed to delete " + ref)
            .that(ru.delete()).isEqualTo(RefUpdate.Result.FORCED);
      }
    }
  }

  @Test
  public void get() throws Exception {
    AccountInfo info = gApi
        .accounts()
        .id("admin")
        .get();
    assertThat(info.name).isEqualTo("Administrator");
    assertThat(info.email).isEqualTo("admin@example.com");
    assertThat(info.username).isEqualTo("admin");
  }

  @Test
  public void self() throws Exception {
    AccountInfo info = gApi
        .accounts()
        .self()
        .get();
    assertThat(info.name).isEqualTo("Administrator");
    assertThat(info.email).isEqualTo("admin@example.com");
    assertThat(info.username).isEqualTo("admin");
  }

  @Test
  public void starUnstarChange() throws Exception {
    PushOneCommit.Result r = createChange();
    String triplet = project.get() + "~master~" + r.getChangeId();
    gApi.accounts()
        .self()
        .starChange(triplet);
    assertThat(info(triplet).starred).isTrue();
    gApi.accounts()
        .self()
        .unstarChange(triplet);
    assertThat(info(triplet).starred).isNull();
  }

  @Test
  public void suggestAccounts() throws Exception {
    String adminUsername = "admin";
    List<AccountInfo> result = gApi.accounts()
        .suggestAccounts().withQuery(adminUsername).get();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).username).isEqualTo(adminUsername);

    List<AccountInfo> resultShortcutApi = gApi.accounts()
        .suggestAccounts(adminUsername).get();
    assertThat(resultShortcutApi).hasSize(result.size());

    List<AccountInfo> emptyResult = gApi.accounts()
        .suggestAccounts("unknown").get();
    assertThat(emptyResult).isEmpty();
  }

  @Test
  public void addEmail() throws Exception {
    List<String> emails = ImmutableList.of(
        "new.email@example.com", "new.email@example.systems");
    for (String email : emails) {
      EmailInput input = new EmailInput();
      input.email = email;
      input.noConfirmation = true;
      gApi.accounts().self().addEmail(input);
    }
  }

  @Test
  public void addInvalidEmail() throws Exception {
    EmailInput input  = new EmailInput();
    input.email = "invalid@";
    input.noConfirmation = true;

    exception.expect(BadRequestException.class);
    exception.expectMessage("invalid email address");
    gApi.accounts().self().addEmail(input);
  }

  @Test
  public void addGpgKey() throws Exception {
    TestKey key = TestKey.key1();
    String id = keyIdToString(key.getKeyId());
    addExternalIdEmail(admin, "test1@example.com");
    assertKeyEquals(
        key, gApi.accounts().self().addGpgKey(key.getPublicKeyArmored()).get());
    assertKeyEquals(key, gApi.accounts().self().gpgKey(id).get());

    PGPPublicKey stored = getOnlyKeyFromStore(key);
    assertThat(stored.getFingerprint())
        .isEqualTo(key.getPublicKey().getFingerprint());

    setApiUser(user);
    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage(id);
    gApi.accounts().self().gpgKey(id).get();
  }

  @Test
  public void reAddExistingGpgKey() throws Exception {
    addExternalIdEmail(admin, "test5@example.com");
    TestKey key = TestKey.key5();
    PGPPublicKey pk = key.getPublicKey();

    GpgKeyInfo info = gApi.accounts().self().addGpgKey(armor(pk)).get();
    assertThat(info.userIds).hasSize(2);
    assertIteratorSize(2, getOnlyKeyFromStore(key).getUserIDs());

    pk = PGPPublicKey.removeCertification(pk, "foo:myId");
    info = gApi.accounts().self().addGpgKey(armor(pk)).get();
    assertThat(info.userIds).hasSize(1);
    assertIteratorSize(1, getOnlyKeyFromStore(key).getUserIDs());
  }

  @Test
  public void addOtherUsersGpgKey() throws Exception {
    // Both users have a matching external ID for this key.
    addExternalIdEmail(admin, "test5@example.com");
    AccountExternalId extId = new AccountExternalId(
        user.getId(), new AccountExternalId.Key("foo:myId"));

    db.accountExternalIds().insert(Collections.singleton(extId));

    TestKey key = TestKey.key5();
    gApi.accounts().self().addGpgKey(key.getPublicKeyArmored());
    setApiUser(user);

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("GPG key already associated with another account");
    gApi.accounts().self().addGpgKey(key.getPublicKeyArmored());
  }

  @Test
  public void listGpgKeys() throws Exception {
    List<TestKey> keys = TestKey.allValidKeys();
    for (TestKey key : keys) {
      addExternalIdEmail(admin,
          PushCertificateIdent.parse(key.getFirstUserId()).getEmailAddress());
      gApi.accounts().self().addGpgKey(key.getPublicKeyArmored());
    }

    Map<String, GpgKeyInfo> actual = gApi.accounts().self().listGpgKeys();
    assertThat(actual).hasSize(keys.size());
    for (TestKey k : keys) {
      String id = keyIdToString(k.getKeyId());
      GpgKeyInfo info = actual.get(id);
      assertThat(info).named(id).isNotNull();
      assertThat(info.id).named(id).isNull();
      info.id = id;
      assertKeyEquals(k, info);
    }
  }

  @Test
  public void deleteGpgKey() throws Exception {
    TestKey key = TestKey.key1();
    String id = keyIdToString(key.getKeyId());
    addExternalIdEmail(admin, "test1@example.com");
    gApi.accounts().self().addGpgKey(key.getPublicKeyArmored()).get();
    assertKeyEquals(key, gApi.accounts().self().gpgKey(id).get());

    gApi.accounts().self().gpgKey(id).delete();
    assertThat(gApi.accounts().self().listGpgKeys()).isEmpty();

    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage(id);
    gApi.accounts().self().gpgKey(id).get();
  }

  private PGPPublicKey getOnlyKeyFromStore(TestKey key) throws Exception {
    try (PublicKeyStore store = publicKeyStoreProvider.get()) {
      Iterable<PGPPublicKeyRing> keys = store.get(key.getKeyId());
      assertThat(keys).hasSize(1);
      return keys.iterator().next().getPublicKey();
    }
  }

  private static String armor(PGPPublicKey key) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
    try (ArmoredOutputStream aout = new ArmoredOutputStream(out)) {
      key.encode(aout);
    }
    return new String(out.toByteArray(), UTF_8);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void assertIteratorSize(int size, Iterator it) {
    assertThat(ImmutableList.copyOf(it)).hasSize(size);
  }

  private static void assertKeyEquals(TestKey expected, GpgKeyInfo actual) {
    String id = keyIdToString(expected.getKeyId());
    assertThat(actual.id).named(id).isEqualTo(id);
    assertThat(actual.fingerprint).named(id).isEqualTo(
        fingerprintToString(expected.getPublicKey().getFingerprint()));
    @SuppressWarnings("unchecked")
    List<String> userIds =
        ImmutableList.copyOf(expected.getPublicKey().getUserIDs());
    assertThat(actual.userIds).named(id).containsExactlyElementsIn(userIds);
    assertThat(actual.key).named(id)
        .startsWith("-----BEGIN PGP PUBLIC KEY BLOCK-----\n");
  }

  private void addExternalIdEmail(TestAccount account, String email)
      throws Exception {
    checkNotNull(email);
    AccountExternalId extId = new AccountExternalId(
        account.getId(), new AccountExternalId.Key(name("test"), email));
    extId.setEmailAddress(email);
    db.accountExternalIds().insert(Collections.singleton(extId));
    // Clear saved AccountState and AccountExternalIds.
    accountCache.evict(account.getId());
    setApiUser(account);
  }
}
