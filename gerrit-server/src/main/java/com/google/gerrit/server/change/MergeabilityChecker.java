// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.Mergeable.MergeableInfo;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.git.WorkQueue.Executor;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

public class MergeabilityChecker implements GitReferenceUpdatedListener {
  private static final Logger log = LoggerFactory
      .getLogger(MergeabilityChecker.class);

  private final ThreadLocalRequestContext tl;
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final Provider<Mergeable> mergeable;
  private final ChangeIndexer indexer;
  private final ListeningExecutorService executor;
  private final MergeabilityCheckQueue mergeabilityCheckQueue;
  private final MetaDataUpdate.Server metaDataUpdateFactory;

  @Inject
  public MergeabilityChecker(ThreadLocalRequestContext tl,
      SchemaFactory<ReviewDb> schemaFactory,
      IdentifiedUser.GenericFactory identifiedUserFactory,
      ChangeControl.GenericFactory changeControlFactory,
      Provider<Mergeable> mergeable, ChangeIndexer indexer,
      @MergeabilityChecksExecutor Executor executor,
      MergeabilityCheckQueue mergeabilityCheckQueue,
      MetaDataUpdate.Server metaDataUpdateFactory) {
    this.tl = tl;
    this.schemaFactory = schemaFactory;
    this.identifiedUserFactory = identifiedUserFactory;
    this.changeControlFactory = changeControlFactory;
    this.mergeable = mergeable;
    this.indexer = indexer;
    this.executor = MoreExecutors.listeningDecorator(executor);
    this.mergeabilityCheckQueue = mergeabilityCheckQueue;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
  }

  private static final Function<Exception, IOException> MAPPER =
      new Function<Exception, IOException>() {
    @Override
    public IOException apply(Exception in) {
      if (in instanceof IOException) {
        return (IOException) in;
      } else if (in instanceof ExecutionException
          && in.getCause() instanceof IOException) {
        return (IOException) in.getCause();
      } else {
        return new IOException(in);
      }
    }
  };

  @Override
  public void onGitReferenceUpdated(GitReferenceUpdatedListener.Event event) {
    String ref = event.getRefName();
    if (ref.startsWith(Constants.R_HEADS) || ref.equals(RefNames.REFS_CONFIG)) {
      executor.submit(new BranchUpdateTask(schemaFactory,
          new Project.NameKey(event.getProjectName()), ref));
    }
    if (ref.equals(RefNames.REFS_CONFIG)) {
      Project.NameKey p = new Project.NameKey(event.getProjectName());
      try {
        ProjectConfig oldCfg = parseConfig(p, event.getOldObjectId());
        ProjectConfig newCfg = parseConfig(p, event.getNewObjectId());
        if (recheckMerges(oldCfg, newCfg)) {
          try {
            new ProjectUpdateTask(schemaFactory, p, true).call();
          } catch (Exception e) {
            String msg = "Failed to update mergeability flags for project " + p.get()
                + " on update of " + RefNames.REFS_CONFIG;
            log.error(msg, e);
            Throwables.propagateIfPossible(e);
            throw new RuntimeException(msg, e);
          }
        }
      } catch (ConfigInvalidException | IOException e) {
        String msg = "Failed to update mergeability flags for project " + p.get()
            + " on update of " + RefNames.REFS_CONFIG;
        log.error(msg, e);
        throw new RuntimeException(msg, e);
      }
    }
  }

  private boolean recheckMerges(ProjectConfig oldCfg, ProjectConfig newCfg) {
    if (oldCfg == null || newCfg == null) {
      return true;
    }
    return !oldCfg.getProject().getSubmitType().equals(newCfg.getProject().getSubmitType())
        || oldCfg.getProject().getUseContentMerge() != newCfg.getProject().getUseContentMerge()
        || (oldCfg.getRulesId() == null
            ? newCfg.getRulesId() != null
            : !oldCfg.getRulesId().equals(newCfg.getRulesId()));
  }

  private ProjectConfig parseConfig(Project.NameKey p, String idStr)
      throws IOException, ConfigInvalidException, RepositoryNotFoundException {
    ObjectId id = ObjectId.fromString(idStr);
    if (ObjectId.zeroId().equals(id)) {
      return null;
    }
    return ProjectConfig.read(metaDataUpdateFactory.create(p), id);
  }

  /**
   * Updates the mergeability flag of the change asynchronously. If the
   * mergeability flag is updated the change is reindexed.
   *
   * @param change the change for which the mergeability flag should be updated
   * @return CheckedFuture that updates the mergeability flag of the change and
   *         returns {@code true} if the mergeability flag was updated and
   *         the change was reindexed, and {@code false} if the
   *         mergeability flag was not updated and the change was not reindexed
   */
  public CheckedFuture<Boolean, IOException> updateAsync(Change change) {
    return updateAsync(change, false);
  }

  private CheckedFuture<Boolean, IOException> updateAsync(Change change, boolean force) {
    return Futures.makeChecked(
        executor.submit(new ChangeUpdateTask(schemaFactory, change, force)),
        MAPPER);
  }

