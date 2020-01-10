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

import static com.google.gerrit.server.quota.QuotaGroupDefinitions.REPOSITORY_SIZE_GROUP;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.common.data.Capable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.metrics.Counter0;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.Histogram1;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer1;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PublishCommentsOp;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.ReceiveCommitsExecutor;
import com.google.gerrit.server.git.MultiProgressMonitor;
import com.google.gerrit.server.git.PermissionAwareRepositoryManager;
import com.google.gerrit.server.git.ProjectRunnable;
import com.google.gerrit.server.git.TransferConfig;
import com.google.gerrit.server.git.UsersSelfAdvertiseRefsHook;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.ContributorAgreementsChecker;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.quota.QuotaBackend;
import com.google.gerrit.server.quota.QuotaException;
import com.google.gerrit.server.quota.QuotaResponse;
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
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PreReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceiveCommand.Result;
import org.eclipse.jgit.transport.ReceivePack;

/**
 * Hook that delegates to {@link ReceiveCommits} in a worker thread.
 *
 * <p>Since the work that {@link ReceiveCommits} does may take a long, potentially unbounded amount
 * of time, it runs in the background so it can be monitored for timeouts and cancelled, and have
 * stalls reported to the user from the main thread.
 */
public class AsyncReceiveCommits implements PreReceiveHook {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String TIMEOUT_NAME = "ReceiveCommitsOverallTimeout";

  public interface Factory {
    AsyncReceiveCommits create(
        ProjectState projectState,
        IdentifiedUser user,
        Repository repository,
        @Nullable MessageSender messageSender);
  }

  public static class Module extends PrivateModule {
    @Override
    public void configure() {
      install(new FactoryModuleBuilder().build(LazyPostReceiveHookChain.Factory.class));
      install(new FactoryModuleBuilder().build(AsyncReceiveCommits.Factory.class));
      expose(AsyncReceiveCommits.Factory.class);
      // Don't expose the binding for ReceiveCommits.Factory. All callers should
      // be using AsyncReceiveCommits.Factory instead.
      install(new FactoryModuleBuilder().build(ReceiveCommits.Factory.class));
      install(new FactoryModuleBuilder().build(PublishCommentsOp.Factory.class));
      install(new FactoryModuleBuilder().build(BranchCommitValidator.Factory.class));
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
    final String name;

    private final Collection<ReceiveCommand> commands;

    private Worker(Collection<ReceiveCommand> commands, String name) {
      this.commands = commands;
      this.name = name;
      progress = new MultiProgressMonitor(new MessageSenderOutputStream(), "Processing changes");
    }

    @Override
    public void run() {
      String oldName = Thread.currentThread().getName();
      Thread.currentThread().setName(oldName + "-for-" + name);
      try {
        receiveCommits.processCommands(commands, progress);
      } finally {
        Thread.currentThread().setName(oldName);
      }
    }

    @Override
    public Project.NameKey getProjectNameKey() {
      return receiveCommits.getProject().getNameKey();
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
      receiveCommits.sendMessages();
    }

    private class MessageSenderOutputStream extends OutputStream {
      @Override
      public void write(int b) {
        receiveCommits.getMessageSender().sendBytes(new byte[] {(byte) b});
      }

      @Override
      public void write(byte[] what, int off, int len) {
        receiveCommits.getMessageSender().sendBytes(what, off, len);
      }

      @Override
      public void write(byte[] what) {
        receiveCommits.getMessageSender().sendBytes(what);
      }

      @Override
      public void flush() {
        receiveCommits.getMessageSender().flush();
      }
    }
  }

  private enum PushType {
    CREATE_REPLACE,
    NORMAL,
    AUTOCLOSE,
  }

  @Singleton
  private static class Metrics {
    private final Histogram1<PushType> changes;
    private final Timer1<PushType> latencyPerChange;
    private final Timer1<PushType> latencyPerPush;
    private final Counter0 timeouts;

