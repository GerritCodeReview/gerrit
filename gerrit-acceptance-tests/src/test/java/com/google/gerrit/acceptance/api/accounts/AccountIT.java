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
import static com.google.gerrit.gpg.PublicKeyStore.REFS_GPG_KEYS;
import static com.google.gerrit.gpg.PublicKeyStore.keyToString;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.extensions.api.accounts.EmailInput;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.GpgKeyInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.gpg.Fingerprint;
import com.google.gerrit.gpg.PublicKeyStore;
import com.google.gerrit.gpg.server.GpgKeys;
import com.google.gerrit.gpg.testutil.TestKey;
import com.google.gerrit.gpg.testutil.TestKeys;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PushCertificateIdent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class AccountIT extends AbstractDaemonTest {
  @ConfigSuite.Default
  public static Config enableSignedPushConfig() {
    Config cfg = new Config();
    cfg.setBoolean("receive", null, "enableSignedPush", true);
    return cfg;
  }

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

  @After
  public void clearPublicKeyStore() throws Exception {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      Ref ref = repo.getRef(REFS_GPG_KEYS);
      if (ref != null) {
        RefUpdate ru = repo.updateRef(REFS_GPG_KEYS);
        ru.setForceUpdate(true);
        assertThat(ru.delete()).isEqualTo(RefUpdate.Result.FORCED);
      }
    }
  }

  private List<AccountExternalId> getExternalIds(TestAccount account)
      throws Exception {
    return db.accountExternalIds().byAccount(account.getId()).toList();
  }

  @After
  public void deleteGpgKeys() throws Exception {
    String ref = REFS_GPG_KEYS;
    try (Repository repo = repoManager.openRepository(allUsers)) {
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
    TestKey key = TestKeys.key1();
    String id = key.getKeyIdString();
    addExternalIdEmail(admin, "test1@example.com");

    assertKeyMapContains(key, addGpgKey(key.getPublicKeyArmored()));
    assertKeys(key);

    setApiUser(user);
    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage(id);
    gApi.accounts().self().gpgKey(id).get();
  }

  @Test
  public void reAddExistingGpgKey() throws Exception {
    addExternalIdEmail(admin, "test5@example.com");
    TestKey key = TestKeys.key5();
    String id = key.getKeyIdString();
    PGPPublicKey pk = key.getPublicKey();

    GpgKeyInfo info = addGpgKey(armor(pk)).get(id);
    assertThat(info.userIds).hasSize(2);
    assertIteratorSize(2, getOnlyKeyFromStore(key).getUserIDs());

    pk = PGPPublicKey.removeCertification(pk, "foo:myId");
    info = addGpgKey(armor(pk)).get(id);
    assertThat(info.userIds).hasSize(1);
    assertIteratorSize(1, getOnlyKeyFromStore(key).getUserIDs());
  }

  @Test
  public void addOtherUsersGpgKey_Conflict() throws Exception {
    // Both users have a matching external ID for this key.
    addExternalIdEmail(admin, "test5@example.com");
    AccountExternalId extId = new AccountExternalId(
        user.getId(), new AccountExternalId.Key("foo:myId"));

    db.accountExternalIds().insert(Collections.singleton(extId));

    TestKey key = TestKeys.key5();
    addGpgKey(key.getPublicKeyArmored());
    setApiUser(user);

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("GPG key already associated with another account");
    addGpgKey(key.getPublicKeyArmored());
  }

  @Test
  public void listGpgKeys() throws Exception {
    List<TestKey> keys = TestKeys.allValidKeys();
    List<String> toAdd = new ArrayList<>(keys.size());
    for (TestKey key : keys) {
      addExternalIdEmail(admin,
          PushCertificateIdent.parse(key.getFirstUserId()).getEmailAddress());
      toAdd.add(key.getPublicKeyArmored());
    }
    gApi.accounts().self().putGpgKeys(toAdd, ImmutableList.<String> of());
    assertKeys(keys);
  }

  @Test
  public void deleteGpgKey() throws Exception {
    TestKey key = TestKeys.key1();
    String id = key.getKeyIdString();
    addExternalIdEmail(admin, "test1@example.com");
    addGpgKey(key.getPublicKeyArmored());
    assertKeys(key);

    gApi.accounts().self().gpgKey(id).delete();
    assertKeys();

    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage(id);
    gApi.accounts().self().gpgKey(id).get();
  }

  @Test
  public void addAndRemoveGpgKeys() throws Exception {
    for (TestKey key : TestKeys.allValidKeys()) {
      addExternalIdEmail(admin,
          PushCertificateIdent.parse(key.getFirstUserId()).getEmailAddress());
    }
    TestKey key1 = TestKeys.key1();
    TestKey key2 = TestKeys.key2();
    TestKey key5 = TestKeys.key5();

    Map<String, GpgKeyInfo> infos = gApi.accounts().self().putGpgKeys(
        ImmutableList.of(
          key1.getPublicKeyArmored(),
          key2.getPublicKeyArmored()),
        ImmutableList.of(key5.getKeyIdString()));
    assertThat(infos.keySet())
        .containsExactly(key1.getKeyIdString(), key2.getKeyIdString());
    assertKeys(key1, key2);

    infos = gApi.accounts().self().putGpgKeys(
        ImmutableList.of(key5.getPublicKeyArmored()),
        ImmutableList.of(key1.getKeyIdString()));
    assertThat(infos.keySet())
        .containsExactly(key1.getKeyIdString(), key5.getKeyIdString());
    assertKeyMapContains(key5, infos);
    assertThat(infos.get(key1.getKeyIdString()).key).isNull();
    assertKeys(key2, key5);

    exception.expect(BadRequestException.class);
    exception.expectMessage("Cannot both add and delete key: "
        + keyToString(key2.getPublicKey()));
    infos = gApi.accounts().self().putGpgKeys(
        ImmutableList.of(key2.getPublicKeyArmored()),
        ImmutableList.of(key2.getKeyIdString()));
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

  private static void assertKeyMapContains(TestKey expected,
      Map<String, GpgKeyInfo> actualMap) {
    GpgKeyInfo actual = actualMap.get(expected.getKeyIdString());
    assertThat(actual).isNotNull();
    assertThat(actual.id).isNull();
    actual.id = expected.getKeyIdString();
    assertKeyEquals(expected, actual);
  }

  private void assertKeys(TestKey... expectedKeys) throws Exception {
    assertKeys(Arrays.asList(expectedKeys));
  }

  private void assertKeys(Iterable<TestKey> expectedKeys) throws Exception {
    // Check via API.
    FluentIterable<TestKey> expected = FluentIterable.from(expectedKeys);
    Map<String, GpgKeyInfo> keyMap = gApi.accounts().self().listGpgKeys();
    assertThat(keyMap.keySet())
        .named("keys returned by listGpgKeys()")
        .containsExactlyElementsIn(
          expected.transform(new Function<TestKey, String>() {
            @Override
            public String apply(TestKey in) {
              return in.getKeyIdString();
            }
          }));

    for (TestKey key : expected) {
      assertKeyEquals(key, gApi.accounts().self().gpgKey(
          key.getKeyIdString()).get());
      assertKeyEquals(key, gApi.accounts().self().gpgKey(
          Fingerprint.toString(key.getPublicKey().getFingerprint())).get());
      assertKeyMapContains(key, keyMap);
    }

    // Check raw external IDs.
    Account.Id currAccountId =
        ((IdentifiedUser) atrScope.get().getUser()).getAccountId();
    assertThat(
        GpgKeys.getGpgExtIds(db, currAccountId)
          .transform(new Function<AccountExternalId, String>() {
            @Override
            public String apply(AccountExternalId in) {
              return in.getSchemeRest();
            }
          }))
        .named("external IDs in database")
        .containsExactlyElementsIn(
            expected.transform(new Function<TestKey, String>() {
              @Override
              public String apply(TestKey in) {
                return BaseEncoding.base16().encode(
                    in.getPublicKey().getFingerprint());
              }
            }));

    // Check raw stored keys.
    for (TestKey key : expected) {
      getOnlyKeyFromStore(key);
    }
  }

  private static void assertKeyEquals(TestKey expected, GpgKeyInfo actual) {
    String id = expected.getKeyIdString();
    assertThat(actual.id).named(id).isEqualTo(id);
    assertThat(actual.fingerprint).named(id).isEqualTo(
        Fingerprint.toString(expected.getPublicKey().getFingerprint()));
    @SuppressWarnings("unchecked")
    List<String> userIds =
        ImmutableList.copyOf(expected.getPublicKey().getUserIDs());
    assertThat(actual.userIds).named(id).containsExactlyElementsIn(userIds);
    assertThat(actual.key).named(id)
        .startsWith("-----BEGIN PGP PUBLIC KEY BLOCK-----\n");
    assertThat(actual.status).isEqualTo(GpgKeyInfo.Status.TRUSTED);
    assertThat(actual.problems).isEmpty();
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

  private Map<String, GpgKeyInfo> addGpgKey(String armored) throws Exception {
    return gApi.accounts().self().putGpgKeys(
        ImmutableList.of(armored),
        ImmutableList.<String> of());
  }
}
