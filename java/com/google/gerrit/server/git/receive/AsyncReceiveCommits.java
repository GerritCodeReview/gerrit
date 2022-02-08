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

import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.quota.QuotaGroupDefinitions.REPOSITORY_SIZE_GROUP;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.common.data.Capable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.AuthException;
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
import com.google.gerrit.server.git.MultiProgressMonitor.TaskKind;
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
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
public class AsyncReceiveCommits {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String RECEIVE_OVERALL_TIMEOUT_NAME = "ReceiveCommitsOverallTimeout";
  private static final String RECEIVE_CANCELLATION_TIMEOUT_NAME =
      "ReceiveCommitsCancellationTimeout";

  public interface Factory {
    AsyncReceiveCommits create(
        ProjectState projectState,
        IdentifiedUser user,
        Repository repository,
        @Nullable MessageSender messageSender);
  }

  public static class AsyncReceiveCommitsModule extends PrivateModule {
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
    @Named(RECEIVE_OVERALL_TIMEOUT_NAME)
    long getReceiveTimeoutMillis(@GerritServerConfig Config cfg) {
      return ConfigUtil.getTimeUnit(
          cfg, "receive", null, "timeout", TimeUnit.MINUTES.toMillis(4), TimeUnit.MILLISECONDS);
    }

    @Provides
    @Singleton
    @Named(RECEIVE_CANCELLATION_TIMEOUT_NAME)
    long getCancellationTimeoutMillis(@GerritServerConfig Config cfg) {
      return ConfigUtil.getTimeUnit(
          cfg,
          "receive",
          null,
          "cancellationTimeout",
          TimeUnit.SECONDS.toMillis(5),
          TimeUnit.MILLISECONDS);
    }
  }

