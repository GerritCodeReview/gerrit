// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.server.git;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.server.cache.PerThreadCache;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.attributes.AttributesNodeProvider;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository.Builder;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.util.FS;
import org.junit.Test;

public class RepoRefCacheTest {
  private static final String TEST_BRANCH = "main";

  @Test
  @SuppressWarnings("resource")
  public void repositoryUseShouldBeTrackedByRepoRefCache() throws Exception {
    RefCache cache;
    TestRepositoryWithRefCounting repoWithRefCounting;

    try (TestRepositoryWithRefCounting repo =
        TestRepositoryWithRefCounting.createWithBranch(TEST_BRANCH)) {
      assertThat(repo.refCounter()).isEqualTo(1);
      repoWithRefCounting = repo;
      cache = new RepoRefCache(repo);
    }

    assertThat(repoWithRefCounting.refCounter()).isEqualTo(1);
    assertThat(cache.get(Constants.R_HEADS + TEST_BRANCH)).isNotNull();
  }

  @Test
  @SuppressWarnings("resource")
  public void shouldNotKeepReferenceToReposWhenCacheIsFull() throws Exception {
    TestRepositoryWithRefCounting repoPointedFromCache;

    try (PerThreadCache threadCache = PerThreadCache.createReadOnly()) {
      fillUpAllThreadCache(threadCache);

      try (TestRepositoryWithRefCounting repo =
          TestRepositoryWithRefCounting.createWithBranch(TEST_BRANCH)) {
        repoPointedFromCache = repo;
        assertThat(repo.refCounter()).isEqualTo(1);
        Optional<RefCache> refCache = RepoRefCache.getOptional(repo);
        assertThat(refCache).isEqualTo(Optional.empty());
        assertThat(repo.refCounter()).isEqualTo(1);
      }

      assertThat(repoPointedFromCache.refCounter()).isEqualTo(0);
    }
  }

  @Test
  public void shouldCheckForStaleness() throws Exception {
    String refName = "refs/heads/foo";

    try (TestRepositoryWithRefCounting repo =
        TestRepositoryWithRefCounting.createWithBranch(TEST_BRANCH)) {
      RepoRefCache refCache = new RepoRefCache(repo);
      TestRepository<Repository> testRepo = new TestRepository<>(repo);

      Optional<ObjectId> cachedObjId = refCache.get(refName);

      assertThat(cachedObjId).isEqualTo(Optional.empty());

      RefUpdate refUpdate = repo.getRefDatabase().newUpdate(refName, true);
      refUpdate.setNewObjectId(testRepo.commit().create().getId());

      assertThat(refUpdate.forceUpdate()).isEqualTo(Result.NEW);

      IllegalStateException thrown =
          assertThrows(IllegalStateException.class, () -> refCache.checkStaleness());
      assertThat(thrown).hasMessageThat().contains(refName);
    }
  }

  private void fillUpAllThreadCache(PerThreadCache cache) {

    // Fill the cache
    for (int i = 0; i < 50; i++) {
      PerThreadCache.Key<String> key = PerThreadCache.Key.create(String.class, i);
      cache.getWithLoader(key, () -> "cached value", null);
    }
  }

  private static class TestRepositoryWithRefCounting extends Repository {
    private int refCounter;

    static TestRepositoryWithRefCounting createWithBranch(String branchName) throws Exception {
      Builder builder =
          new InMemoryRepository.Builder()
              .setRepositoryDescription(new DfsRepositoryDescription(""))
              .setFS(FS.detect().setUserHome(null));
      TestRepositoryWithRefCounting testRepo = new TestRepositoryWithRefCounting(builder);
      new TestRepository<>(testRepo).branch(branchName).commit().message("").create();
      return testRepo;
    }

    private final Repository repo;

    private TestRepositoryWithRefCounting(InMemoryRepository.Builder builder) throws IOException {
      super(builder);

      repo = builder.build();
      refCounter = 1;
    }

    public int refCounter() {
      return refCounter;
    }

    @Override
    public void incrementOpen() {
      repo.incrementOpen();
      refCounter++;
    }