    @Inject
    Metrics(MetricMaker metricMaker) {
      // For the changes metric the push type field is never set to PushType.NORMAL, hence it is not
      // mentioned in the field description.
      changes =
          metricMaker.newHistogram(
              "receivecommits/changes_per_push",
              new Description("number of changes uploaded in a single push.").setCumulative(),
              Field.ofEnum(PushType.class, "type", Metadata.Builder::pushType)
                  .description("type of push (create/replace, autoclose)")
                  .build());

      Field<PushType> pushTypeField =
          Field.ofEnum(PushType.class, "type", Metadata.Builder::pushType)
              .description("type of push (create/replace, autoclose, normal)")
              .build();

      latencyPerChange =
          metricMaker.newTimer(
              "receivecommits/latency_per_push_per_change",
              new Description(
                      "Processing delay per push divided by the number of changes in said push. "
                          + "(Only includes pushes which contain changes.)")
                  .setUnit(Units.MILLISECONDS)
                  .setCumulative(),
              pushTypeField);

      latencyPerPush =
          metricMaker.newTimer(
              "receivecommits/latency_per_push",
              new Description("processing delay for a processing single push")
                  .setUnit(Units.MILLISECONDS)
                  .setCumulative(),
              pushTypeField);

      timeouts =
          metricMaker.newCounter(
              "receivecommits/timeout", new Description("rate of push timeouts").setRate());
    }
  }

  private final Metrics metrics;
  private final ReceiveCommits receiveCommits;
  private final ResultChangeIds resultChangeIds;
  private final PermissionBackend.ForProject perm;
  private final ReceivePack receivePack;
  private final ExecutorService executor;
  private final RequestScopePropagator scopePropagator;
  private final ReceiveConfig receiveConfig;
  private final ContributorAgreementsChecker contributorAgreements;
  private final long timeoutMillis;
  private final ProjectState projectState;
  private final IdentifiedUser user;
  private final Repository repo;
  private final AllRefsWatcher allRefsWatcher;

  @Inject
  AsyncReceiveCommits(
      ReceiveCommits.Factory factory,
      PermissionBackend permissionBackend,
      Provider<InternalChangeQuery> queryProvider,
      @ReceiveCommitsExecutor ExecutorService executor,
      RequestScopePropagator scopePropagator,
      ReceiveConfig receiveConfig,
      TransferConfig transferConfig,
      LazyPostReceiveHookChain.Factory lazyPostReceive,
      ContributorAgreementsChecker contributorAgreements,
      Metrics metrics,
      QuotaBackend quotaBackend,
      UsersSelfAdvertiseRefsHook usersSelfAdvertiseRefsHook,
      AllUsersName allUsersName,
      @Named(TIMEOUT_NAME) long timeoutMillis,
      @Assisted ProjectState projectState,
      @Assisted IdentifiedUser user,
      @Assisted Repository repo,
      @Assisted @Nullable MessageSender messageSender)
      throws PermissionBackendException {
    this.executor = executor;
    this.scopePropagator = scopePropagator;
    this.receiveConfig = receiveConfig;
    this.contributorAgreements = contributorAgreements;
    this.timeoutMillis = timeoutMillis;
    this.projectState = projectState;
    this.user = user;
    this.repo = repo;
    this.metrics = metrics;
    // If the user lacks READ permission, some references may be filtered and hidden from view.
    // Check objects mentioned inside the incoming pack file are reachable from visible refs.
    Project.NameKey projectName = projectState.getNameKey();
    this.perm = permissionBackend.user(user).project(projectName);

    receivePack = new ReceivePack(PermissionAwareRepositoryManager.wrap(repo, perm));
    receivePack.setAllowCreates(true);
    receivePack.setAllowDeletes(true);
    receivePack.setAllowNonFastForwards(true);
    receivePack.setRefLogIdent(user.newRefLogIdent());
    receivePack.setTimeout(transferConfig.getTimeout());
    receivePack.setMaxObjectSizeLimit(projectState.getEffectiveMaxObjectSizeLimit().value);
    receivePack.setCheckReceivedObjects(projectState.getConfig().getCheckReceivedObjects());
    receivePack.setRefFilter(new ReceiveRefFilter());
    receivePack.setAllowPushOptions(true);
    receivePack.setPreReceiveHook(this);
    receivePack.setPostReceiveHook(lazyPostReceive.create(user, projectName));

    try {
      projectState.checkStatePermitsRead();
      this.perm.check(ProjectPermission.READ);
    } catch (AuthException | ResourceConflictException e) {
      receivePack.setCheckReferencedObjectsAreReachable(
          receiveConfig.checkReferencedObjectsAreReachable);
    }

    allRefsWatcher = new AllRefsWatcher();
    receivePack.setAdvertiseRefsHook(
        ReceiveCommitsAdvertiseRefsHookChain.create(
            allRefsWatcher,
            usersSelfAdvertiseRefsHook,
            allUsersName,
            queryProvider,
            projectName,
            user.getAccountId()));
    resultChangeIds = new ResultChangeIds();
    receiveCommits =
        factory.create(
            projectState, user, receivePack, repo, allRefsWatcher, messageSender, resultChangeIds);
    receiveCommits.init();
    QuotaResponse.Aggregated availableTokens =
        quotaBackend.user(user).project(projectName).availableTokens(REPOSITORY_SIZE_GROUP);
    try {
      availableTokens.throwOnError();
    } catch (QuotaException e) {
      logger.atWarning().withCause(e).log(
          "Quota %s availableTokens request failed for project %s",
          REPOSITORY_SIZE_GROUP, projectName);
      throw new RuntimeException(e);
    }
    availableTokens.availableTokens().ifPresent(v -> receivePack.setMaxObjectSizeLimit(v));
  }

