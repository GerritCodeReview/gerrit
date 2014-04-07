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

package com.google.gerrit.server.vhost;

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gerrit.common.errors.InvalidSshKeyException;
import com.google.gerrit.reviewdb.client.AccountSshKey;
import com.google.gerrit.server.change.MergeabilityChecksExecutor;
import com.google.gerrit.server.documentation.QueryDocumentationExecutor;
import com.google.gerrit.server.git.ChangeUpdateExecutor;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.gerrit.server.git.ReceiveCommitsExecutor;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.index.IndexModule.IndexType;
import com.google.gerrit.server.patch.IntraLineWorkerPool;
import com.google.gerrit.server.project.ProjectCacheClock;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;

import org.eclipse.jgit.lib.Config;

import java.io.File;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Carries JVM-wide server state.
 * <p>
 * This module supplies data that is not site-specific. A single instance of the
 * module is constructed for the Java process. The module itself is added to
 * each site's Guice stack into the cfgInjector. See {@link RunningSite}.
 */
public class GlobalDataModule extends AbstractModule {
  private final Config globalConfig;
  private final File sites;
  private final String canonicalWebUrlFormat;
  private final GlobalCachePool cachePool;
  private final WorkQueue workQueue;
  private final ProjectCacheClock projectCacheClock;
  private final WorkQueue.Executor receiveCommits;
  private final WorkQueue.Executor backgroundMergeabilityChecks;
  private final WorkQueue.Executor interactiveMergeabilityChecks;
  private final ListeningExecutorService changeUpdate;
  private final ListeningExecutorService indexExecutor;
  private final IntraLineWorkerPool intraLinePool;
  private final QueryDocumentationExecutor queryDocExecutor;

  @Inject
  GlobalDataModule(
      @GerritGlobalConfig Config globalConfig,
      WorkQueue workQueue,
      GlobalCachePool caches,
      QueryDocumentationExecutor queryDocExecutor) {
    this.globalConfig = globalConfig;
    this.workQueue = workQueue;
    this.cachePool = caches;
    this.sites = new File(globalConfig.getString("gerrit", "vhost", "sites"));

    this.canonicalWebUrlFormat = Objects.firstNonNull(
        Strings.emptyToNull(globalConfig.getString(
            "gerrit", "vhost", "canonicalWebUrlFormat")),
        "https://%s.example.com/");

    this.projectCacheClock = new ProjectCacheClock(globalConfig);
    this.intraLinePool = new IntraLineWorkerPool(globalConfig);
    this.queryDocExecutor = queryDocExecutor;

    int cores = Runtime.getRuntime().availableProcessors();
    receiveCommits = workQueue.createQueue(
        globalConfig.getInt("receive", null, "threadPoolSize", cores),
        "ReceiveCommits");

    backgroundMergeabilityChecks = workQueue.createQueue(
        globalConfig.getInt("changeMerge", null, "threadPoolSize", cores),
        "MergeabilityChecks-Background");

    interactiveMergeabilityChecks = workQueue.createQueue(
        globalConfig.getInt("changeMerge", null, "interactiveThreadPoolSize", cores),
        "MergeabilityChecks-Interactive");

    indexExecutor = MoreExecutors.listeningDecorator(workQueue.createQueue(
        globalConfig.getInt("index", null, "threads", cores),
        "IndexUpdate"));

    int updateThreads = globalConfig.getInt("receive", null, "changeUpdateThreads", cores);
    if (1 < updateThreads) {
      changeUpdate = MoreExecutors.listeningDecorator(
        new ThreadPoolExecutor(updateThreads, updateThreads,
            0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(updateThreads),
            new ThreadFactoryBuilder()
              .setNameFormat("ChangeUpdate-%d")
              .setDaemon(true)
              .build(),
            new ThreadPoolExecutor.CallerRunsPolicy()));
    } else {
      changeUpdate = MoreExecutors.sameThreadExecutor();
    }

    new LocalDiskRepositoryManager.Lifecycle(globalConfig).start();
  }

  @Override
  protected void configure() {
    // Think carefully about the contents of this method. Bindings written
    // here are published into every Gerrit site loaded within the process.
    // The configure() method is executed many times, for different Guice
    // environments and injector objects.

    bind(GlobalDataModule.class).toInstance(this);
    bind(GlobalCachePool.class).toInstance(cachePool);

    bind(WorkQueue.class).toInstance(workQueue);
    bind(ProjectCacheClock.class).toInstance(projectCacheClock);
    bind(IntraLineWorkerPool.class).toInstance(intraLinePool);
    bind(QueryDocumentationExecutor.class).toInstance(queryDocExecutor);
  }

  ListeningExecutorService getIndexExecutor() {
    return indexExecutor;
  }

  @Provides
  @GerritGlobalConfig
  Config getConfig() {
    return globalConfig;
  }

  @Provides
  @ReceiveCommitsExecutor
  WorkQueue.Executor getReceiveCommitsExecutor() {
    return receiveCommits;
  }

  @Provides
  @ChangeUpdateExecutor
  ListeningExecutorService getChangeUpdateExecutor() {
    return changeUpdate;
  }

  @Provides
  @MergeabilityChecksExecutor(MergeabilityChecksExecutor.Priority.BACKGROUND)
  WorkQueue.Executor getBackgroundMergeabilityChecksExecutor() {
    return backgroundMergeabilityChecks;
  }

  @Provides
  @MergeabilityChecksExecutor(MergeabilityChecksExecutor.Priority.INTERACTIVE)
  WorkQueue.Executor getInteractiveMergeabilityChecksExecutor() {
    return interactiveMergeabilityChecks;
  }

  @Provides
  SshKeyCache getSshKeyCache() {
    return new SshKeyCache() {
      @Override
      public void evict(String username) {
      }

      @Override
      public AccountSshKey create(AccountSshKey.Id id, String encoded)
          throws InvalidSshKeyException {
        throw new InvalidSshKeyException();
      }
    };
  }

  Config createSiteConfig(String siteName) {
    Config cfg = new Config(globalConfig);
    cfg.setString("index", null, "type", IndexType.LUCENE.name());
    cfg.setString("database", null, "type", "H2");
    cfg.setString("sshd", null, "listenAddress", "off");
    cfg.setInt("plugins", null, "checkFrequency", 0);
    cfg.setString("gerrit", null, "canonicalWebUrl",
        String.format(canonicalWebUrlFormat, siteName));
    return cfg;
  }

  File getSitePath(String siteName) {
    return new File(sites, siteName);
  }

  boolean exists(String siteName) {
    return getSitePath(siteName).exists();
  }
}
