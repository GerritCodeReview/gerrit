// Copyright (C) 2026 The Android Open Source Project
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
import static com.google.common.util.concurrent.Uninterruptibles.awaitUninterruptibly;

import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.testing.ConfigSuite;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

@RunWith(ConfigSuite.class)
public class AllUsersRepositoryProviderTest {
  private static final int TIMEOUT_SECONDS = 5;

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  @ConfigSuite.Parameter public Config cfg;

  private AllUsersRepositoryProvider provider;
  private TrackingRepositoryManager repoManager;

  @ConfigSuite.Config
  public static Config withoutFileKeyCache() {
    Config cfg = new Config();
    cfg.setBoolean("core", null, "useFileKeyByProjectCache", false);
    return cfg;
  }

  @Before
  public void setUp() throws Exception {
    SitePaths site = new SitePaths(temporaryFolder.newFolder().toPath());
    cfg.setString("gerrit", null, "basePath", "git");
    repoManager = new TrackingRepositoryManager(site, cfg);
    AllUsersName allUsersName = new AllUsersName("All-Users");
    provider = new AllUsersRepositoryProvider(repoManager, allUsersName);
    try (Repository ignored = repoManager.createRepository(allUsersName)) {
      // Create All-Users before the provider opens it.
    }
  }

  @After
  public void tearDown() {
    provider.stop();
  }

  @Test
  public void returnsSharedRepository() {
    Repository shared = provider.get();
    assertThat(provider.get()).isSameInstanceAs(shared);
  }

  @Test
  public void closingSharedRepositoryDoesNotCloseServerOwnedHandle() {
    Repository shared = provider.get();
    shared.close();
    assertThat(repoManager.openedRepository.closed).isFalse();
  }

  @Test
  public void concurrentInitializationClosesUnusedHandle() throws Exception {
    ConcurrentLinkedQueue<TrackingRepository> opened = new ConcurrentLinkedQueue<>();
    CountDownLatch bothOpened = new CountDownLatch(2);
    repoManager.onOpen =
        repo -> {
          opened.add(repo);
          bothOpened.countDown();
          // Let's wait for both callers to have opened a handle
          assertThat(awaitUninterruptibly(bothOpened, TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        };
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<Repository> first = executor.submit(provider::get);
      Future<Repository> second = executor.submit(provider::get);
      assertThat(second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
          .isSameInstanceAs(first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
      assertThat(opened.stream().filter(repo -> repo.closed).count()).isEqualTo(1);
    }
  }

  private static class TrackingRepositoryManager extends LocalDiskRepositoryManager {
    private TrackingRepository openedRepository;
    private Consumer<TrackingRepository> onOpen = repo -> {};

    TrackingRepositoryManager(SitePaths site, Config cfg) {
      super(site, cfg);
    }

    @Override
    public synchronized Repository openRepository(NameKey name) throws RepositoryNotFoundException {
      TrackingRepository repo = new TrackingRepository(super.openRepository(name));
      openedRepository = repo;
      onOpen.accept(repo);
      return repo;
    }
  }

  private static class TrackingRepository extends DelegateRepository {
    private boolean closed;

    TrackingRepository(Repository repository) {
      super(repository);
    }

    @Override
    public void close() {
      closed = true;
      super.close();
    }
  }
}
