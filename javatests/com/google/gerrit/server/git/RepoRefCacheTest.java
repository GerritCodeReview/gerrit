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

import com.google.gerrit.testing.GerritBaseTests;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.attributes.AttributesNodeProvider;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.BaseRepositoryBuilder;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.util.FS;
import org.junit.Test;

public class RepoRefCacheTest extends GerritBaseTests {

  @SuppressWarnings("resource")
  @Test
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
    cache.close();
  }

  @SuppressWarnings("rawtypes")
  static class RepositoryWrapper extends Repository {
    final AtomicInteger useCnt = new AtomicInteger(1);

    static BaseRepositoryBuilder builder() {
      return new InMemoryRepository.Builder()
          .setRepositoryDescription(new DfsRepositoryDescription("foo"))
          .setFS(FS.detect().setUserHome(null));
    }

    private final Repository repo;

    protected RepositoryWrapper(BaseRepositoryBuilder builder) throws IOException {
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
      return createAttributesNodeProvider();
    }

    @Override
    public void scanForRepoChanges() throws IOException {}

    @Override
    public void notifyIndexChanged(boolean internal) {}

    @Override
    public ReflogReader getReflogReader(String refName) throws IOException {
      return getReflogReader(refName);
    }
  }
}