  /**
   * Updates the mergeability flag of the change asynchronously and reindexes
   * the change in any case.
   *
   * @param change the change for which the mergeability flag should be updated
   * @return CheckedFuture that updates the mergeability flag of the change and
   *         reindexes the change (whether the mergeability flag was updated or
   *         not)
   */
  public CheckedFuture<?, IOException> updateAndIndexAsync(Change change) {
    final Change.Id id = change.getId();
    return Futures.makeChecked(
        Futures.transform(updateAsync(change),
          new AsyncFunction<Boolean, Object>() {
            @SuppressWarnings("unchecked")
            @Override
            public ListenableFuture<Object> apply(Boolean indexUpdated)
                throws Exception {
              if (!indexUpdated) {
                return (ListenableFuture<Object>) indexer.indexAsync(id);
              }
              return Futures.immediateFuture(null);
            }
          }), MAPPER);
  }

  public boolean update(Change change) throws IOException {
    try {
      return new ChangeUpdateTask(schemaFactory, change).call();
    } catch (Exception e) {
      Throwables.propagateIfPossible(e);
      throw MAPPER.apply(e);
    }
  }

  public void update(Project.NameKey project) throws IOException {
    try {
      for (CheckedFuture<?, IOException> f : new ProjectUpdateTask(
          schemaFactory, project, false).call()) {
        f.checkedGet();
      }
    } catch (Exception e) {
      Throwables.propagateIfPossible(e);
      throw MAPPER.apply(e);
    }
  }

  private class ChangeUpdateTask implements Callable<Boolean> {
    private final SchemaFactory<ReviewDb> schemaFactory;
    private final Change change;
    private final boolean force;

    private ReviewDb reviewDb;

    ChangeUpdateTask(SchemaFactory<ReviewDb> schemaFactory, Change change) {
      this(schemaFactory, change, false);
    }

    ChangeUpdateTask(SchemaFactory<ReviewDb> schemaFactory, Change change,
        boolean force) {
      this.schemaFactory = schemaFactory;
      this.change = change;
      this.force = force;
    }

    @Override
    public Boolean call() throws Exception {
      mergeabilityCheckQueue.updatingMergeabilityFlag(change, force);

      RequestContext context = new RequestContext() {
        @Override
        public CurrentUser getCurrentUser() {
          return identifiedUserFactory.create(change.getOwner());
        }

        @Override
        public Provider<ReviewDb> getReviewDbProvider() {
          return new Provider<ReviewDb>() {
            @Override
            public ReviewDb get() {
              if (reviewDb == null) {
                try {
                  reviewDb = schemaFactory.open();
                } catch (OrmException e) {
                  throw new ProvisionException("Cannot open ReviewDb", e);
                }
              }
              return reviewDb;
            }
          };
        }
      };
      RequestContext old = tl.setContext(context);
      ReviewDb db = context.getReviewDbProvider().get();
      try {
        PatchSet ps = db.patchSets().get(change.currentPatchSetId());
        Mergeable m = mergeable.get();
        m.setForce(force);

        ChangeControl control =
            changeControlFactory.controlFor(change.getId(), context.getCurrentUser());
        MergeableInfo info = m.apply(
            new RevisionResource(new ChangeResource(control), ps));
        return change.isMergeable() != info.mergeable;
      } catch (ResourceConflictException e) {
        // change is closed
        return false;
      } finally {
        tl.setContext(old);
        if (reviewDb != null) {
          reviewDb.close();
          reviewDb = null;
        }
      }
    }
  }

  private abstract class UpdateTask implements
      Callable<List<CheckedFuture<Boolean, IOException>>> {
    private final SchemaFactory<ReviewDb> schemaFactory;
    private final boolean force;

    UpdateTask(SchemaFactory<ReviewDb> schemaFactory, boolean force) {
      this.schemaFactory = schemaFactory;
      this.force = force;
    }

    @Override
    public List<CheckedFuture<Boolean, IOException>> call() throws Exception {
      List<Change> openChanges;
      ReviewDb db = schemaFactory.open();
      try {
        openChanges = loadChanges(db);
      } finally {
        db.close();
      }

      List<CheckedFuture<Boolean, IOException>> futures =
          new ArrayList<>(openChanges.size());
      for (Change change : mergeabilityCheckQueue.addAll(openChanges, force)) {
        futures.add(updateAsync(change, force));
      }
      return futures;
    }

    protected abstract List<Change> loadChanges(ReviewDb db) throws OrmException;
  }

  private class BranchUpdateTask extends UpdateTask {
    private final Branch.NameKey branch;

    BranchUpdateTask(SchemaFactory<ReviewDb> schemaFactory,
        Project.NameKey project, String ref) {
      super(schemaFactory, false);
      this.branch = new Branch.NameKey(project, ref);
    }

    @Override
    protected List<Change> loadChanges(ReviewDb db) throws OrmException {
      return db.changes().byBranchOpenAll(branch).toList();
    }
  }

  private class ProjectUpdateTask extends UpdateTask {
    private final Project.NameKey project;

    ProjectUpdateTask(SchemaFactory<ReviewDb> schemaFactory,
        Project.NameKey project, boolean force) {
      super(schemaFactory, force);
      this.project = project;
    }

    @Override
    protected List<Change> loadChanges(ReviewDb db) throws OrmException {
      return db.changes().byProjectOpenAll(project).toList();
    }
  }
}
