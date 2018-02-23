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

package com.google.gerrit.server.schema;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.reviewdb.client.RefNames.REFS_USERS_DEFAULT;
import static com.google.gerrit.server.account.VersionedAccountPreferences.PREFERENCES;
import static com.google.gerrit.server.git.UserConfigSections.KEY_URL;
import static com.google.gerrit.server.git.UserConfigSections.MY;
import static com.google.gerrit.server.schema.Schema_160.DEFAULT_DRAFT_ITEM;
import static com.google.gerrit.server.schema.Schema_160.DEFAULT_DRAFT_ITEM_ANCHOR;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.MenuItem;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.VersionedAccountPreferences;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.testutil.SchemaUpgradeTestEnvironment;
import com.google.gerrit.testutil.TestUpdateUI;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class Schema_159_to_160_Test {
  @Rule public SchemaUpgradeTestEnvironment testEnv = new SchemaUpgradeTestEnvironment();

  @Inject private AccountCache accountCache;
  @Inject private AllUsersName allUsersName;
  @Inject private GerritApi gApi;
  @Inject private GitRepositoryManager repoManager;
  @Inject private Provider<IdentifiedUser> userProvider;
  @Inject private Schema_160 schema160;

  private ReviewDb db;
  private Account.Id accountId;

  @Before
  public void setUp() throws Exception {
    testEnv.getInjector().injectMembers(this);
    db = testEnv.getDb();
    accountId = userProvider.get().getAccountId();
  }

  @Test
  public void skipUnmodified() throws Exception {
    ObjectId oldMetaId = metaRef(accountId);
    assertThat(myMenusFromNoteDb(accountId).values()).doesNotContain(DEFAULT_DRAFT_ITEM);
    assertThat(myMenusFromApi(accountId).values()).doesNotContain(DEFAULT_DRAFT_ITEM);

    schema160.migrateData(db, new TestUpdateUI());

    assertThat(metaRef(accountId)).isEqualTo(oldMetaId);
  }

  @Test
  public void deleteItems() throws Exception {
    ObjectId oldMetaId = metaRef(accountId);
    List<String> defaultNames = ImmutableList.copyOf(myMenusFromApi(accountId).keySet());

    GeneralPreferencesInfo prefs = gApi.accounts().id(accountId.get()).getPreferences();
    prefs.my.add(0, new MenuItem("Something else", DEFAULT_DRAFT_ITEM + "+is:mergeable"));
    prefs.my.add(new MenuItem("Drafts", DEFAULT_DRAFT_ITEM));
    prefs.my.add(new MenuItem("Totally not drafts", DEFAULT_DRAFT_ITEM));
    prefs.my.add(new MenuItem("Leading hash", DEFAULT_DRAFT_ITEM_ANCHOR));
    gApi.accounts().id(accountId.get()).setPreferences(prefs);

    List<String> oldNames =
        ImmutableList.<String>builder()
            .add("Something else")
            .addAll(defaultNames)
            .add("Drafts")
            .add("Totally not drafts")
            .add("Leading hash")
            .build();
    assertThat(myMenusFromApi(accountId).keySet()).containsExactlyElementsIn(oldNames).inOrder();

    schema160.migrateData(db, new TestUpdateUI());
    accountCache.evict(accountId);
    testEnv.setApiUser(accountId);

    assertThat(metaRef(accountId)).isNotEqualTo(oldMetaId);

    List<String> newNames =
        ImmutableList.<String>builder().add("Something else").addAll(defaultNames).build();
    assertThat(myMenusFromNoteDb(accountId).keySet()).containsExactlyElementsIn(newNames).inOrder();
    assertThat(myMenusFromApi(accountId).keySet()).containsExactlyElementsIn(newNames).inOrder();
  }

  @Test
  public void skipNonExistentRefsUsersDefault() throws Exception {
    assertThat(readRef(REFS_USERS_DEFAULT)).isEmpty();
    schema160.migrateData(db, new TestUpdateUI());
    assertThat(readRef(REFS_USERS_DEFAULT)).isEmpty();
  }

  @Test
  public void deleteDefaultItem() throws Exception {
    assertThat(readRef(REFS_USERS_DEFAULT)).isEmpty();
    List<String> defaultNames = ImmutableList.copyOf(defaultMenusFromApi().keySet());

    // Setting *any* preference causes preferences.config to contain the full set of "my" sections.
    // This mimics real-world behavior prior to the 2.15 upgrade; see Issue 8439 for details.
    GeneralPreferencesInfo prefs = gApi.config().server().getDefaultPreferences();
    prefs.signedOffBy = !firstNonNull(prefs.signedOffBy, false);
    gApi.config().server().setDefaultPreferences(prefs);

    try (Repository repo = repoManager.openRepository(allUsersName)) {
      Config cfg = new BlobBasedConfig(null, repo, readRef(REFS_USERS_DEFAULT).get(), PREFERENCES);
      assertThat(cfg.getSubsections("my")).containsExactlyElementsIn(defaultNames).inOrder();

      // Add more defaults directly in git, the SetPreferences endpoint doesn't respect the "my"
      // field in the input in 2.15 and earlier.
      cfg.setString("my", "Drafts", "url", DEFAULT_DRAFT_ITEM);
      cfg.setString("my", "Something else", "url", DEFAULT_DRAFT_ITEM + "+is:mergeable");
      cfg.setString("my", "Totally not drafts", "url", DEFAULT_DRAFT_ITEM);
      new TestRepository<>(repo)
          .branch(REFS_USERS_DEFAULT)
          .commit()
          .add(PREFERENCES, cfg.toText())
          .create();
    }

    List<String> oldNames =
        ImmutableList.<String>builder()
            .addAll(defaultNames)
            .add("Drafts")
            .add("Something else")
            .add("Totally not drafts")
            .build();
    assertThat(defaultMenusFromApi().keySet()).containsExactlyElementsIn(oldNames).inOrder();

    schema160.migrateData(db, new TestUpdateUI());

    assertThat(readRef(REFS_USERS_DEFAULT)).isPresent();

    List<String> newNames =
        ImmutableList.<String>builder().addAll(defaultNames).add("Something else").build();
    assertThat(myMenusFromNoteDb(VersionedAccountPreferences::forDefault).keySet())
        .containsExactlyElementsIn(newNames)
        .inOrder();
    assertThat(defaultMenusFromApi().keySet()).containsExactlyElementsIn(newNames).inOrder();
  }

  private ImmutableMap<String, String> myMenusFromNoteDb(Account.Id id) throws Exception {
    return myMenusFromNoteDb(() -> VersionedAccountPreferences.forUser(id));
  }

  // Raw config values, bypassing the defaults set by GeneralPreferencesLoader.
  private ImmutableMap<String, String> myMenusFromNoteDb(
      Supplier<VersionedAccountPreferences> prefsSupplier) throws Exception {
    try (Repository repo = repoManager.openRepository(allUsersName)) {
      VersionedAccountPreferences prefs = prefsSupplier.get();
      prefs.load(repo);
      Config cfg = prefs.getConfig();
      return cfg.getSubsections(MY)
          .stream()
          .collect(toImmutableMap(i -> i, i -> cfg.getString(MY, i, KEY_URL)));
    }
  }

  private ImmutableMap<String, String> myMenusFromApi(Account.Id id) throws Exception {
    return myMenus(gApi.accounts().id(id.get()).getPreferences());
  }

  private ImmutableMap<String, String> defaultMenusFromApi() throws Exception {
    return myMenus(gApi.config().server().getDefaultPreferences());
  }

  private static ImmutableMap<String, String> myMenus(GeneralPreferencesInfo prefs) {

    return prefs.my.stream().collect(toImmutableMap(i -> i.name, i -> i.url));
  }

  private ObjectId metaRef(Account.Id id) throws Exception {
    return readRef(RefNames.refsUsers(id))
        .orElseThrow(() -> new AssertionError("missing ref for account " + id));
  }

  private Optional<ObjectId> readRef(String ref) throws Exception {
    try (Repository repo = repoManager.openRepository(allUsersName)) {
      return Optional.ofNullable(repo.exactRef(ref)).map(Ref::getObjectId);
    }
  }
}
