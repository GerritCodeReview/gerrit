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

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.git.ReceiveCommits.MessageSender;
import com.google.gerrit.server.git.WorkQueue.Executor;
import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PreReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/** Hook that delegates to {@link ReceiveCommits} in a worker thread. */
public class AsyncReceiveCommits implements PreReceiveHook {
  private static final Logger log =
      LoggerFactory.getLogger(AsyncReceiveCommits.class);

  public interface Factory {
    AsyncReceiveCommits create(ProjectControl projectControl,
        Repository repository);
  }

  public static class Module extends AbstractModule {
    @Override
    public void configure() {
    }

    @Provides
    @Singleton
    @ReceiveCommitsExecutor
    Executor getExecutor(@GerritServerConfig final Config config,
        final WorkQueue queues) {
      int poolSize = config.getInt("receive", null, "threadPoolSize",
          Runtime.getRuntime().availableProcessors());
      return queues.createQueue(poolSize, "ReceiveCommits");
    }
  }

  private class Worker implements ProjectRunnable {
    private final Collection<ReceiveCommand> commands;

    private Worker(final Collection<ReceiveCommand> commands) {
      this.commands = commands;
    }

    @Override
    public void run() {
      rc.processCommands(commands, progress);
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

  private class MessageSenderOutputStream extends OutputStream {
    private final MessageSender messageSender = rc.getMessageSender();

    @Override
    public void write(int b) {
      messageSender.sendBytes(new byte[]{(byte)b});
    }

    @Override
    public void write(byte[] what, int off, int len) {
      messageSender.sendBytes(what, off, len);
    }

    @Override
    public void write(byte[] what) {
      messageSender.sendBytes(what);
    }

    @Override
    public void flush() {
      messageSender.flush();
    }
  }

  private final ReceiveCommits rc;
  private final Executor executor;
  private final ScopePropagator scopePropagator;
  private final MultiProgressMonitor progress;
  private final long timeoutMillis;

  @Inject
  AsyncReceiveCommits(@GerritServerConfig final Config cfg,
      final ReceiveCommits.Factory factory,
      @ReceiveCommitsExecutor final Executor executor,
      final ScopePropagator scopePropagator,
      @Assisted final ProjectControl projectControl,
      @Assisted final Repository repo) {
    this.executor = executor;
    this.scopePropagator = scopePropagator;
    rc = factory.create(projectControl, repo);
    rc.getReceivePack().setPreReceiveHook(this);

    progress = new MultiProgressMonitor(
        new MessageSenderOutputStream(), "Updating changes");

    timeoutMillis = ConfigUtil.getTimeUnit(
        cfg, "receive", null, "timeout",
        TimeUnit.MILLISECONDS.convert(1, TimeUnit.MINUTES),
        TimeUnit.MILLISECONDS);
  }

  @Override
  public void onPreReceive(final ReceivePack rp,
      final Collection<ReceiveCommand> commands) {
    try {
      progress.begin(executor.submit(new Worker(commands),
          scopePropagator), timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (ExecutionException e) {
      log.warn("Error in ReceiveCommits", e);
      rc.getMessageSender().sendError("internal error while processing changes");
      // ReceiveCommits has tried its best to catch errors, so anything at this
      // point is very bad.
      for (final ReceiveCommand c : commands) {
        if (c.getResult() == ReceiveCommand.Result.NOT_ATTEMPTED) {
          rc.reject(c, "internal error");
        }
      }
    }
  }

  public ReceiveCommits getReceiveCommits() {
    return rc;
  }
}
