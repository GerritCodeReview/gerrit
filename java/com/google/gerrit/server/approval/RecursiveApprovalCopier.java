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

package com.google.gerrit.server.approval;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.git.RefUpdateUtil;
import com.google.gerrit.server.FanOutExecutor;
import com.google.gerrit.server.InternalUser;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.internal.storage.file.RefDirectory;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;

public class RecursiveApprovalCopier {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final int SLICE_MAX_REFS = 1000;

  private final BatchUpdate.Factory batchUpdateFactory;
  private final GitRepositoryManager repositoryManager;
  private final InternalUser.Factory internalUserFactory;
  private final ApprovalsUtil approvalsUtil;
  private final ChangeNotes.Factory changeNotesFactory;
  private final GitReferenceUpdated gitRefUpdated;
  private final ListeningExecutorService executor;

  private final ConcurrentHashMap<Project.NameKey, List<ReceiveCommand>> pendingRefUpdates =
      new ConcurrentHashMap<>();

  private volatile boolean failedForAtLeastOneProject;

  private final AtomicInteger totalCopyApprovalsTasks = new AtomicInteger();
  private final AtomicInteger finishedCopyApprovalsTasks = new AtomicInteger();

  private final AtomicInteger totalRefUpdates = new AtomicInteger();
  private final AtomicInteger finishedRefUpdates = new AtomicInteger();

  @Inject
  public RecursiveApprovalCopier(
      BatchUpdate.Factory batchUpdateFactory,
      GitRepositoryManager repositoryManager,
      InternalUser.Factory internalUserFactory,
      ApprovalsUtil approvalsUtil,
      ChangeNotes.Factory changeNotesFactory,
      GitReferenceUpdated gitRefUpdated,
      @FanOutExecutor ExecutorService executor) {
    this.batchUpdateFactory = batchUpdateFactory;
    this.repositoryManager = repositoryManager;
    this.internalUserFactory = internalUserFactory;
    this.approvalsUtil = approvalsUtil;
    this.changeNotesFactory = changeNotesFactory;
    this.gitRefUpdated = gitRefUpdated;
    this.executor = MoreExecutors.listeningDecorator(executor);
  }

  /**
   * This method assumes it is used as a standalone program having exclusive access to the Git
   * repositories. Therefore, it will (safely) skip locking of the loose refs when performing batch
   * ref-updates.
   */
  public void persistStandalone()
      throws RepositoryNotFoundException, IOException, InterruptedException, ExecutionException {
    persist(repositoryManager.list(), null, false);

    if (failedForAtLeastOneProject) {
      throw new RuntimeException("There were errors, check the logs for details");
    }
  }

  public void persist(Project.NameKey project, @Nullable Consumer<Change> labelsCopiedListener)
      throws IOException, RepositoryNotFoundException, InterruptedException, ExecutionException {
    persist(ImmutableList.of(project), labelsCopiedListener, true);
  }

  private void persist(
      Collection<Project.NameKey> projects,
      @Nullable Consumer<Change> labelsCopiedListener,
      boolean shouldLockLooseRefs)
      throws InterruptedException, ExecutionException, RepositoryNotFoundException, IOException {
    List<ListenableFuture<Void>> copyApprovalsTasks = new LinkedList<>();
    for (Project.NameKey project : projects) {
      copyApprovalsTasks.addAll(submitCopyApprovalsTasks(project, labelsCopiedListener));
    }
    Futures.successfulAsList(copyApprovalsTasks).get();

    List<ListenableFuture<Void>> batchRefUpdateTasks =
        submitBatchRefUpdateTasks(shouldLockLooseRefs);
    Futures.successfulAsList(batchRefUpdateTasks).get();
  }

  private List<ListenableFuture<Void>> submitCopyApprovalsTasks(
      Project.NameKey project, @Nullable Consumer<Change> labelsCopiedListener)
      throws RepositoryNotFoundException, IOException {
    List<ListenableFuture<Void>> futures = new LinkedList<>();
    try (Repository repository = repositoryManager.openRepository(project)) {
      ImmutableList<Ref> allMetaRefs =
          repository.getRefDatabase().getRefsByPrefix(RefNames.REFS_CHANGES).stream()
              .filter(r -> r.getName().endsWith(RefNames.META_SUFFIX))
              .collect(toImmutableList());

      totalCopyApprovalsTasks.addAndGet(allMetaRefs.size());

      for (List<Ref> slice : Lists.partition(allMetaRefs, SLICE_MAX_REFS)) {
        futures.add(
            executor.submit(
                () -> {
                  copyApprovalsForSlice(project, slice, labelsCopiedListener);
                  return null;
                }));
      }
    }
    return futures;
  }

  private void copyApprovalsForSlice(
      Project.NameKey project, List<Ref> slice, @Nullable Consumer<Change> labelsCopiedListener)
      throws Exception {
    try {
      copyApprovalsForSlice(project, slice, labelsCopiedListener, false);
    } catch (Exception e) {
      failedForAtLeastOneProject = true;
      logger.atSevere().withCause(e).log(
          "Error in a slice of project %s, will retry and skip corrupt meta-refs", project);
      copyApprovalsForSlice(project, slice, labelsCopiedListener, true);
    }
    logProgress();
  }

