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
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
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
import com.google.gerrit.server.change.MergeabilityChecksExecutor.Priority;
import com.google.gerrit.server.change.Mergeable.MergeableInfo;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.git.WorkQueue.Executor;
import com.google.gerrit.server.index.ChangeIndexer;
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
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

public class MergeabilityChecker implements GitReferenceUpdatedListener {
  private static final Logger log = LoggerFactory
      .getLogger(MergeabilityChecker.class);

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

  public class Check {
    private List<Change> changes;
    private List<Branch.NameKey> branches;
    private List<Project.NameKey> projects;
    private boolean force;
    private boolean reindex;
    private boolean interactive;

    private Check() {
      changes = Lists.newArrayListWithExpectedSize(1);
      branches = Lists.newArrayListWithExpectedSize(1);
      projects = Lists.newArrayListWithExpectedSize(1);
      interactive = true;
    }

    public Check addChange(Change change) {
      changes.add(change);
      return this;
    }

    public Check addBranch(Branch.NameKey branch) {
      branches.add(branch);
      interactive = false;
      return this;
    }

    public Check addProject(Project.NameKey project) {
      projects.add(project);
      interactive = false;
      return this;
    }

    /** Force reindexing regardless of whether mergeable flag was modified. */
    public Check reindex() {
      reindex = true;
      return this;
    }

    /** Force mergeability check even if change is not stale. */
    private Check force() {
      force = true;
      return this;
    }

    private ListeningExecutorService getExecutor() {
      return interactive ? interactiveExecutor : backgroundExecutor;
    }

    public CheckedFuture<?, IOException> runAsync() {
      final ListeningExecutorService executor = getExecutor();
      ListenableFuture<List<Change>> getChanges;
      if (branches.isEmpty() && projects.isEmpty()) {
        getChanges = Futures.immediateFuture(changes);
      } else {
        getChanges = executor.submit(
            new Callable<List<Change>>() {
              @Override
              public List<Change> call() throws OrmException {
                return getChanges();
              }
            });
      }

      return Futures.makeChecked(Futures.transform(getChanges,
          new AsyncFunction<List<Change>, List<Object>>() {
            @Override
            public ListenableFuture<List<Object>> apply(List<Change> changes) {
              List<ListenableFuture<?>> result =
                  Lists.newArrayListWithCapacity(changes.size());
              for (final Change c : changes) {
                ListenableFuture<Boolean> b =
                    executor.submit(new Task(c, force));
                if (reindex) {
                  result.add(Futures.transform(
                      b, new AsyncFunction<Boolean, Object>() {
                        @SuppressWarnings("unchecked")
                        @Override
                        public ListenableFuture<Object> apply(
                            Boolean indexUpdated) throws Exception {
                          if (!indexUpdated) {
                            return (ListenableFuture<Object>)
                                indexer.indexAsync(c.getId());
                          }
                          return Futures.immediateFuture(null);
                        }
                      }));
                } else {
                  result.add(b);
                }
              }
              return Futures.allAsList(result);
            }
          }), MAPPER);
    }

    public void run() throws IOException {
      try {
        runAsync().checkedGet();
      } catch (Exception e) {
        Throwables.propagateIfPossible(e, IOException.class);
        throw MAPPER.apply(e);
      }
    }

    private List<Change> getChanges() throws OrmException {
      ReviewDb db = schemaFactory.open();
      try {
        List<Change> results = Lists.newArrayList();
        results.addAll(changes);
        for (Project.NameKey p : projects) {
          Iterables.addAll(results, db.changes().byProjectOpenAll(p));
        }
        for (Branch.NameKey b : branches) {
          Iterables.addAll(results, db.changes().byBranchOpenAll(b));
        }
        return results;
      } catch (OrmException e) {
        log.error("Failed to fetch changes for mergeability check", e);
        throw e;
      } finally {
        db.close();
      }
    }
  }

  private final ThreadLocalRequestContext tl;
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final Provider<Mergeable> mergeable;
  private final ChangeIndexer indexer;
  private final ListeningExecutorService backgroundExecutor;
  private final ListeningExecutorService interactiveExecutor;
  private final MergeabilityCheckQueue mergeabilityCheckQueue;
  private final MetaDataUpdate.Server metaDataUpdateFactory;

  @Inject
  public MergeabilityChecker(ThreadLocalRequestContext tl,
      SchemaFactory<ReviewDb> schemaFactory,
      IdentifiedUser.GenericFactory identifiedUserFactory,
      ChangeControl.GenericFactory changeControlFactory,
      Provider<Mergeable> mergeable, ChangeIndexer indexer,
      @MergeabilityChecksExecutor(Priority.BACKGROUND)
        Executor backgroundExecutor,
      @MergeabilityChecksExecutor(Priority.INTERACTIVE)
        Executor interactiveExecutor,
      MergeabilityCheckQueue mergeabilityCheckQueue,
      MetaDataUpdate.Server metaDataUpdateFactory) {
    this.tl = tl;
    this.schemaFactory = schemaFactory;
    this.identifiedUserFactory = identifiedUserFactory;
    this.changeControlFactory = changeControlFactory;
    this.mergeable = mergeable;
    this.indexer = indexer;
    this.backgroundExecutor =
        MoreExecutors.listeningDecorator(backgroundExecutor);
    this.interactiveExecutor =
        MoreExecutors.listeningDecorator(interactiveExecutor);
    this.mergeabilityCheckQueue = mergeabilityCheckQueue;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
  }

  public Check newCheck() {
    return new Check();
  }

  @Override
  public void onGitReferenceUpdated(GitReferenceUpdatedListener.Event event) {
    String ref = event.getRefName();
    if (ref.startsWith(Constants.R_HEADS) || ref.equals(RefNames.REFS_CONFIG)) {
      Branch.NameKey branch = new Branch.NameKey(
          new Project.NameKey(event.getProjectName()), ref);
      newCheck().addBranch(branch).runAsync();
    }
    if (ref.equals(RefNames.REFS_CONFIG)) {
      Project.NameKey p = new Project.NameKey(event.getProjectName());
      try {
        ProjectConfig oldCfg = parseConfig(p, event.getOldObjectId());
        ProjectConfig newCfg = parseConfig(p, event.getNewObjectId());
        if (recheckMerges(oldCfg, newCfg)) {
          newCheck().addProject(p).force().runAsync();
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

  private class Task implements Callable<Boolean> {
    private final Change change;
    private final boolean force;

    private ReviewDb reviewDb;

    Task(Change change, boolean force) {
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
        if (ps == null) {
          // Cannot compute mergeability if current patch set is missing.
          return false;
        }

        Mergeable m = mergeable.get();
        m.setForce(force);

        ChangeControl control =
            changeControlFactory.controlFor(change, context.getCurrentUser());
        MergeableInfo info = m.apply(
            new RevisionResource(new ChangeResource(control), ps));
        return change.isMergeable() != info.mergeable;
      } catch (ResourceConflictException e) {
        // change is closed
        return false;
      } catch (Exception e) {
        log.error(String.format(
            "cannot update mergeability flag of change %d in project %s after update of %s",
            change.getId().get(),
            change.getDest().getParentKey(), change.getDest().get()), e);
        throw e;
      } finally {
        tl.setContext(old);
        if (reviewDb != null) {
          reviewDb.close();
          reviewDb = null;
        }
      }
    }
  }
}
