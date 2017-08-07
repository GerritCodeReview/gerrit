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

package com.google.gerrit.server.git.receive;

import com.google.common.collect.SetMultimap;
import com.google.gerrit.common.data.Capable;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.HackPushNegotiateHook;
import com.google.gerrit.server.git.MultiProgressMonitor;
import com.google.gerrit.server.git.ProjectRunnable;
import com.google.gerrit.server.git.TransferConfig;
import com.google.gerrit.server.git.VisibleRefFilter;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.util.MagicBranch;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.inject.Inject;
import com.google.inject.PrivateModule;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.name.Named;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.AdvertiseRefsHook;
import org.eclipse.jgit.transport.AdvertiseRefsHookChain;
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
    AsyncReceiveCommits create(
        ProjectControl projectControl,
        Repository repository,
        SetMultimap<ReviewerStateInternal, Account.Id> extraReviewers);
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
    final MultiProgressMonitor progress;

    private final Collection<ReceiveCommand> commands;
    private final ReceiveCommits rc;

    private Worker(Collection<ReceiveCommand> commands) {
      this.commands = commands;
      rc = factory.create(projectControl, rp, allRefsWatcher, extraReviewers);
      rc.init();
      progress = new MultiProgressMonitor(new MessageSenderOutputStream(), "Processing changes");
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

    void sendMessages() {
      rc.sendMessages();
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
  }

  private final ReceiveCommits.Factory factory;
  private final ReceivePack rp;
  private final ExecutorService executor;
  private final RequestScopePropagator scopePropagator;
  private final ReceiveConfig receiveConfig;
  private final long timeoutMillis;
  private final ProjectControl projectControl;
  private final Repository repo;
  private final SetMultimap<ReviewerStateInternal, Account.Id> extraReviewers;
  private final AllRefsWatcher allRefsWatcher;

  @Inject
  AsyncReceiveCommits(
      ReceiveCommits.Factory factory,
      PermissionBackend permissionBackend,
      VisibleRefFilter.Factory refFilterFactory,
      Provider<InternalChangeQuery> queryProvider,
      @ReceiveCommitsExecutor ExecutorService executor,
      RequestScopePropagator scopePropagator,
      ReceiveConfig receiveConfig,
      TransferConfig transferConfig,
      Provider<LazyPostReceiveHookChain> lazyPostReceive,
      @Named(TIMEOUT_NAME) long timeoutMillis,
      @Assisted ProjectControl projectControl,
      @Assisted Repository repo,
      @Assisted SetMultimap<ReviewerStateInternal, Account.Id> extraReviewers)
      throws PermissionBackendException {
    this.factory = factory;
    this.executor = executor;
    this.scopePropagator = scopePropagator;
    this.receiveConfig = receiveConfig;
    this.projectControl = projectControl;
    this.repo = repo;
    this.timeoutMillis = timeoutMillis;
    this.extraReviewers = extraReviewers;

    IdentifiedUser user = projectControl.getUser().asIdentifiedUser();
    ProjectState state = projectControl.getProjectState();
    Project.NameKey projectName = projectControl.getProject().getNameKey();
    rp = new ReceivePack(repo);
    rp.setAllowCreates(true);
    rp.setAllowDeletes(true);
    rp.setAllowNonFastForwards(true);
    rp.setRefLogIdent(user.newRefLogIdent());
    rp.setTimeout(transferConfig.getTimeout());
    rp.setMaxObjectSizeLimit(transferConfig.getEffectiveMaxObjectSizeLimit(state));
    rp.setCheckReceivedObjects(state.getConfig().getCheckReceivedObjects());
    rp.setRefFilter(new ReceiveRefFilter());
    rp.setAllowPushOptions(true);
    rp.setPreReceiveHook(this);
    rp.setPostReceiveHook(lazyPostReceive.get());

    // If the user lacks READ permission, some references may be filtered and hidden from view.
    // Check objects mentioned inside the incoming pack file are reachable from visible refs.
    try {
      permissionBackend.user(user).project(projectName).check(ProjectPermission.READ);
    } catch (AuthException e) {
      rp.setCheckReferencedObjectsAreReachable(receiveConfig.checkReferencedObjectsAreReachable);
    }

    List<AdvertiseRefsHook> advHooks = new ArrayList<>(4);
    allRefsWatcher = new AllRefsWatcher();
    advHooks.add(allRefsWatcher);
    advHooks.add(refFilterFactory.create(state, repo).setShowMetadata(false));
    advHooks.add(new ReceiveCommitsAdvertiseRefsHook(queryProvider, projectName));
    advHooks.add(new HackPushNegotiateHook());
    rp.setAdvertiseRefsHook(AdvertiseRefsHookChain.newChain(advHooks));
  }

  /** Determine if the user can upload commits. */
  public Capable canUpload() {
    Capable result = projectControl.canPushToAtLeastOneRef();
    if (result != Capable.OK) {
      return result;
    }
    if (receiveConfig.checkMagicRefs) {
      result = MagicBranch.checkMagicBranchRefs(repo, projectControl.getProject());
    }
    return result;
  }

  @Override
  public void onPreReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
    Worker w = new Worker(commands);
    try {
      w.progress.waitFor(
          executor.submit(scopePropagator.wrap(w)), timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (ExecutionException e) {
      log.warn(
          String.format(
              "Error in ReceiveCommits while processing changes for project %s",
              projectControl.getProject().getName()),
          e);
      rp.sendError("internal error while processing changes");
      // ReceiveCommits has tried its best to catch errors, so anything at this
      // point is very bad.
      for (ReceiveCommand c : commands) {
        if (c.getResult() == Result.NOT_ATTEMPTED) {
          c.setResult(Result.REJECTED_OTHER_REASON, "internal error");
        }
      }
    } finally {
      w.sendMessages();
    }
  }

  public ReceivePack getReceivePack() {
    return rp;
  }
}
