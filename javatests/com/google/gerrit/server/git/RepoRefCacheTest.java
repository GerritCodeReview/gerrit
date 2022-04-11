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
import com.google.gerrit.testing.GerritBaseTests;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.attributes.AttributesNodeProvider;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.util.FS;
import org.junit.After;
import org.junit.Test;

public class RepoRefCacheTest extends GerritBaseTests {
  /** Copied from PerThreadCache because it isn't public */
  private static final int PER_THREAD_CACHE_SIZE = 25;

  @After
  public void cleanupThreadLocalCache() {
    Optional.ofNullable(PerThreadCache.get()).ifPresent(PerThreadCache::close);
  }

  @Test
  @SuppressWarnings("resource")
  public void usingClosedPerThreadCacheFails() throws Exception {
    RefCache cache;

    try (RepositoryWrapper repo = new RepositoryWrapper(RepositoryWrapper.builder())) {
      assertThat(repo.useCnt.get()).isEqualTo(1);
      try (RefCache refCache = new RepoRefCache(repo)) {
        assertThat(repo.useCnt.get()).isEqualTo(2);
        cache = refCache;
      }
      assertThat(repo.useCnt.get()).isEqualTo(1);
    }

    exception.expect(IllegalStateException.class);
    exception.expectMessage("already closed");
    cache.get("foobar");
  }

  @Test
  @SuppressWarnings("resource")
  public void usingRepoCacheOfClosedRepositoryShouldStillWork() throws Exception {
    RefCache cache;
    RepositoryWrapper repoPointedFromCache;

    try (RepositoryWrapper repo = new RepositoryWrapper(RepositoryWrapper.builder())) {
      repoPointedFromCache = repo;

      assertThat(repo.useCnt.get()).isEqualTo(1);
      cache = new RepoRefCache(repo);
      assertThat(repo.useCnt.get()).isEqualTo(2);
    }

    assertThat(repoPointedFromCache.useCnt.get()).isEqualTo(1);
    cache.get("foobar");

    cache.close();
    assertThat(repoPointedFromCache.useCnt.get()).isEqualTo(0);
  }

  @Test
  @SuppressWarnings("resource")
  public void shouldKeepReferenceToReposWhilstCached() throws IOException {
    RefCache cache;
    RepositoryWrapper repoPointedFromCache;

    try (PerThreadCache threadCache = PerThreadCache.createReadOnly()) {

      try (RepositoryWrapper repo = new RepositoryWrapper(RepositoryWrapper.builder())) {
        repoPointedFromCache = repo;
        assertThat(repo.useCnt.get()).isEqualTo(1);
        try (RefCache refCache = RepoRefCache.getOptional(repo).get()) {
          assertThat(repo.useCnt.get()).isEqualTo(2);
          cache = refCache;
        }
        assertThat(repo.useCnt.get()).isEqualTo(2);
      }

      assertThat(repoPointedFromCache.useCnt.get()).isEqualTo(1);
      cache.get("foobar");
    }

    assertThat(repoPointedFromCache.useCnt.get()).isEqualTo(0);
  }

  @Test
  @SuppressWarnings("resource")
  public void shouldNotKeepReferenceToReposWhenCacheIsFull() throws IOException {
    RepositoryWrapper repoPointedFromCache;

    try (PerThreadCache threadCache = PerThreadCache.createReadOnly()) {
      fillUpAllThreadCache(threadCache);

      try (RepositoryWrapper repo = new RepositoryWrapper(RepositoryWrapper.builder())) {
        repoPointedFromCache = repo;
        assertThat(repo.useCnt.get()).isEqualTo(1);
        try (RefCache refCache = RepoRefCache.getOptional(repo).get()) {
          assertThat(repo.useCnt.get()).isEqualTo(2);
        }
        assertThat(repo.useCnt.get()).isEqualTo(1);
      }

      assertThat(repoPointedFromCache.useCnt.get()).isEqualTo(0);
    }
  }

  public void shouldCheckForStaleness() throws Exception {
    String refName = "refs/heads/foo";

    try (InMemoryRepository repo = RepositoryWrapper.builder().build();
        RepoRefCache refCache = new RepoRefCache(repo)) {
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
    for (int i = 0; i < PER_THREAD_CACHE_SIZE; i++) {
      PerThreadCache.Key<String> key = PerThreadCache.Key.create(String.class, i);
      cache.get(key, () -> "cached value");
    }
  }

  static class RepositoryWrapper extends Repository {
    final AtomicInteger useCnt = new AtomicInteger(1);

    static InMemoryRepository.Builder builder() {
      return new InMemoryRepository.Builder()
          .setRepositoryDescription(new DfsRepositoryDescription("foo"))
          .setFS(FS.detect().setUserHome(null));
    }

    private final Repository repo;

    protected RepositoryWrapper(InMemoryRepository.Builder builder) throws IOException {
      super(builder);

      repo = builder.build();
    }

    @Override
    public void incrementOpen() {
      repo.incrementOpen();
      useCnt.incrementAndGet();
    }

    @Override
    public void close() {
      repo.close();
      useCnt.decrementAndGet();
    }

    @Override
    public void create(boolean bare) throws IOException {}

    @Override
    public ObjectDatabase getObjectDatabase() {
      return repo.getObjectDatabase();
    }

    @Override
    public RefDatabase getRefDatabase() {
      return repo.getRefDatabase();
    }

    @Override
    public StoredConfig getConfig() {
      return repo.getConfig();
    }

    @Override
    public AttributesNodeProvider createAttributesNodeProvider() {
      return repo.createAttributesNodeProvider();
    }

    @Override
    public void scanForRepoChanges() throws IOException {}

    @Override
    public void notifyIndexChanged(boolean internal) {}

    @Override
    public ReflogReader getReflogReader(String refName) throws IOException {
      return repo.getReflogReader(refName);
    }
  }
}
