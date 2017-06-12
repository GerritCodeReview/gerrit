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
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.inject.Inject;
import com.google.inject.PrivateModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.name.Named;
import java.io.OutputStream;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PreReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceiveCommand.Result;
import org.eclipse.jgit.transport.ReceivePack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Hook that delegates to {@link ReceiveCommits} in a worker thread. */
public class AsyncReceiveCommits implements PreReceiveHook {
  private static final Logger log = LoggerFactory.getLogger(AsyncReceiveCommits.class);

  private static final String TIMEOUT_NAME = "ReceiveCommitsOverallTimeout";

  public interface Factory {
    AsyncReceiveCommits create(ProjectControl projectControl, Repository repository);
  }

  public static class Module extends PrivateModule {
    @Override
    public void configure() {
      install(new FactoryModuleBuilder().build(AsyncReceiveCommits.Factory.class));
      expose(AsyncReceiveCommits.Factory.class);
      // Don't expose the binding for ReceiveCommits.Factory. All callers should
      // be using AsyncReceiveCommits.Factory instead.
      install(new FactoryModuleBuilder().build(ReceiveCommits.Factory.class));
    }

    @Provides
    @Singleton
    @Named(TIMEOUT_NAME)
    long getTimeoutMillis(@GerritServerConfig Config cfg) {
      return ConfigUtil.getTimeUnit(
          cfg, "receive", null, "timeout", TimeUnit.MINUTES.toMillis(4), TimeUnit.MILLISECONDS);
    }
  }

  private class Worker implements ProjectRunnable {
    private final Collection<ReceiveCommand> commands;

    private Worker(Collection<ReceiveCommand> commands) {
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
    @Override
    public void write(int b) {
      rc.getMessageSender().sendBytes(new byte[] {(byte) b});
    }

    @Override
    public void write(byte[] what, int off, int len) {
      rc.getMessageSender().sendBytes(what, off, len);
    }

    @Override
    public void write(byte[] what) {
      rc.getMessageSender().sendBytes(what);
    }

    @Override
    public void flush() {
      rc.getMessageSender().flush();
    }
  }

  private final ReceiveCommits rc;
  private final ScheduledThreadPoolExecutor executor;
  private final RequestScopePropagator scopePropagator;
  private final MultiProgressMonitor progress;
  private final long timeoutMillis;

  @Inject
  AsyncReceiveCommits(
      ReceiveCommits.Factory factory,
      @ReceiveCommitsExecutor ScheduledThreadPoolExecutor executor,
      RequestScopePropagator scopePropagator,
      @Named(TIMEOUT_NAME) long timeoutMillis,
      @Assisted ProjectControl projectControl,
      @Assisted Repository repo) {
    this.executor = executor;
    this.scopePropagator = scopePropagator;
    rc = factory.create(projectControl, repo);
    rc.getReceivePack().setPreReceiveHook(this);

    progress = new MultiProgressMonitor(new MessageSenderOutputStream(), "Processing changes");
    this.timeoutMillis = timeoutMillis;
  }

  @Override
  public void onPreReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
    try {
      progress.waitFor(
          executor.submit(scopePropagator.wrap(new Worker(commands))),
          timeoutMillis,
          TimeUnit.MILLISECONDS);
    } catch (ExecutionException e) {
      log.warn(
          String.format(
              "Error in ReceiveCommits while processing changes for project %s",
              rc.getProject().getName()),
          e);
      rc.addError("internal error while processing changes");
      // ReceiveCommits has tried its best to catch errors, so anything at this
      // point is very bad.
      for (ReceiveCommand c : commands) {
        if (c.getResult() == Result.NOT_ATTEMPTED) {
          c.setResult(Result.REJECTED_OTHER_REASON, "internal error");
        }
      }
    } finally {
      rc.sendMessages();
    }
  }

  public ReceiveCommits getReceiveCommits() {
    return rc;
  }
}