  private static MultiProgressMonitor newMultiProgressMonitor(
      MultiProgressMonitor.Factory multiProgressMonitorFactory, MessageSender messageSender) {
    return multiProgressMonitorFactory.create(
        new OutputStream() {
          @Override
          public void write(int b) {
            messageSender.sendBytes(new byte[] {(byte) b});
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
        },
        TaskKind.RECEIVE_COMMITS,
        "Processing changes");
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

  private final MultiProgressMonitor.Factory multiProgressMonitorFactory;
  private final Metrics metrics;
  private final ReceiveCommits receiveCommits;
  private final PermissionBackend.ForProject perm;
  private final ReceivePack receivePack;
  private final ExecutorService executor;
  private final RequestScopePropagator scopePropagator;
  private final ReceiveConfig receiveConfig;
  private final ContributorAgreementsChecker contributorAgreements;
  private final long receiveTimeoutMillis;
  private final long cancellationTimeoutMillis;
  private final ProjectState projectState;
  private final IdentifiedUser user;
  private final Repository repo;
  private final AllRefsWatcher allRefsWatcher;

  @Inject
  AsyncReceiveCommits(
      MultiProgressMonitor.Factory multiProgressMonitorFactory,
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
      @Named(RECEIVE_OVERALL_TIMEOUT_NAME) long receiveTimeoutMillis,
      @Named(RECEIVE_CANCELLATION_TIMEOUT_NAME) long cancellationTimeoutMillis,
      @Assisted ProjectState projectState,
      @Assisted IdentifiedUser user,
      @Assisted Repository repo,
      @Assisted @Nullable MessageSender messageSender)
      throws PermissionBackendException {
    this.multiProgressMonitorFactory = multiProgressMonitorFactory;
    this.executor = executor;
    this.scopePropagator = scopePropagator;
    this.receiveConfig = receiveConfig;
    this.contributorAgreements = contributorAgreements;
    this.receiveTimeoutMillis = receiveTimeoutMillis;
    this.cancellationTimeoutMillis = cancellationTimeoutMillis;
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
    receivePack.setPreReceiveHook(asHook());
    receivePack.setPostReceiveHook(lazyPostReceive.create(user, projectName));

    if (!projectState.statePermitsRead() || !this.perm.test(ProjectPermission.READ)) {
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
    receiveCommits =
        factory.create(projectState, user, receivePack, repo, allRefsWatcher, messageSender);
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
    availableTokens.availableTokens().ifPresent(receivePack::setMaxPackSizeLimit);
  }

  /** Determine if the user can upload commits. */
  public Capable canUpload() throws IOException, PermissionBackendException {
    if (!perm.test(ProjectPermission.PUSH_AT_LEAST_ONE_REF)) {
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

  /**
   * Returns a {@link PreReceiveHook} implementation that can be used directly by JGit when
   * processing a push.
   */
  public PreReceiveHook asHook() {
    return (rp, commands) -> {
      checkState(receivePack == rp, "can't perform PreReceive for a different receive pack");
      long startNanos = System.nanoTime();
      ReceiveCommitsResult result;
      try {
        result = preReceive(commands);
      } catch (TimeoutException e) {
        receivePack.sendError("timeout while processing changes");
        rejectCommandsNotAttempted(commands);
        return;
      } catch (Exception e) {
        logger.atSevere().withCause(e.getCause()).log("error while processing push");
        receivePack.sendError("internal error");
        rejectCommandsNotAttempted(commands);
        return;
      } finally {
        // Flush the messages queued up until now (if any).
        receiveCommits.sendMessages();
      }
      reportMetrics(result, System.nanoTime() - startNanos);
    };
  }

  /** Processes {@code commands}, applies them to Git storage and communicates back on the wire. */
  @UsedAt(UsedAt.Project.GOOGLE)
  public ReceiveCommitsResult preReceive(Collection<ReceiveCommand> commands)
      throws TimeoutException, UncheckedExecutionException {
    if (commands.stream().anyMatch(c -> c.getResult() != Result.NOT_ATTEMPTED)) {
      // Stop processing when command was already processed by previously invoked
      // pre-receive hooks
      return ReceiveCommitsResult.empty();
    }
    String currentThreadName = Thread.currentThread().getName();
    MultiProgressMonitor monitor =
        newMultiProgressMonitor(multiProgressMonitorFactory, receiveCommits.getMessageSender());
    Callable<ReceiveCommitsResult> callable =
        () -> {
          String oldName = Thread.currentThread().getName();
          Thread.currentThread().setName(oldName + "-for-" + currentThreadName);
          try {
            return receiveCommits.processCommands(commands, monitor);
          } finally {
            Thread.currentThread().setName(oldName);
          }
        };

    try {
      // WorkQueue does not support Callable<T>, so we have to covert it here.
      FutureTask<ReceiveCommitsResult> runnable =
          ProjectRunnable.fromCallable(
              callable, receiveCommits.getProject().getNameKey(), "receive-commits", null, false);
      monitor.waitFor(
          executor.submit(scopePropagator.wrap(runnable)),
          receiveTimeoutMillis,
          TimeUnit.MILLISECONDS,
          cancellationTimeoutMillis,
          TimeUnit.MILLISECONDS);
      if (!runnable.isDone()) {
        // At this point we are either done or have thrown a TimeoutException and bailed out.
        throw new IllegalStateException("unable to get receive commits result");
      }
      return runnable.get();
    } catch (TimeoutException e) {
      metrics.timeouts.increment();
      logger.atWarning().withCause(e).log(
          "Timeout in ReceiveCommits while processing changes for project %s",
          projectState.getName());
      throw e;
    } catch (InterruptedException | ExecutionException e) {
      throw new UncheckedExecutionException(e);
    }
  }

  @UsedAt(UsedAt.Project.GOOGLE)
  public void reportMetrics(ReceiveCommitsResult result, long deltaNanos) {
    PushType pushType;
    int totalChanges = 0;
    if (result.magicPush()) {
      pushType = PushType.CREATE_REPLACE;
      Set<Change.Id> created = result.changes().get(ReceiveCommitsResult.ChangeStatus.CREATED);
      Set<Change.Id> replaced = result.changes().get(ReceiveCommitsResult.ChangeStatus.REPLACED);
      metrics.changes.record(pushType, created.size() + replaced.size());
      totalChanges = replaced.size() + created.size();
    } else {
      Set<Change.Id> autoclosed =
          result.changes().get(ReceiveCommitsResult.ChangeStatus.AUTOCLOSED);
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

  /**
   * Sends all messages which have been collected while processing the push to the client.
   *
   * @see ReceiveCommits#sendMessages()
   */
  @UsedAt(UsedAt.Project.GOOGLE)
  public void sendMessages() {
    receiveCommits.sendMessages();
  }

  public ReceivePack getReceivePack() {
    return receivePack;
  }

  /**
   * Marks all commands that were not processed yet as {@link Result#REJECTED_OTHER_REASON}.
   * Intended to be used to finish up remaining commands when errors occur during processing.
   */
  private static void rejectCommandsNotAttempted(Collection<ReceiveCommand> commands) {
    for (ReceiveCommand c : commands) {
      if (c.getResult() == Result.NOT_ATTEMPTED) {
        c.setResult(Result.REJECTED_OTHER_REASON, "internal error");
      }
    }
  }
}
