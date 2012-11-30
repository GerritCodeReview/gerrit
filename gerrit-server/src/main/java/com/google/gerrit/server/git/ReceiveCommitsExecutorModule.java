// Copyright (C) 2012 The Android Open Source Project
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

import com.google.common.util.concurrent.ForwardingListeningExecutorService;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Module providing the {@link ReceiveCommitsExecutor}. */
public class ReceiveCommitsExecutorModule extends AbstractModule {
  @Override
  protected void configure() {
  }

  @Provides
  @Singleton
  @ReceiveCommitsExecutor
  public WorkQueue.Executor createReceiveCommitsExecutor(
      @GerritServerConfig Config config,
      WorkQueue queues) {
    int poolSize = config.getInt("receive", null, "threadPoolSize",
        Runtime.getRuntime().availableProcessors());
    return queues.createQueue(poolSize, "ReceiveCommits");
  }

  @Provides
  @Singleton
  @ChangeUpdateExecutor
  public RequestScopeAwareListeningExecutorService createChangeUpdateExecutor(
      @GerritServerConfig Config config) {
    ListeningExecutorService e;
    int poolSize = config.getInt("receive", null, "changeUpdateThreads", 1);
    if (poolSize <= 1) {
      e = MoreExecutors.sameThreadExecutor();
    } else {
      e = MoreExecutors.listeningDecorator(
          MoreExecutors.getExitingExecutorService(
            new ThreadPoolExecutor(1, poolSize,
                10, TimeUnit.MINUTES,
                new ArrayBlockingQueue<Runnable>(poolSize),
                new ThreadFactoryBuilder()
                  .setNameFormat("ChangeUpdate-%d")
                  .setDaemon(true)
                  .build(),
                new ThreadPoolExecutor.CallerRunsPolicy())));
    }
    return new RequestScopeAwareListeningExecutorService(e, poolSize > 1);
  }

  public class RequestScopeAwareListeningExecutorService extends
      ForwardingListeningExecutorService {
    private ListeningExecutorService e;
    private boolean applyRequestScope;
    private RequestScopePropagator requestScopePropagator;

    public RequestScopeAwareListeningExecutorService(
        final ListeningExecutorService e, boolean applyRequestScope) {
      this.e = e;
      this.applyRequestScope = applyRequestScope;
    }

    public RequestScopeAwareListeningExecutorService setRequestScopePropagator(
        final RequestScopePropagator requestScopePropagator) {
      this.requestScopePropagator = requestScopePropagator;
      return this;
    }

    @Override
    protected ListeningExecutorService delegate() {
      return e;
    }

    @Override
    public <T> ListenableFuture<T> submit(Callable<T> task) {
      if (applyRequestScope) {
        return super.submit(requestScopePropagator.wrap(task));
      } else {
        return super.submit(task);
      }
    }
  }
}
