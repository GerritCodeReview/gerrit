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
import static com.google.gerrit.acceptance.GitUtil.fetch;
import static com.google.gerrit.gpg.PublicKeyStore.REFS_GPG_KEYS;
import static com.google.gerrit.gpg.PublicKeyStore.keyToString;
import static com.google.gerrit.gpg.testutil.TestKeys.allValidKeys;
import static com.google.gerrit.gpg.testutil.TestKeys.validKeyWithExpiration;
import static com.google.gerrit.gpg.testutil.TestKeys.validKeyWithSecondUserId;
import static com.google.gerrit.gpg.testutil.TestKeys.validKeyWithoutExpiration;
import static com.google.gerrit.server.StarredChangesUtil.DEFAULT_LABEL;
import static com.google.gerrit.server.StarredChangesUtil.IGNORE_LABEL;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.fail;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.BaseEncoding;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.accounts.EmailInput;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.StarsInput;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.GpgKeyInfo;
import com.google.gerrit.extensions.common.SshKeyInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.gpg.Fingerprint;
import com.google.gerrit.gpg.PublicKeyStore;
import com.google.gerrit.gpg.server.GpgKeys;
import com.google.gerrit.gpg.testutil.TestKey;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.client.AccountProjectWatch.NotifyType;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.account.WatchConfig;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.RefPattern;
import com.google.gerrit.server.util.MagicBranch;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.gerrit.testutil.FakeEmailSender.Message;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PushCertificateIdent;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class AccountIT extends AbstractDaemonTest {
  @ConfigSuite.Default
  public static Config enableSignedPushConfig() {
    Config cfg = new Config();
    cfg.setBoolean("receive", null, "enableSignedPush", true);
    return cfg;
  }

  @Inject private Provider<PublicKeyStore> publicKeyStoreProvider;

  @Inject private AllUsersName allUsers;

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
    accountCache.evict(admin.getId());
    accountCache.evict(user.getId());
  }

  @After
  public void clearPublicKeyStore() throws Exception {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      Ref ref = repo.exactRef(REFS_GPG_KEYS);
      if (ref != null) {
        RefUpdate ru = repo.updateRef(REFS_GPG_KEYS);
        ru.setForceUpdate(true);
        assertThat(ru.delete()).isEqualTo(RefUpdate.Result.FORCED);
      }
    }
  }

  private Collection<AccountExternalId> getExternalIds(TestAccount account) throws Exception {
    return accountCache.get(account.getId()).getExternalIds();
  }

  @After
  public void deleteGpgKeys() throws Exception {
    String ref = REFS_GPG_KEYS;
    try (Repository repo = repoManager.openRepository(allUsers)) {
      if (repo.getRefDatabase().exactRef(ref) != null) {
        RefUpdate ru = repo.updateRef(ref);
        ru.setForceUpdate(true);
        assert_()
            .withFailureMessage("Failed to delete " + ref)
            .that(ru.delete())
            .isEqualTo(RefUpdate.Result.FORCED);
      }
    }
  }

  @Test
  public void get() throws Exception {
    AccountInfo info = gApi.accounts().id("admin").get();
    assertThat(info.name).isEqualTo("Administrator");
    assertThat(info.email).isEqualTo("admin@example.com");
    assertThat(info.username).isEqualTo("admin");
  }

  @Test
  public void getByIntId() throws Exception {
    AccountInfo info = gApi.accounts().id("admin").get();
    AccountInfo infoByIntId = gApi.accounts().id(info._accountId).get();
    assertThat(info.name).isEqualTo(infoByIntId.name);
  }

  @Test
  public void self() throws Exception {
    AccountInfo info = gApi.accounts().self().get();
    assertUser(info, admin);

    info = gApi.accounts().id("self").get();
    assertUser(info, admin);
  }

  @Test
  public void active() throws Exception {
    assertThat(gApi.accounts().id("user").getActive()).isTrue();
    gApi.accounts().id("user").setActive(false);
    assertThat(gApi.accounts().id("user").getActive()).isFalse();
    gApi.accounts().id("user").setActive(true);
    assertThat(gApi.accounts().id("user").getActive()).isTrue();
  }

  @Test
  public void deactivateSelf() throws Exception {
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("cannot deactivate own account");
    gApi.accounts().self().setActive(false);
  }

  @Test
  public void deactivateNotActive() throws Exception {
    assertThat(gApi.accounts().id("user").getActive()).isTrue();
    gApi.accounts().id("user").setActive(false);
    assertThat(gApi.accounts().id("user").getActive()).isFalse();
    try {
      gApi.accounts().id("user").setActive(false);
      fail("Expected exception");
    } catch (ResourceConflictException e) {
      assertThat(e.getMessage()).isEqualTo("account not active");
    }
    gApi.accounts().id("user").setActive(true);
  }

  @Test
  public void starUnstarChange() throws Exception {
    PushOneCommit.Result r = createChange();
    String triplet = project.get() + "~master~" + r.getChangeId();
    gApi.accounts().self().starChange(triplet);
    ChangeInfo change = info(triplet);
    assertThat(change.starred).isTrue();
    assertThat(change.stars).contains(DEFAULT_LABEL);

    gApi.accounts().self().unstarChange(triplet);
    change = info(triplet);
    assertThat(change.starred).isNull();
    assertThat(change.stars).isNull();
  }

  @Test
  public void starUnstarChangeWithLabels() throws Exception {
    PushOneCommit.Result r = createChange();
    String triplet = project.get() + "~master~" + r.getChangeId();
    assertThat(gApi.accounts().self().getStars(triplet)).isEmpty();
    assertThat(gApi.accounts().self().getStarredChanges()).isEmpty();

    gApi.accounts()
        .self()
        .setStars(triplet, new StarsInput(ImmutableSet.of(DEFAULT_LABEL, "red", "blue")));
    ChangeInfo change = info(triplet);
    assertThat(change.starred).isTrue();
    assertThat(change.stars).containsExactly("blue", "red", DEFAULT_LABEL).inOrder();
    assertThat(gApi.accounts().self().getStars(triplet))
        .containsExactly("blue", "red", DEFAULT_LABEL)
        .inOrder();
    List<ChangeInfo> starredChanges = gApi.accounts().self().getStarredChanges();
    assertThat(starredChanges).hasSize(1);
    ChangeInfo starredChange = starredChanges.get(0);
    assertThat(starredChange._number).isEqualTo(r.getChange().getId().get());
    assertThat(starredChange.starred).isTrue();
    assertThat(starredChange.stars).containsExactly("blue", "red", DEFAULT_LABEL).inOrder();

    gApi.accounts()
        .self()
        .setStars(
            triplet,
            new StarsInput(ImmutableSet.of("yellow"), ImmutableSet.of(DEFAULT_LABEL, "blue")));
    change = info(triplet);
    assertThat(change.starred).isNull();
    assertThat(change.stars).containsExactly("red", "yellow").inOrder();
    assertThat(gApi.accounts().self().getStars(triplet)).containsExactly("red", "yellow").inOrder();
    starredChanges = gApi.accounts().self().getStarredChanges();
    assertThat(starredChanges).hasSize(1);
    starredChange = starredChanges.get(0);
    assertThat(starredChange._number).isEqualTo(r.getChange().getId().get());
    assertThat(starredChange.starred).isNull();
    assertThat(starredChange.stars).containsExactly("red", "yellow").inOrder();

    setApiUser(user);
    exception.expect(AuthException.class);
    exception.expectMessage("not allowed to get stars of another account");
    gApi.accounts().id(Integer.toString((admin.id.get()))).getStars(triplet);
  }

  @Test
  public void starWithInvalidLabels() throws Exception {
    PushOneCommit.Result r = createChange();
    String triplet = project.get() + "~master~" + r.getChangeId();
    exception.expect(BadRequestException.class);
    exception.expectMessage("invalid labels: another invalid label, invalid label");
    gApi.accounts()
        .self()
        .setStars(
            triplet,
            new StarsInput(
                ImmutableSet.of(DEFAULT_LABEL, "invalid label", "blue", "another invalid label")));
  }

  @Test
  public void starWithDefaultAndIgnoreLabel() throws Exception {
    PushOneCommit.Result r = createChange();
    String triplet = project.get() + "~master~" + r.getChangeId();
    exception.expect(BadRequestException.class);
    exception.expectMessage(
        "The labels "
            + DEFAULT_LABEL
            + " and "
            + IGNORE_LABEL
            + " are mutually exclusive."
            + " Only one of them can be set.");
    gApi.accounts()
        .self()
        .setStars(triplet, new StarsInput(ImmutableSet.of(DEFAULT_LABEL, "blue", IGNORE_LABEL)));
  }

  @Test
  public void ignoreChange() throws Exception {
    PushOneCommit.Result r = createChange();

    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    gApi.changes().id(r.getChangeId()).addReviewer(in);

    TestAccount user2 = accounts.user2();
    in = new AddReviewerInput();
    in.reviewer = user2.email;
    gApi.changes().id(r.getChangeId()).addReviewer(in);

    setApiUser(user);
    gApi.accounts().self().setStars(r.getChangeId(), new StarsInput(ImmutableSet.of(IGNORE_LABEL)));

    sender.clear();
    setApiUser(admin);
    gApi.changes().id(r.getChangeId()).abandon();
    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    assertThat(messages.get(0).rcpt()).containsExactly(user2.emailAddress);
  }

  @Test
  public void addReviewerToIgnoredChange() throws Exception {
    PushOneCommit.Result r = createChange();

    setApiUser(user);
    gApi.accounts().self().setStars(r.getChangeId(), new StarsInput(ImmutableSet.of(IGNORE_LABEL)));

    sender.clear();
    setApiUser(admin);

    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    gApi.changes().id(r.getChangeId()).addReviewer(in);
    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    Message message = messages.get(0);
    assertThat(message.rcpt()).containsExactly(user.emailAddress);
    assertMailFrom(message, admin.email);
  }

  @Test
  public void suggestAccounts() throws Exception {
    String adminUsername = "admin";
    List<AccountInfo> result = gApi.accounts().suggestAccounts().withQuery(adminUsername).get();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).username).isEqualTo(adminUsername);

    List<AccountInfo> resultShortcutApi = gApi.accounts().suggestAccounts(adminUsername).get();
    assertThat(resultShortcutApi).hasSize(result.size());

    List<AccountInfo> emptyResult = gApi.accounts().suggestAccounts("unknown").get();
    assertThat(emptyResult).isEmpty();
  }

  @Test
  public void addEmail() throws Exception {
    List<String> emails = ImmutableList.of("new.email@example.com", "new.email@example.systems");
    for (String email : emails) {
      EmailInput input = new EmailInput();
      input.email = email;
      input.noConfirmation = true;
      gApi.accounts().self().addEmail(input);
    }
  }

  @Test
  public void addInvalidEmail() throws Exception {
    EmailInput input = new EmailInput();
    input.email = "invalid@";
    input.noConfirmation = true;

    exception.expect(BadRequestException.class);
    exception.expectMessage("invalid email address");
    gApi.accounts().self().addEmail(input);
  }

  @Test
  public void fetchUserBranch() throws Exception {
    // change something in the user preferences to ensure that the user branch
    // is created
    setApiUser(user);
    GeneralPreferencesInfo input = new GeneralPreferencesInfo();
    input.changesPerPage = GeneralPreferencesInfo.defaults().changesPerPage + 10;
    gApi.accounts().self().setPreferences(input);

    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers, user);
    String userRefName = RefNames.refsUsers(user.id);

    // remove default READ permissions
    ProjectConfig cfg = projectCache.checkedGet(allUsers).getConfig();
    cfg.getAccessSection(RefNames.REFS_USERS + "${" + RefPattern.USERID_SHARDED + "}", true)
        .remove(new Permission(Permission.READ));
    saveProjectConfig(allUsers, cfg);

    // deny READ permission that is inherited from All-Projects
    deny(allUsers, Permission.READ, ANONYMOUS_USERS, RefNames.REFS + "*");

    // fetching user branch without READ permission fails
    try {
      fetch(allUsersRepo, userRefName + ":userRef");
      Assert.fail("user branch is visible although no READ permission is granted");
    } catch (TransportException e) {
      // expected because no READ granted on user branch
    }

    // allow each user to read its own user branch
    grant(
        Permission.READ,
        allUsers,
        RefNames.REFS_USERS + "${" + RefPattern.USERID_SHARDED + "}",
        false,
        REGISTERED_USERS);

    // fetch user branch using refs/users/YY/XXXXXXX
    fetch(allUsersRepo, userRefName + ":userRef");
    Ref userRef = allUsersRepo.getRepository().exactRef("userRef");
    assertThat(userRef).isNotNull();

    // fetch user branch using refs/users/self
    fetch(allUsersRepo, RefNames.REFS_USERS_SELF + ":userSelfRef");
    Ref userSelfRef = allUsersRepo.getRepository().getRefDatabase().exactRef("userSelfRef");
    assertThat(userSelfRef).isNotNull();
    assertThat(userSelfRef.getObjectId()).isEqualTo(userRef.getObjectId());

    // fetching user branch of another user fails
    String otherUserRefName = RefNames.refsUsers(admin.id);
    exception.expect(TransportException.class);
    exception.expectMessage("Remote does not have " + otherUserRefName + " available for fetch.");
    fetch(allUsersRepo, otherUserRefName + ":otherUserRef");
  }

  @Test
  public void pushToUserBranch() throws Exception {
    // change something in the user preferences to ensure that the user branch
    // is created
    GeneralPreferencesInfo input = new GeneralPreferencesInfo();
    input.changesPerPage = GeneralPreferencesInfo.defaults().changesPerPage + 10;
    gApi.accounts().self().setPreferences(input);

    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    fetch(allUsersRepo, RefNames.refsUsers(admin.id) + ":userRef");
    allUsersRepo.reset("userRef");
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), allUsersRepo);
    push.to(RefNames.refsUsers(admin.id)).assertOkStatus();

    push = pushFactory.create(db, admin.getIdent(), allUsersRepo);
    push.to(RefNames.REFS_USERS_SELF).assertOkStatus();
  }

  @Test
  public void pushToUserBranchForReview() throws Exception {
    // change something in the user preferences to ensure that the user branch
    // is created
    GeneralPreferencesInfo input = new GeneralPreferencesInfo();
    input.changesPerPage = GeneralPreferencesInfo.defaults().changesPerPage + 10;
    gApi.accounts().self().setPreferences(input);

    String userRefName = RefNames.refsUsers(admin.id);
    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    fetch(allUsersRepo, userRefName + ":userRef");
    allUsersRepo.reset("userRef");
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), allUsersRepo);
    PushOneCommit.Result r = push.to(MagicBranch.NEW_CHANGE + userRefName);
    r.assertOkStatus();
    assertThat(r.getChange().change().getDest().get()).isEqualTo(userRefName);
    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(r.getChangeId()).current().submit();

    push = pushFactory.create(db, admin.getIdent(), allUsersRepo);
    r = push.to(MagicBranch.NEW_CHANGE + RefNames.REFS_USERS_SELF);
    r.assertOkStatus();
    assertThat(r.getChange().change().getDest().get()).isEqualTo(userRefName);
    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(r.getChangeId()).current().submit();
  }

  @Test
  public void pushWatchConfigToUserBranch() throws Exception {
    // change something in the user preferences to ensure that the user branch
    // is created
    GeneralPreferencesInfo input = new GeneralPreferencesInfo();
    input.changesPerPage = GeneralPreferencesInfo.defaults().changesPerPage + 10;
    gApi.accounts().self().setPreferences(input);

    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    fetch(allUsersRepo, RefNames.refsUsers(admin.id) + ":userRef");
    allUsersRepo.reset("userRef");

    Config wc = new Config();
    wc.setString(
        WatchConfig.PROJECT,
        project.get(),
        WatchConfig.KEY_NOTIFY,
        WatchConfig.NotifyValue.create(null, EnumSet.of(NotifyType.ALL_COMMENTS)).toString());
    PushOneCommit push =
        pushFactory.create(
            db,
            admin.getIdent(),
            allUsersRepo,
            "Add project watch",
            WatchConfig.WATCH_CONFIG,
            wc.toText());
    push.to(RefNames.REFS_USERS_SELF).assertOkStatus();

    String invalidNotifyValue = "]invalid[";
    wc.setString(WatchConfig.PROJECT, project.get(), WatchConfig.KEY_NOTIFY, invalidNotifyValue);
    push =
        pushFactory.create(
            db,
            admin.getIdent(),
            allUsersRepo,
            "Add invalid project watch",
            WatchConfig.WATCH_CONFIG,
            wc.toText());
    PushOneCommit.Result r = push.to(RefNames.REFS_USERS_SELF);
    r.assertErrorStatus("invalid watch configuration");
    r.assertMessage(
        String.format(
            "%s: Invalid project watch of account %d for project %s: %s",
            WatchConfig.WATCH_CONFIG, admin.getId().get(), project.get(), invalidNotifyValue));
  }

  @Test
  public void addGpgKey() throws Exception {
    TestKey key = validKeyWithoutExpiration();
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
    TestKey key = validKeyWithSecondUserId();
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
    AccountExternalId extId =
        new AccountExternalId(user.getId(), new AccountExternalId.Key("foo:myId"));

    db.accountExternalIds().insert(Collections.singleton(extId));
    accountCache.evict(user.getId());

    TestKey key = validKeyWithSecondUserId();
    addGpgKey(key.getPublicKeyArmored());
    setApiUser(user);

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("GPG key already associated with another account");
    addGpgKey(key.getPublicKeyArmored());
  }

  @Test
  public void listGpgKeys() throws Exception {
    List<TestKey> keys = allValidKeys();
    List<String> toAdd = new ArrayList<>(keys.size());
    for (TestKey key : keys) {
      addExternalIdEmail(admin, PushCertificateIdent.parse(key.getFirstUserId()).getEmailAddress());
      toAdd.add(key.getPublicKeyArmored());
    }
    gApi.accounts().self().putGpgKeys(toAdd, ImmutableList.<String>of());
    assertKeys(keys);
  }

  @Test
  public void deleteGpgKey() throws Exception {
    TestKey key = validKeyWithoutExpiration();
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
    for (TestKey key : allValidKeys()) {
      addExternalIdEmail(admin, PushCertificateIdent.parse(key.getFirstUserId()).getEmailAddress());
    }
    TestKey key1 = validKeyWithoutExpiration();
    TestKey key2 = validKeyWithExpiration();
    TestKey key5 = validKeyWithSecondUserId();

    Map<String, GpgKeyInfo> infos =
        gApi.accounts()
            .self()
            .putGpgKeys(
                ImmutableList.of(key1.getPublicKeyArmored(), key2.getPublicKeyArmored()),
                ImmutableList.of(key5.getKeyIdString()));
    assertThat(infos.keySet()).containsExactly(key1.getKeyIdString(), key2.getKeyIdString());
    assertKeys(key1, key2);

    infos =
        gApi.accounts()
            .self()
            .putGpgKeys(
                ImmutableList.of(key5.getPublicKeyArmored()),
                ImmutableList.of(key1.getKeyIdString()));
    assertThat(infos.keySet()).containsExactly(key1.getKeyIdString(), key5.getKeyIdString());
    assertKeyMapContains(key5, infos);
    assertThat(infos.get(key1.getKeyIdString()).key).isNull();
    assertKeys(key2, key5);

    exception.expect(BadRequestException.class);
    exception.expectMessage("Cannot both add and delete key: " + keyToString(key2.getPublicKey()));
    infos =
        gApi.accounts()
            .self()
            .putGpgKeys(
                ImmutableList.of(key2.getPublicKeyArmored()),
                ImmutableList.of(key2.getKeyIdString()));
  }

  @Test
  public void sshKeys() throws Exception {
    // The test account should initially have exactly one ssh key
    List<SshKeyInfo> info = gApi.accounts().self().listSshKeys();
    assertThat(info).hasSize(1);
    assertSequenceNumbers(info);
    SshKeyInfo key = info.get(0);
    String inital = AccountCreator.publicKey(admin.sshKey, admin.email);
    assertThat(key.sshPublicKey).isEqualTo(inital);

    // Add a new key
    String newKey = AccountCreator.publicKey(AccountCreator.genSshKey(), admin.email);
    gApi.accounts().self().addSshKey(newKey);
    info = gApi.accounts().self().listSshKeys();
    assertThat(info).hasSize(2);
    assertSequenceNumbers(info);

    // Add an existing key (the request succeeds, but the key isn't added again)
    gApi.accounts().self().addSshKey(inital);
    info = gApi.accounts().self().listSshKeys();
    assertThat(info).hasSize(2);
    assertSequenceNumbers(info);

    // Add another new key
    String newKey2 = AccountCreator.publicKey(AccountCreator.genSshKey(), admin.email);
    gApi.accounts().self().addSshKey(newKey2);
    info = gApi.accounts().self().listSshKeys();
    assertThat(info).hasSize(3);
    assertSequenceNumbers(info);

    // Delete second key
    gApi.accounts().self().deleteSshKey(2);
    info = gApi.accounts().self().listSshKeys();
    assertThat(info).hasSize(2);
    assertThat(info.get(0).seq).isEqualTo(1);
    assertThat(info.get(1).seq).isEqualTo(3);
  }

  private void assertSequenceNumbers(List<SshKeyInfo> sshKeys) {
    int seq = 1;
    for (SshKeyInfo key : sshKeys) {
      assertThat(key.seq).isEqualTo(seq++);
    }
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

  private static void assertKeyMapContains(TestKey expected, Map<String, GpgKeyInfo> actualMap) {
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
        .containsExactlyElementsIn(expected.transform(TestKey::getKeyIdString));

    for (TestKey key : expected) {
      assertKeyEquals(key, gApi.accounts().self().gpgKey(key.getKeyIdString()).get());
      assertKeyEquals(
          key,
          gApi.accounts()
              .self()
              .gpgKey(Fingerprint.toString(key.getPublicKey().getFingerprint()))
              .get());
      assertKeyMapContains(key, keyMap);
    }

    // Check raw external IDs.
    Account.Id currAccountId = atrScope.get().getUser().getAccountId();
    Iterable<String> expectedFps =
        expected.transform(k -> BaseEncoding.base16().encode(k.getPublicKey().getFingerprint()));
    Iterable<String> actualFps =
        GpgKeys.getGpgExtIds(db, currAccountId).transform(AccountExternalId::getSchemeRest);
    assertThat(actualFps).named("external IDs in database").containsExactlyElementsIn(expectedFps);

    // Check raw stored keys.
    for (TestKey key : expected) {
      getOnlyKeyFromStore(key);
    }
  }

  private static void assertKeyEquals(TestKey expected, GpgKeyInfo actual) {
    String id = expected.getKeyIdString();
    assertThat(actual.id).named(id).isEqualTo(id);
    assertThat(actual.fingerprint)
        .named(id)
        .isEqualTo(Fingerprint.toString(expected.getPublicKey().getFingerprint()));
    @SuppressWarnings("unchecked")
    List<String> userIds = ImmutableList.copyOf(expected.getPublicKey().getUserIDs());
    assertThat(actual.userIds).named(id).containsExactlyElementsIn(userIds);
    assertThat(actual.key).named(id).startsWith("-----BEGIN PGP PUBLIC KEY BLOCK-----\n");
    assertThat(actual.status).isEqualTo(GpgKeyInfo.Status.TRUSTED);
    assertThat(actual.problems).isEmpty();
  }

  private void addExternalIdEmail(TestAccount account, String email) throws Exception {
    checkNotNull(email);
    AccountExternalId extId =
        new AccountExternalId(account.getId(), new AccountExternalId.Key(name("test"), email));
    extId.setEmailAddress(email);
    db.accountExternalIds().insert(Collections.singleton(extId));
    // Clear saved AccountState and AccountExternalIds.
    accountCache.evict(account.getId());
    setApiUser(account);
  }

  private Map<String, GpgKeyInfo> addGpgKey(String armored) throws Exception {
    return gApi.accounts().self().putGpgKeys(ImmutableList.of(armored), ImmutableList.<String>of());
  }

  private void assertUser(AccountInfo info, TestAccount account) throws Exception {
    assertThat(info.name).isEqualTo(account.fullName);
    assertThat(info.email).isEqualTo(account.email);
    assertThat(info.username).isEqualTo(account.username);
  }
}
