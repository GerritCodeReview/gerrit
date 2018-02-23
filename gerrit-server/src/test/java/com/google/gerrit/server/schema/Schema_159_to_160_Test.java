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

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.git.UserConfigSections.KEY_URL;
import static com.google.gerrit.server.git.UserConfigSections.MY;
import static com.google.gerrit.server.schema.Schema_160.DEFAULT_DRAFT_ITEM_GWTUI;

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
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
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
    assertThat(myMenusFromNoteDb(accountId).values()).doesNotContain(DEFAULT_DRAFT_ITEM_GWTUI);
    assertThat(myMenusFromApi(accountId).values()).doesNotContain(DEFAULT_DRAFT_ITEM_GWTUI);

    schema160.migrateData(db, new TestUpdateUI());

    assertThat(metaRef(accountId)).isEqualTo(oldMetaId);
  }

  @Test
  public void deleteItems() throws Exception {
    ObjectId oldMetaId = metaRef(accountId);
    List<String> defaultNames = ImmutableList.copyOf(myMenusFromApi(accountId).keySet());

    GeneralPreferencesInfo prefs = gApi.accounts().id(accountId.get()).getPreferences();
    prefs.my.add(0, new MenuItem("Something else", DEFAULT_DRAFT_ITEM_GWTUI + "+is:mergeable"));
    prefs.my.add(new MenuItem("Drafts", DEFAULT_DRAFT_ITEM_GWTUI));
    prefs.my.add(new MenuItem("Totally not drafts", DEFAULT_DRAFT_ITEM_GWTUI));
    gApi.accounts().id(accountId.get()).setPreferences(prefs);

    List<String> oldNames =
        ImmutableList.<String>builder()
            .add("Something else")
            .addAll(defaultNames)
            .add("Drafts")
            .add("Totally not drafts")
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

  // Raw config values, bypassing the defaults set by GeneralPreferencesLoader.
  private ImmutableMap<String, String> myMenusFromNoteDb(Account.Id id) throws Exception {
    try (Repository repo = repoManager.openRepository(allUsersName)) {
      VersionedAccountPreferences prefs = VersionedAccountPreferences.forUser(id);
      prefs.load(repo);
      Config cfg = prefs.getConfig();
      return cfg.getSubsections(MY)
          .stream()
          .collect(toImmutableMap(i -> i, i -> cfg.getString(MY, i, KEY_URL)));
    }
  }

  private ImmutableMap<String, String> myMenusFromApi(Account.Id id) throws Exception {
    return gApi.accounts()
        .id(id.get())
        .getPreferences()
        .my
        .stream()
        .collect(toImmutableMap(i -> i.name, i -> i.url));
  }

  private ObjectId metaRef(Account.Id id) throws Exception {
    try (Repository repo = repoManager.openRepository(allUsersName)) {
      return repo.exactRef(RefNames.refsUsers(id)).getObjectId();
    }
  }
}