  /** Determine if the user can upload commits. */
  public Capable canUpload() throws IOException, PermissionBackendException {
    try {
      perm.check(ProjectPermission.PUSH_AT_LEAST_ONE_REF);
    } catch (AuthException e) {
      return new Capable("Upload denied for project '" + projectState.getName() + "'");
    }

    try {
      contributorAgreements.check(projectState.getNameKey(), user);
    } catch (AuthException e) {
      return new Capable(e.getMessage());
    }

    if (receiveConfig.checkMagicRefs) {
      return MagicBranch.checkMagicBranchRefs(repo, projectState.getProject());
    }
    return Capable.OK;
  }

  @Override
  public void onPreReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
    if (commands.stream().anyMatch(c -> c.getResult() != Result.NOT_ATTEMPTED)) {
      // Stop processing when command was already processed by previously invoked
      // pre-receive hooks
      return;
    }

    long startNanos = System.nanoTime();
    Worker w = new Worker(commands, Thread.currentThread().getName());
    try {
      w.progress.waitFor(
          executor.submit(scopePropagator.wrap(w)), timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (ExecutionException e) {
      metrics.timeouts.increment();
      logger.atWarning().withCause(e).log(
          "Error in ReceiveCommits while processing changes for project %s",
          projectState.getName());
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

    long deltaNanos = System.nanoTime() - startNanos;
    int totalChanges = 0;

    PushType pushType;
    if (resultChangeIds.isMagicPush()) {
      pushType = PushType.CREATE_REPLACE;
      List<Change.Id> created = resultChangeIds.get(ResultChangeIds.Key.CREATED);
      List<Change.Id> replaced = resultChangeIds.get(ResultChangeIds.Key.REPLACED);
      metrics.changes.record(pushType, created.size() + replaced.size());
      totalChanges = replaced.size() + created.size();
    } else {
      List<Change.Id> autoclosed = resultChangeIds.get(ResultChangeIds.Key.AUTOCLOSED);
      if (!autoclosed.isEmpty()) {
        pushType = PushType.AUTOCLOSE;
        metrics.changes.record(pushType, autoclosed.size());
        totalChanges = autoclosed.size();
      } else {
        pushType = PushType.NORMAL;
      }
    }

    if (totalChanges > 0) {
      metrics.latencyPerChange.record(pushType, deltaNanos / totalChanges, NANOSECONDS);
    }

    metrics.latencyPerPush.record(pushType, deltaNanos, NANOSECONDS);
  }

  /** Returns the Change.Ids that were processed in onPreReceive */
  @UsedAt(UsedAt.Project.GOOGLE)
  public ResultChangeIds getResultChangeIds() {
    return resultChangeIds;
  }

  public ReceivePack getReceivePack() {
    return receivePack;
  }
}