    @Override
    public void close() {
      repo.close();
      refCounter--;
    }

    @Override
    public void create(boolean bare) throws IOException {}

    @Override
    public ObjectDatabase getObjectDatabase() {
      checkIsOpen();
      return repo.getObjectDatabase();
    }

    @Override
    public RefDatabase getRefDatabase() {
      RefDatabase refDatabase = repo.getRefDatabase();
      return new RefDatabase() {

        @Override
        public int hashCode() {
          return refDatabase.hashCode();
        }

        @Override
        public void create() throws IOException {
          refDatabase.create();
        }

        @Override
        public void close() {
          checkIsOpen();
          refDatabase.close();
        }

        @Override
        public boolean isNameConflicting(String name) throws IOException {
          checkIsOpen();
          return refDatabase.isNameConflicting(name);
        }

        @Override
        public boolean equals(Object obj) {
          return refDatabase.equals(obj);
        }

        @Override
        public Collection<String> getConflictingNames(String name) throws IOException {
          checkIsOpen();
          return refDatabase.getConflictingNames(name);
        }

        @Override
        public RefUpdate newUpdate(String name, boolean detach) throws IOException {
          checkIsOpen();
          return refDatabase.newUpdate(name, detach);
        }

        @Override
        public RefRename newRename(String fromName, String toName) throws IOException {
          checkIsOpen();
          return refDatabase.newRename(fromName, toName);
        }

        @Override
        public BatchRefUpdate newBatchUpdate() {
          checkIsOpen();
          return refDatabase.newBatchUpdate();
        }

        @Override
        public boolean performsAtomicTransactions() {
          checkIsOpen();
          return refDatabase.performsAtomicTransactions();
        }

        @Override
        public Ref exactRef(String name) throws IOException {
          checkIsOpen();
          return refDatabase.exactRef(name);
        }

        @Override
        public String toString() {
          return refDatabase.toString();
        }

        @Override
        public Map<String, Ref> exactRef(String... refs) throws IOException {
          checkIsOpen();
          return refDatabase.exactRef(refs);
        }

        @Override
        public Ref firstExactRef(String... refs) throws IOException {
          checkIsOpen();
          return refDatabase.firstExactRef(refs);
        }

        @Override
        public List<Ref> getRefs() throws IOException {
          checkIsOpen();
          return refDatabase.getRefs();
        }

        @Override
        public Map<String, Ref> getRefs(String prefix) throws IOException {
          checkIsOpen();
          return refDatabase.getRefs(prefix);
        }

        @Override
        public List<Ref> getRefsByPrefix(String prefix) throws IOException {
          checkIsOpen();
          return refDatabase.getRefsByPrefix(prefix);
        }

        @Override
        public boolean hasRefs() throws IOException {
          checkIsOpen();
          return refDatabase.hasRefs();
        }

        @Override
        public List<Ref> getAdditionalRefs() throws IOException {
          checkIsOpen();
          return refDatabase.getAdditionalRefs();
        }

        @Override
        public Ref peel(Ref ref) throws IOException {
          checkIsOpen();
          return refDatabase.peel(ref);
        }

        @Override
        public void refresh() {
          checkIsOpen();
          refDatabase.refresh();
        }
      };
    }

    @Override
    public StoredConfig getConfig() {
      return repo.getConfig();
    }

    @Override
    public AttributesNodeProvider createAttributesNodeProvider() {
      checkIsOpen();
      return repo.createAttributesNodeProvider();
    }

    @Override
    public void scanForRepoChanges() throws IOException {
      checkIsOpen();
    }

    @Override
    public void notifyIndexChanged(boolean internal) {
      checkIsOpen();
    }

    @Override
    public ReflogReader getReflogReader(String refName) throws IOException {
      checkIsOpen();
      return repo.getReflogReader(refName);
    }

    private void checkIsOpen() {
      if (refCounter <= 0) {
        throw new IllegalStateException("Repository is not open (refCounter=" + refCounter + ")");
      }
    }
  }
}
