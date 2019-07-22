// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.account.externalids;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.google.common.cache.Cache;
import com.google.gerrit.metrics.DisabledMetricMaker;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.account.externalids.testing.ExternalIdTestUtil;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.inject.util.Providers;
import java.io.IOException;
import java.util.function.Consumer;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ExternalIDCacheLoaderTest {
  private static AllUsersName ALL_USERS = new AllUsersName("All-Users");

  @Mock Cache<ObjectId, AllExternalIds> externalIdCache;

  private ExternalIdCacheLoader loader;
  private GitRepositoryManager repoManager = new InMemoryRepositoryManager();
  private ExternalIdReader externalIdReader;
  private ExternalIdReader externalIdReaderSpy;

  @Before
  public void setUp() throws Exception {
    repoManager.createRepository(ALL_USERS).close();
    externalIdReader = new ExternalIdReader(repoManager, ALL_USERS, new DisabledMetricMaker());
    externalIdReaderSpy = Mockito.spy(externalIdReader);
    loader = createLoader(true);
  }

  @Test
  public void worksOnSingleCommit() throws Exception {
    ObjectId firstState = insertExternalId(1, 1);
    assertThat(loader.load(firstState)).isEqualTo(allFromGit(firstState));
    verify(externalIdReaderSpy, times(1)).all(firstState);
  }

  @Test
  public void reloadsSingleUpdateUsingPartialReload() throws Exception {
    ObjectId firstState = insertExternalId(1, 1);
    ObjectId head = insertExternalId(2, 2);

    when(externalIdCache.getIfPresent(firstState)).thenReturn(allFromGit(firstState));

    assertThat(loader.load(head)).isEqualTo(allFromGit(head));
    verifyZeroInteractions(externalIdReaderSpy);
  }

  @Test
  public void reloadsMultipleUpdatesUsingPartialReload() throws Exception {
    ObjectId firstState = insertExternalId(1, 1);
    insertExternalId(2, 2);
    insertExternalId(3, 3);
    ObjectId head = insertExternalId(4, 4);

    when(externalIdCache.getIfPresent(firstState)).thenReturn(allFromGit(firstState));

    assertThat(loader.load(head)).isEqualTo(allFromGit(head));
    verifyZeroInteractions(externalIdReaderSpy);
  }

  @Test
  public void reloadsAllExternalIdsWhenNoOldStateIsCached() throws Exception {
    ObjectId firstState = insertExternalId(1, 1);
    ObjectId head = insertExternalId(2, 2);

    when(externalIdCache.getIfPresent(firstState)).thenReturn(null);

    assertThat(loader.load(head)).isEqualTo(allFromGit(head));
    verify(externalIdReaderSpy, times(1)).all(head);
  }

  @Test
  public void partialReloadingDisabledAlwaysTriggersFullReload() throws Exception {
    loader = createLoader(false);
    insertExternalId(1, 1);
    ObjectId head = insertExternalId(2, 2);

    assertThat(loader.load(head)).isEqualTo(allFromGit(head));
    verify(externalIdReaderSpy, times(1)).all(head);
  }

  @Test
  public void fallsBackToFullReloadOnManyUpdatesOnBranch() throws Exception {
    insertExternalId(1, 1);
    ObjectId head = null;
    for (int i = 2; i < 20; i++) {
      head = insertExternalId(i, i);
    }

    assertThat(loader.load(head)).isEqualTo(allFromGit(head));
    verify(externalIdReaderSpy, times(1)).all(head);
  }

  @Test
  public void fallsBackToFullReloadOnManyUpdatesInFiles() throws Exception {
    ObjectId firstState = insertExternalId(1, 1);
    ObjectId head;
    try (Repository repo = repoManager.openRepository(ALL_USERS)) {
      PersonIdent updater = new PersonIdent("Foo bar", "foo@bar.com");
      ExternalIdNotes extIdNotes = ExternalIdNotes.loadNoCacheUpdate(ALL_USERS, repo);
      for (int i = 2; i < 60; i++) {
        extIdNotes.insert(externalId(i, i));
      }
      try (MetaDataUpdate metaDataUpdate =
          new MetaDataUpdate(GitReferenceUpdated.DISABLED, null, repo)) {
        metaDataUpdate.getCommitBuilder().setAuthor(updater);
        metaDataUpdate.getCommitBuilder().setCommitter(updater);
        head = extIdNotes.commit(metaDataUpdate).getId();
      }
    }

    when(externalIdCache.getIfPresent(firstState)).thenReturn(allFromGit(firstState));

    assertThat(loader.load(head)).isEqualTo(allFromGit(head));
    verify(externalIdReaderSpy, times(1)).all(head);
  }

  @Test
  public void handlesDeletionInPartialReload() throws Exception {
    ObjectId firstState = insertExternalId(1, 1);
    ObjectId head = deleteExternalId(1, 1);
    assertThat(allFromGit(head).byAccount().size()).isEqualTo(0);

    when(externalIdCache.getIfPresent(firstState)).thenReturn(allFromGit(firstState));

    assertThat(loader.load(head)).isEqualTo(allFromGit(head));
    verifyZeroInteractions(externalIdReaderSpy);
  }

  @Test
  public void handlesModifyInPartialReload() throws Exception {
    ObjectId firstState = insertExternalId(1, 1);
    ObjectId head =
        modifyExternalId(
            externalId(1, 1),
            ExternalId.create("fooschema", "bar1", Account.id(1), "foo@bar.com", "password"));
    assertThat(allFromGit(head).byAccount().size()).isEqualTo(1);

    when(externalIdCache.getIfPresent(firstState)).thenReturn(allFromGit(firstState));

    assertThat(loader.load(head)).isEqualTo(allFromGit(head));
    verifyZeroInteractions(externalIdReaderSpy);
  }

  @Test
  public void ignoresInvalidExternalId() throws Exception {
    ObjectId firstState = insertExternalId(1, 1);
    ObjectId head;
    try (Repository repo = repoManager.openRepository(ALL_USERS);
        RevWalk rw = new RevWalk(repo)) {
      ExternalIdTestUtil.insertExternalIdWithKeyThatDoesntMatchNoteId(
          repo, rw, new PersonIdent("foo", "foo@bar.com"), Account.id(2), "test");
      head = repo.exactRef(RefNames.REFS_EXTERNAL_IDS).getObjectId();
    }

    when(externalIdCache.getIfPresent(firstState)).thenReturn(allFromGit(firstState));

    assertThat(loader.load(head)).isEqualTo(allFromGit(head));
    verifyZeroInteractions(externalIdReaderSpy);
  }

  private ExternalIdCacheLoader createLoader(boolean allowPartial) {
    Config cfg = new Config();
    cfg.setBoolean("cache", "external_ids_map", "enablePartialReloads", allowPartial);
    return new ExternalIdCacheLoader(
        repoManager,
        ALL_USERS,
        externalIdReaderSpy,
        Providers.of(externalIdCache),
        new DisabledMetricMaker(),
        cfg);
  }

  private AllExternalIds allFromGit(ObjectId revision) throws Exception {
    return AllExternalIds.create(externalIdReader.all(revision));
  }

  private ObjectId insertExternalId(int key, int accountId) throws Exception {
    return performExternalIdUpdate(
        u -> {
          try {
            u.insert(externalId(key, accountId));
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
  }

  private ObjectId modifyExternalId(ExternalId oldId, ExternalId newId) throws Exception {
    return performExternalIdUpdate(
        u -> {
          try {
            u.replace(oldId, newId);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
  }

  private ObjectId deleteExternalId(int key, int accountId) throws Exception {
    return performExternalIdUpdate(u -> u.delete(externalId(key, accountId)));
  }

  private ExternalId externalId(int key, int accountId) {
    return ExternalId.create("fooschema", "bar" + key, Account.id(accountId));
  }

  private ObjectId performExternalIdUpdate(Consumer<ExternalIdNotes> update) throws Exception {
    try (Repository repo = repoManager.openRepository(ALL_USERS)) {
      PersonIdent updater = new PersonIdent("Foo bar", "foo@bar.com");
      ExternalIdNotes extIdNotes = ExternalIdNotes.loadNoCacheUpdate(ALL_USERS, repo);
      update.accept(extIdNotes);
      try (MetaDataUpdate metaDataUpdate =
          new MetaDataUpdate(GitReferenceUpdated.DISABLED, null, repo)) {
        metaDataUpdate.getCommitBuilder().setAuthor(updater);
        metaDataUpdate.getCommitBuilder().setCommitter(updater);
        return extIdNotes.commit(metaDataUpdate).getId();
      }
    }
  }
}
