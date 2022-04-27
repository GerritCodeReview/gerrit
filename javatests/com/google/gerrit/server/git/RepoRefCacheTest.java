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

import java.io.IOException;
import org.eclipse.jgit.attributes.AttributesNodeProvider;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository.Builder;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.RefDatabase;
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
