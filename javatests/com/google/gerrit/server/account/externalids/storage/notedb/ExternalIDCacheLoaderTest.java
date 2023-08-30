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

package com.google.gerrit.server.account.externalids.storage.notedb;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.google.common.base.CharMatcher;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.metrics.DisabledMetricMaker;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdKeyFactory;
import com.google.gerrit.server.account.externalids.testing.ExternalIdTestUtil;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AllUsersNameProvider;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ExternalIDCacheLoaderTest {
  private static AllUsersName ALL_USERS = new AllUsersName(AllUsersNameProvider.DEFAULT);

  private Cache<ObjectId, AllExternalIds> externalIdCache;
  private ExternalIdCacheLoader loader;
  private GitRepositoryManager repoManager = new InMemoryRepositoryManager();
  private ExternalIdReader externalIdReader;
  private ExternalIdReader externalIdReaderSpy;

  private ExternalIdFactoryNoteDbImpl externalIdFactory;
  @Mock private AuthConfig authConfig;

  @Before
  public void setUp() throws Exception {
    externalIdFactory =
        new ExternalIdFactoryNoteDbImpl(new ExternalIdKeyFactory(() -> false), authConfig);
    externalIdCache = CacheBuilder.newBuilder().build();
    repoManager.createRepository(ALL_USERS).close();
    externalIdReader =
        new ExternalIdReader(
            repoManager, ALL_USERS, new DisabledMetricMaker(), externalIdFactory, authConfig);
    externalIdReaderSpy = Mockito.spy(externalIdReader);
    loader = createLoader();
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
    externalIdCache.put(firstState, allFromGit(firstState));

    assertThat(loader.load(head)).isEqualTo(allFromGit(head));
    verify(externalIdReaderSpy, times(1)).checkReadEnabled();
    verifyNoMoreInteractions(externalIdReaderSpy);
  }

  @Test
  public void loadCacheSuccessfullyWhenInInconsistentState() throws Exception {
    int key = 1;
    int account = 1;
    ExternalId externalId = externalId(key, account);
    ExternalId.Key externalIdKey = externalId.key();

    Repository repo = repoManager.openRepository(ALL_USERS);
    ObjectId newState = insertExternalId(key, account);
    try (TreeWalk tw = new TreeWalk(repo);
        RevWalk rw = new RevWalk(repo)) {
      tw.reset(rw.parseCommit(newState).getTree());
      tw.next();

      HashMap<ObjectId, ObjectId> additions = new HashMap<>();
      additions.put(fileNameToObjectId(tw.getPathString()), tw.getObjectId(0));
      AllExternalIds oldExternalIds =
          AllExternalIds.create(Stream.<ExternalId>builder().add(externalId).build());

      AllExternalIds allExternalIds =
          loader.buildAllExternalIds(repo, oldExternalIds, additions, new HashSet<>());

      assertThat(allExternalIds).isNotNull();
      assertThat(allExternalIds.byKey().containsKey(externalIdKey)).isTrue();
      assertThat(allExternalIds.byKey().get(externalIdKey)).isEqualTo(externalId);
    }
  }

  private static ObjectId fileNameToObjectId(String path) {
    return ObjectId.fromString(CharMatcher.is('/').removeFrom(path));
  }

  @Test
  public void reloadsMultipleUpdatesUsingPartialReload() throws Exception {
    ObjectId firstState = insertExternalId(1, 1);
    insertExternalId(2, 2);
    insertExternalId(3, 3);
    ObjectId head = insertExternalId(4, 4);
    externalIdCache.put(firstState, allFromGit(firstState));

    assertThat(loader.load(head)).isEqualTo(allFromGit(head));
    verify(externalIdReaderSpy, times(1)).checkReadEnabled();
    verifyNoMoreInteractions(externalIdReaderSpy);
  }

  @Test
  public void reloadsAllExternalIdsWhenNoOldStateIsCached() throws Exception {
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
  public void doesFullReloadWhenNoCacheStateIsFound() throws Exception {
    ObjectId head = insertExternalId(1, 1);

    assertThat(loader.load(head)).isEqualTo(allFromGit(head));
    verify(externalIdReaderSpy, times(1)).all(head);
  }

  @Test
  public void handlesDeletionInPartialReload() throws Exception {
    ObjectId firstState = insertExternalId(1, 1);
    ObjectId head = deleteExternalId(1, 1);
    assertThat(allFromGit(head).byAccount().size()).isEqualTo(0);
    externalIdCache.put(firstState, allFromGit(firstState));

    assertThat(loader.load(head)).isEqualTo(allFromGit(head));
    verify(externalIdReaderSpy, times(1)).checkReadEnabled();
    verifyNoMoreInteractions(externalIdReaderSpy);
  }

  @Test
  public void handlesModifyInPartialReload() throws Exception {
    ObjectId firstState = insertExternalId(1, 1);
    ObjectId head =
        modifyExternalId(
            externalId(1, 1),
            externalIdFactory.create(
                "fooschema", "bar1", Account.id(1), "foo@bar.com", "password"));
    assertThat(allFromGit(head).byAccount().size()).isEqualTo(1);
    externalIdCache.put(firstState, allFromGit(firstState));

    assertThat(loader.load(head)).isEqualTo(allFromGit(head));
    verify(externalIdReaderSpy, times(1)).checkReadEnabled();
    verifyNoMoreInteractions(externalIdReaderSpy);
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

    externalIdCache.put(firstState, allFromGit(firstState));

    assertThat(loader.load(head)).isEqualTo(allFromGit(head));
    verify(externalIdReaderSpy, times(1)).checkReadEnabled();
    verifyNoMoreInteractions(externalIdReaderSpy);
  }

  @Test
  public void handlesTreePrefixesInDifferentialReload() throws Exception {
    // Create more than 256 notes (NoteMap's current sharding limit) and check that we really have
    // created a situation where NoteNames are sharded.
    ObjectId oldState = insertExternalIds(257);
    assertAllFilesHaveSlashesInPath();
    ObjectId head = insertExternalId(500, 500);
    externalIdCache.put(oldState, allFromGit(oldState));

    assertThat(loader.load(head)).isEqualTo(allFromGit(head));
    verify(externalIdReaderSpy, times(1)).checkReadEnabled();
    verifyNoMoreInteractions(externalIdReaderSpy);
  }

  @Test
  public void handlesReshard() throws Exception {
    // Create 256 notes (NoteMap's current sharding limit) and check that we are not yet sharding
    ObjectId oldState = insertExternalIds(256);
    assertNoFilesHaveSlashesInPath();
    // Create one more external ID and then have the Loader compute the new state
    ObjectId head = insertExternalId(500, 500);
    assertAllFilesHaveSlashesInPath(); // NoteMap resharded
    externalIdCache.put(oldState, allFromGit(oldState));

    assertThat(loader.load(head)).isEqualTo(allFromGit(head));
    verify(externalIdReaderSpy, times(1)).checkReadEnabled();
    verifyNoMoreInteractions(externalIdReaderSpy);
  }

  private ExternalIdCacheLoader createLoader() {
    return new ExternalIdCacheLoader(
        repoManager,
        ALL_USERS,
        externalIdReaderSpy,
        externalIdCache,
        new DisabledMetricMaker(),
        new Config(),
        externalIdFactory);
  }

  private AllExternalIds allFromGit(ObjectId revision) throws Exception {
    return AllExternalIds.create(externalIdReader.all(revision).stream());
  }

  private ObjectId insertExternalIds(int numberOfIdsToInsert) throws Exception {
    ObjectId oldState = null;
    // Create more than 256 notes (NoteMap's current sharding limit) and check that we really have
    // created a situation where NoteNames are sharded.
    for (int i = 0; i < numberOfIdsToInsert; i++) {
      oldState = insertExternalId(i, i);
    }
    return oldState;
  }

  @CanIgnoreReturnValue
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
    return externalIdFactory.create("fooschema", "bar" + key, Account.id(accountId));
  }

  private ObjectId performExternalIdUpdate(Consumer<ExternalIdNotes> update) throws Exception {
    try (Repository repo = repoManager.openRepository(ALL_USERS)) {
      PersonIdent updater = new PersonIdent("Foo bar", "foo@bar.com");
      ExternalIdNotes extIdNotes = ExternalIdNotes.load(ALL_USERS, repo, externalIdFactory, false);
      update.accept(extIdNotes);
      try (MetaDataUpdate metaDataUpdate =
          new MetaDataUpdate(GitReferenceUpdated.DISABLED, null, repo)) {
        metaDataUpdate.getCommitBuilder().setAuthor(updater);
        metaDataUpdate.getCommitBuilder().setCommitter(updater);
        return extIdNotes.commit(metaDataUpdate).getId();
      }
    }
  }

  private void assertAllFilesHaveSlashesInPath() throws Exception {
    assertThat(allFilesInExternalIdRef().stream().allMatch(f -> f.contains("/"))).isTrue();
  }

  private void assertNoFilesHaveSlashesInPath() throws Exception {
    assertThat(allFilesInExternalIdRef().stream().noneMatch(f -> f.contains("/"))).isTrue();
  }

  private ImmutableList<String> allFilesInExternalIdRef() throws Exception {
    try (Repository repo = repoManager.openRepository(ALL_USERS);
        TreeWalk treeWalk = new TreeWalk(repo);
        RevWalk rw = new RevWalk(repo)) {
      treeWalk.reset(
          rw.parseCommit(repo.exactRef(RefNames.REFS_EXTERNAL_IDS).getObjectId()).getTree());
      treeWalk.setRecursive(true);
      ImmutableList.Builder<String> allPaths = ImmutableList.builder();
      while (treeWalk.next()) {
        allPaths.add(treeWalk.getPathString());
      }
      return allPaths.build();
    }
  }
}