  private void copyApprovalsForSlice(
      Project.NameKey project,
      List<Ref> slice,
      @Nullable Consumer<Change> labelsCopiedListener,
      boolean checkForCorruptMetaRefs)
      throws Exception {
    logger.atInfo().log("copy-approvals for a slice of %s project", project);
    try (BatchUpdate bu =
        batchUpdateFactory.create(project, internalUserFactory.create(), TimeUtil.nowTs())) {
      for (Ref metaRef : slice) {
        Change.Id changeId = Change.Id.fromRef(metaRef.getName());
        if (checkForCorruptMetaRefs && isCorrupt(project, changeId)) {
          logger.atSevere().log("skipping corrupt meta-ref %s", metaRef.getName());
          continue;
        }
        bu.addOp(
            changeId,
            new RecursiveApprovalCopier.PersistCopiedVotesOp(approvalsUtil, labelsCopiedListener));
      }

      BatchRefUpdate bru = bu.prepareRefUpdates();
      if (bru != null) {
        List<ReceiveCommand> cmds = bru.getCommands();
        pendingRefUpdates.compute(
            project,
            (p, u) -> {
              if (u == null) {
                return new LinkedList<>(cmds);
              }
              u.addAll(cmds);
              return u;
            });
        totalRefUpdates.addAndGet(cmds.size());
      }

      finishedCopyApprovalsTasks.addAndGet(slice.size());
    }
  }

  private List<ListenableFuture<Void>> submitBatchRefUpdateTasks(boolean shouldLockLooseRefs) {
    logger.atInfo().log("submitting batch ref-update tasks");
    List<ListenableFuture<Void>> futures = new LinkedList<>();
    for (Map.Entry<Project.NameKey, List<ReceiveCommand>> e : pendingRefUpdates.entrySet()) {
      Project.NameKey project = e.getKey();
      List<ReceiveCommand> updates = e.getValue();
      futures.add(
          executor.submit(
              () -> {
                executeRefUpdates(project, updates, shouldLockLooseRefs);
                return null;
              }));
    }
    return futures;
  }

  private void executeRefUpdates(
      Project.NameKey project, List<ReceiveCommand> updates, boolean shouldLockLooseRefs)
      throws RepositoryNotFoundException, IOException {
    logger.atInfo().log(
        "executing batch ref-update for project %s, size %d", project, updates.size());
    try (Repository repository = repositoryManager.openRepository(project)) {
      RefDatabase refdb = repository.getRefDatabase();
      BatchRefUpdate bu;
      if (refdb instanceof RefDirectory) {
        bu = ((RefDirectory) refdb).newBatchUpdate(shouldLockLooseRefs);
      } else {
        bu = refdb.newBatchUpdate();
      }
      bu.addCommand(updates);
      RefUpdateUtil.executeChecked(bu, repository);
      gitRefUpdated.fire(project, bu, null);

      finishedRefUpdates.addAndGet(updates.size());
      logProgress();
    }
  }

  private boolean isCorrupt(Project.NameKey project, Change.Id changeId) {
    Change c = ChangeNotes.Factory.newChange(project, changeId);
    try {
      changeNotesFactory.createForBatchUpdate(c, true);
      return false;
    } catch (Exception e) {
      logger.atSevere().withCause(e).log(e.getMessage());
      return true;
    }
  }

  public void persist(Change change) throws UpdateException, RestApiException {
    Project.NameKey project = change.getProject();
    try (BatchUpdate bu =
        batchUpdateFactory.create(project, internalUserFactory.create(), TimeUtil.nowTs())) {
      Change.Id changeId = change.getId();
      bu.addOp(changeId, new PersistCopiedVotesOp(approvalsUtil, null));
      bu.execute();
    }
  }

  private void logProgress() {
    logger.atInfo().log(
        "copy-approvals tasks done: %d/%d, ref-update tasks done: %d/%d",
        finishedCopyApprovalsTasks.get(),
        totalCopyApprovalsTasks.get(),
        finishedRefUpdates.get(),
        totalRefUpdates.get());
  }

  private static class PersistCopiedVotesOp implements BatchUpdateOp {
    private final ApprovalsUtil approvalsUtil;
    private final Consumer<Change> listener;

    PersistCopiedVotesOp(
        ApprovalsUtil approvalsUtil, @Nullable Consumer<Change> labelsCopiedListener) {
      this.approvalsUtil = approvalsUtil;
      this.listener = labelsCopiedListener;
    }

    @Override
    public boolean updateChange(ChangeContext ctx) throws IOException {
      Change change = ctx.getChange();
      ChangeUpdate update = ctx.getUpdate(change.currentPatchSetId(), change.getLastUpdatedOn());
      approvalsUtil.persistCopiedApprovals(
          ctx.getNotes(),
          ctx.getNotes().getCurrentPatchSet(),
          ctx.getRevWalk(),
          ctx.getRepoView().getConfig(),
          update);

      boolean labelsCopied = update.hasCopiedApprovals();

      if (labelsCopied && listener != null) {
        listener.accept(change);
      }

      return labelsCopied;
    }
  }
}
