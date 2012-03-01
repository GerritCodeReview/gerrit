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

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.gerrit.server.git.WorkQueue.Executor;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.Inject;
import com.google.inject.PrivateModule;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PreReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/** Hook that delegates to {@link ReceiveCommits} in a worker thread. */
public class AsyncReceiveCommits implements PreReceiveHook {
  private static final Logger log =
      LoggerFactory.getLogger(AsyncReceiveCommits.class);

  private final ReceiveCommits rc;
  private final Executor executor;
  private final RequestScopePropagator scopePropagator;

  public interface Factory {
    AsyncReceiveCommits create(ProjectControl projectControl,
        Repository repository);
  }

  public static class Module extends PrivateModule {
    @Override
    public void configure() {
      install(new FactoryModuleBuilder()
          .build(AsyncReceiveCommits.Factory.class));
      expose(AsyncReceiveCommits.Factory.class);
      // Don't expose the binding for ReceiveCommits.Factory. All callers should
      // be using AsyncReceiveCommits.Factory instead.
      install(new FactoryModuleBuilder()
          .build(ReceiveCommits.Factory.class));
    }
  }

  private class Worker implements ProjectRunnable {
    private final Collection<ReceiveCommand> commands;

    private Worker(final Collection<ReceiveCommand> commands) {
      this.commands = commands;
    }

    @Override
    public void run() {
      rc.processCommands(commands);
    }

    @Override
    public Project.NameKey getProjectNameKey() {
      return rc.getProject().getNameKey();
    }

    @Override
    public String getRemoteName() {
      return null;
    }

    @Override
    public boolean hasCustomizedPrint() {
      return true;
    }

    @Override
    public String toString() {
      return "receive-commits";
    }
  }

  @Inject
  AsyncReceiveCommits(final ReceiveCommits.Factory factory,
      @ReceiveCommitsExecutor final Executor executor,
      final RequestScopePropagator scopePropagator,
      @Assisted final ProjectControl projectControl,
      @Assisted final Repository repo) {
    this.executor = executor;
    this.scopePropagator = scopePropagator;
    rc = factory.create(projectControl, repo);
    rc.getReceivePack().setPreReceiveHook(this);
  }

  @Override
  public void onPreReceive(final ReceivePack rp,
      final Collection<ReceiveCommand> commands) {
    Future<?> workerFuture = executor.submit(
        scopePropagator.wrap(new Worker(commands)));
    Exception err = null;
    try {
      workerFuture.get();
    } catch (ExecutionException e) {
      err = e;
    } catch (InterruptedException e) {
      err = e;
    }
    if (err != null) {
      log.warn("Error in ReceiveCommits", err);
      rc.getMessageSender().sendError("internal error while processing changes");
      // ReceiveCommits has tried its best to catch errors, so anything at this
      // point is very bad.
      for (final ReceiveCommand c : commands) {
        if (c.getResult() == ReceiveCommand.Result.NOT_ATTEMPTED) {
          ReceiveCommits.reject(c, "internal error");
        }
      }
    }
  }

  public ReceiveCommits getReceiveCommits() {
    return rc;
  }
}
