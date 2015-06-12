// Copyright (C) 2008 The Android Open Source Project
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

import static org.eclipse.jgit.lib.RefDatabase.ALL;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.common.data.SubmitTypeRecord;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.RemotePeer;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.change.Submit;
import com.google.gerrit.server.config.GerritRequestModule;
import com.google.gerrit.server.config.RequestScopedReviewDbProvider;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.strategy.SubmitStrategy;
import com.google.gerrit.server.git.strategy.SubmitStrategyFactory;
import com.google.gerrit.server.git.validators.MergeValidationException;
import com.google.gerrit.server.git.validators.MergeValidators;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.mail.MergedSender;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.servlet.RequestScoped;

import com.jcraft.jsch.HostKey;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Merges changes in submission order into a single branch.
 * <p>
 * Branches are reduced to the minimum number of heads needed to merge
 * everything. This allows commits to be entered into the queue in any order
 * (such as ancestors before descendants) and only the most recent commit on any
 * line of development will be merged. All unmerged commits along a line of
 * development must be in the submission queue in order to merge the tip of that
 * line.
 * <p>
 * Conflicts are handled by discarding the entire line of development and
 * marking it as conflicting, even if an earlier commit along that same line can
 * be merged cleanly.
 */
public class MergeOp {
  public interface Factory {
    MergeOp create(ChangeSet changes);
  }

  private static final Logger log = LoggerFactory.getLogger(MergeOp.class);

  private final AccountCache accountCache;
  private final ApprovalsUtil approvalsUtil;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final ChangeData.Factory changeDataFactory;
  private final ChangeHooks hooks;
  private final ChangeIndexer indexer;
  private final ChangeMessagesUtil cmUtil;
  private final ChangeNotes.Factory notesFactory;
  private final ChangeUpdate.Factory updateFactory;
  private final GitReferenceUpdated gitRefUpdated;
  private final GitRepositoryManager repoManager;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;
  private final MergedSender.Factory mergedSenderFactory;
  private final MergeValidators.Factory mergeValidatorsFactory;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final ProjectCache projectCache;
  private final Provider<InternalChangeQuery> queryProvider;
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final Submit submit;
  private final SubmitStrategyFactory submitStrategyFactory;
  private final SubmoduleOp.Factory subOpFactory;
  private final TagCache tagCache;
  private final WorkQueue workQueue;

  private final Map<Change.Id, CodeReviewCommit> commits;
  private final List<Change> toUpdate;
  private final PerThreadRequestScope.Scoper threadScoper;
  private final ChangeSet changes;

  private ProjectState destProject;
  private ReviewDb db;
  private Repository repo;
  private RevWalk rw;
  private RevFlag canMergeFlag;
  private CodeReviewCommit branchTip;
  private MergeTip mergeTip;
  private ObjectInserter inserter;
  private PersonIdent refLogIdent;


  @Inject
  MergeOp(AccountCache accountCache,
      ApprovalsUtil approvalsUtil,
      ChangeControl.GenericFactory changeControlFactory,
      ChangeData.Factory changeDataFactory,
      ChangeHooks hooks,
      ChangeIndexer indexer,
      Injector injector,
      ChangeMessagesUtil cmUtil,
      ChangeNotes.Factory notesFactory,
      ChangeUpdate.Factory updateFactory,
      GitReferenceUpdated gitRefUpdated,
      GitRepositoryManager repoManager,
      IdentifiedUser.GenericFactory identifiedUserFactory,
      MergedSender.Factory mergedSenderFactory,
      MergeValidators.Factory mergeValidatorsFactory,
      PatchSetInfoFactory patchSetInfoFactory,
      ProjectCache projectCache,
      Provider<InternalChangeQuery> queryProvider,
      SchemaFactory<ReviewDb> schemaFactory,
      Submit submit,
      SubmitStrategyFactory submitStrategyFactory,
      SubmoduleOp.Factory subOpFactory,
      TagCache tagCache,
      WorkQueue workQueue,
      @Assisted ChangeSet changes) {
    this.accountCache = accountCache;
    this.approvalsUtil = approvalsUtil;
    this.changeControlFactory = changeControlFactory;
    this.changeDataFactory = changeDataFactory;
    this.hooks = hooks;
    this.indexer = indexer;
    this.cmUtil = cmUtil;
    this.notesFactory = notesFactory;
    this.updateFactory = updateFactory;
    this.gitRefUpdated = gitRefUpdated;
    this.repoManager = repoManager;
    this.identifiedUserFactory = identifiedUserFactory;
    this.mergedSenderFactory = mergedSenderFactory;
    this.mergeValidatorsFactory = mergeValidatorsFactory;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.projectCache = projectCache;
    this.queryProvider = queryProvider;
    this.schemaFactory = schemaFactory;
    this.submit = submit;
    this.submitStrategyFactory = submitStrategyFactory;
    this.subOpFactory = subOpFactory;
    this.tagCache = tagCache;
    this.workQueue = workQueue;
    this.changes = changes;
    commits = new HashMap<>();
    toUpdate = Lists.newArrayList();

    Injector child = injector.createChildInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bindScope(RequestScoped.class, PerThreadRequestScope.REQUEST);
        bind(RequestScopePropagator.class)
            .to(PerThreadRequestScope.Propagator.class);
        bind(PerThreadRequestScope.Propagator.class);
        install(new GerritRequestModule());

        bind(SocketAddress.class).annotatedWith(RemotePeer.class).toProvider(
            new Provider<SocketAddress>() {
              @Override
              public SocketAddress get() {
                throw new OutOfScopeException("No remote peer on merge thread");
              }
            });
        bind(SshInfo.class).toInstance(new SshInfo() {
          @Override
          public List<HostKey> getHostKeys() {
            return Collections.emptyList();
          }
        });
      }

      @Provides
      public PerThreadRequestScope.Scoper provideScoper(
          final PerThreadRequestScope.Propagator propagator,
          final Provider<RequestScopedReviewDbProvider> dbProvider) {
        final RequestContext requestContext = new RequestContext() {
          @Override
          public CurrentUser getCurrentUser() {
            throw new OutOfScopeException("No user on merge thread");
          }

          @Override
          public Provider<ReviewDb> getReviewDbProvider() {
            return dbProvider.get();
          }
        };
        return new PerThreadRequestScope.Scoper() {
          @Override
          public <T> Callable<T> scope(Callable<T> callable) {
            return propagator.scope(requestContext, callable);
          }
        };
      }
    });
    threadScoper = child.getInstance(PerThreadRequestScope.Scoper.class);
  }

  private void setDestProject(Branch.NameKey destBranch) throws MergeException {
    destProject = projectCache.get(destBranch.getParentKey());
    if (destProject == null) {
      throw new MergeException("No such project: " + destBranch.getParentKey());
    }
  }

  private void openSchema() throws OrmException {
    if (db == null) {
      db = schemaFactory.open();
    }
  }

  /**
   *
   * @return a ChangeSet including all the parents which are not yet merged.
   * @throws IOException
   * @throws IncorrectObjectTypeException
   * @throws MissingObjectException
   * @throws OrmException
   * @throws MergeException
   * @throws NoSuchProjectException
   */
  private ChangeSet getCompleteListOfChanges(ChangeSet cs) throws
      MissingObjectException, IncorrectObjectTypeException, IOException,
      OrmException, MergeException, NoSuchProjectException {

    List<Change> ret = Lists.newArrayList();
    for (Change.Id cId : cs.ids()) {
      try {
        ChangeData cd = changeDataFactory.create(db, cId);
        openRepository(cd.change());
        if (getSubmitType(cd.changeControl(), cd.currentPatchSet())
            == SubmitType.CHERRY_PICK) {
          ret.add(cd.change());
          continue;
        }

        // Get the underlying git commit object
        PatchSet ps = cd.currentPatchSet();
        String objIdStr = ps.getRevision().get();
        ObjectId objId = ObjectId.fromString(objIdStr);
        RevCommit commit = rw.parseCommit(objId);

        // Get the common ancestor with target branch
        Branch.NameKey destBranch = cd.change().getDest();
        repo.getRefDatabase().refresh();
        Ref ref = repo.getRefDatabase().getRef(destBranch.get());
        if (ref == null) {
          ret.add(cd.change());
          // A new empty branch doesn't have additional changes
          continue;
        }

        rw.markStart(commit);
        objId = ref.getObjectId();
        RevCommit head = rw.parseCommit(objId);
        rw.markUninteresting(head);

        for (RevCommit c : rw) {
          List<ChangeData> destChanges = queryProvider.get()
            .byCommit(c);

          if (destChanges.size() == 0) {
            throw new MergeException("Unable to find dependency " + c.getId());
          } else if (destChanges.size() == 1) {
            Change chg = destChanges.get(0).change();
            if (chg.getStatus() == Change.Status.NEW
                || chg.getStatus() == Change.Status.SUBMITTED) {
              ret.add(chg);
            }
          } else if (destChanges.size() > 1) {
            throw new MergeException("More than one commit having the same SHA1? " + c.getId());
          }
        }
      } finally {
        if (rw != null) {
          rw.close();
        }
        if (repo != null) {
          repo.close();
        }
      }
    }

    return ChangeSet.create(ret, cs.user());
  }

  private static Optional<SubmitRecord> findOkRecord(Collection<SubmitRecord> in) {
    return Iterables.tryFind(in, new Predicate<SubmitRecord>() {
      @Override
      public boolean apply(SubmitRecord input) {
        return input.status == SubmitRecord.Status.OK;
      }
    });
  }

  private List<SubmitRecord> checkSubmitRule(ChangeData cd)
      throws ResourceConflictException, OrmException {
    PatchSet patchSet = cd.currentPatchSet();
    List<SubmitRecord> results = new SubmitRuleEvaluator(cd)
    .setPatchSet(patchSet)
    .evaluate();
    Optional<SubmitRecord> ok = findOkRecord(results);
    if (ok.isPresent()) {
      // Rules supplied a valid solution.
      return ImmutableList.of(ok.get());
    } else if (results.isEmpty()) {
      throw new IllegalStateException(String.format(
          "SubmitRuleEvaluator.evaluate returned empty list for %s in %s",
          patchSet.getId(),
          cd.change().getProject().get()));
    }

    for (SubmitRecord record : results) {
      switch (record.status) {
        case CLOSED:
          throw new ResourceConflictException("change is closed");

        case RULE_ERROR:
          throw new ResourceConflictException(String.format(
              "rule error: %s",
              record.errorMessage));

        case NOT_READY:
          StringBuilder msg = new StringBuilder();
          for (SubmitRecord.Label lbl : record.labels) {
            switch (lbl.status) {
              case OK:
              case MAY:
                continue;

              case REJECT:
                if (msg.length() > 0) {
                  msg.append("; ");
                }
                msg.append("blocked by ").append(lbl.label);
                continue;

              case NEED:
                if (msg.length() > 0) {
                  msg.append("; ");
                }
                msg.append("needs ").append(lbl.label);
                continue;

              case IMPOSSIBLE:
                if (msg.length() > 0) {
                  msg.append("; ");
                }
                msg.append("needs ").append(lbl.label)
                .append(" (check project access)");
                continue;

              default:
                throw new IllegalStateException(String.format(
                    "Unsupported SubmitRecord.Label %s for %s in %s",
                    lbl.toString(),
                    patchSet.getId(),
                    cd.change().getProject().get()));
            }
          }
          throw new ResourceConflictException(msg.toString());

        default:
          throw new IllegalStateException(String.format(
              "Unsupported SubmitRecord %s for %s in %s",
              record,
              patchSet.getId().getId(),
              cd.change().getProject().get()));
      }
    }
    throw new IllegalStateException();
  }

  private void checkPermissions(ChangeSet cs)
      throws ResourceConflictException, OrmException {
    for (Change.Id id : cs.ids()) {
      ChangeData cd = changeDataFactory.create(db, id);
      checkSubmitRule(cd);
    }
  }

  // For historic reasons we will first go into the submitted state
  // TODO(sbeller): remove this when we get rid of Change.Status.SUBMITTED
  private void submitDependants(ChangeSet cs)
      throws OrmException, ResourceConflictException, IOException {
    IdentifiedUser caller = cs.user();
    for (Change.Id id : cs.ids()) {
      ChangeData cd = changeDataFactory.create(db, id);
      switch (cd.change().getStatus()) {
        case ABANDONED:
          throw new ResourceConflictException("Change " + cd.getId() +
              " was abandoned while processing this change set");
        case DRAFT:
          throw new ResourceConflictException("Cannot submit draft " + cd.getId());
        case NEW:
          RevisionResource rsrc =
          new RevisionResource(new ChangeResource(cd.changeControl(), null),
              cd.currentPatchSet());
          logDebug("Submitting change id {}", cd.change().getId());
          submit.submitThisChange(rsrc, caller, false);
          break;
        case MERGED:
          // we're racing here, but having it already merged is fine.
        case SUBMITTED:
          // ok
      }
    }
  }

  public void merge(boolean checkPermissions) throws NoSuchChangeException,
  OrmException, ResourceConflictException {
    ChangeSet cs;
    logDebug("Beginning merge of {}", changes);
    try {
      openSchema();
      cs = getCompleteListOfChanges(changes);
      logDebug("Calculated to merge {}", cs);
      submitDependants(cs);
      if (checkPermissions) {
        logDebug("Checking permission for {}", cs);
        checkPermissions(cs);
      }
      try {
        integrateIntoHistory(cs);
      } catch (MergeException e) {
        throw new ResourceConflictException("Merge Conflict", e);
      }
    } catch (IOException | MergeException | NoSuchProjectException e) {
      // Anything before the merge attempt is an error
      throw new OrmException(e);
    } finally {
      if (db != null) {
        db.close();
      }
    }
  }

  private void integrateIntoHistory(ChangeSet cs)
      throws MergeException, NoSuchChangeException, ResourceConflictException {
    logDebug("Beginning merge attempt on {}", changes);
    try {
      openSchema();
      for (Change.Id cId : cs.ids()) {
        ChangeData cd = changeDataFactory.create(db, cId);
        Change c = cd.change();
        openRepository(c);

        Branch.NameKey destBranch = c.getDest();
        setDestProject(destBranch);

        RefUpdate branchUpdate = openBranch(destBranch);
        boolean reopen = false;

        ListMultimap<SubmitType, Change> toSubmit =
            validateChangeList(queryProvider.get().submitted(destBranch));

        Set<SubmitType> submitTypes = new HashSet<>(toSubmit.keySet());
        for (SubmitType submitType : submitTypes) {
          if (reopen) {
            logDebug("Reopening branch");
            branchUpdate = openBranch(destBranch);
          }
          SubmitStrategy strategy = createStrategy(destBranch, submitType);
          MergeTip mergeTip = preMerge(strategy, toSubmit.get(submitType));
          RefUpdate update = updateBranch(destBranch, branchUpdate);
          reopen = true;

          updateChangeStatus(toSubmit.get(submitType), mergeTip);
          updateSubscriptions(destBranch, toSubmit.get(submitType));
          if (update != null) {
            fireRefUpdated(destBranch, update);
          }
        }

        updateChangeStatus(toUpdate, mergeTip);
      }
    } catch (NoSuchProjectException noProject) {
      logWarn("Project " + noProject.project() + " no longer exists,"
          + " abandoning open changes");
      abandonAllOpenChanges(noProject.project());
    } catch (OrmException e) {
      throw new MergeException("Cannot query the database", e);
    } finally {
      if (inserter != null) {
        inserter.close();
      }
      if (rw != null) {
        rw.close();
      }
      if (repo != null) {
        repo.close();
      }
    }
  }

  private MergeTip preMerge(SubmitStrategy strategy,
      List<Change> submitted) throws MergeException {
    logDebug("Running submit strategy {} for {} commits",
        strategy.getClass().getSimpleName(), submitted.size());

    List<CodeReviewCommit> toMerge = new ArrayList<>(submitted.size());
    for (Change c : submitted) {
      toMerge.add(commits.get(c.getId()));
    }

    mergeTip = strategy.run(branchTip, toMerge);
    refLogIdent = strategy.getRefLogIdent();
    logDebug("Produced {} new commits", strategy.getNewCommits().size());
    commits.putAll(strategy.getNewCommits());
    return mergeTip;
  }

  private SubmitStrategy createStrategy(Branch.NameKey destBranch,
      SubmitType submitType) throws MergeException, NoSuchProjectException {
    return submitStrategyFactory.create(submitType, db, repo, rw, inserter,
        canMergeFlag, getAlreadyAccepted(branchTip), destBranch);
  }

  private void openRepository(Change c) throws MergeException, NoSuchProjectException {
    Project.NameKey name = c.getDest().getParentKey();
    try {
      repo = repoManager.openRepository(name);
    } catch (RepositoryNotFoundException notFound) {
      throw new NoSuchProjectException(name, notFound);
    } catch (IOException err) {
      String m = "Error opening repository \"" + name.get() + '"';
      throw new MergeException(m, err);
    }

    rw = CodeReviewCommit.newRevWalk(repo);
    rw.sort(RevSort.TOPO);
    rw.sort(RevSort.COMMIT_TIME_DESC, true);
    canMergeFlag = rw.newFlag("CAN_MERGE");

    inserter = repo.newObjectInserter();
  }

  private RefUpdate openBranch(Branch.NameKey destBranch)
      throws MergeException, OrmException, NoSuchChangeException {
    try {
      RefUpdate branchUpdate = repo.updateRef(destBranch.get());
      if (branchUpdate.getOldObjectId() != null) {
        branchTip =
            (CodeReviewCommit) rw.parseCommit(branchUpdate.getOldObjectId());
      } else if (repo.getFullBranch().equals(destBranch.get())) {
        branchTip = null;
        branchUpdate.setExpectedOldObjectId(ObjectId.zeroId());
      } else {
        for (ChangeData cd : queryProvider.get().submitted(destBranch)) {
          try {
            Change c = cd.change();
            setNew(notesFactory.create(c), message(c, "Change could not be merged, "
                + "because the destination branch does not exist anymore."));
          } catch (OrmException e) {
            log.error("Error setting change new", e);
          }
        }
      }
      logDebug("Opened branch {}: {}", destBranch.get(), branchTip);
      return branchUpdate;
    } catch (IOException e) {
      throw new MergeException("Cannot open branch", e);
    }
  }

  private Set<RevCommit> getAlreadyAccepted(CodeReviewCommit branchTip)
      throws MergeException {
    Set<RevCommit> alreadyAccepted = new HashSet<>();

    if (branchTip != null) {
      alreadyAccepted.add(branchTip);
    }

    try {
      for (Ref r : repo.getRefDatabase().getRefs(Constants.R_HEADS).values()) {
        try {
          alreadyAccepted.add(rw.parseCommit(r.getObjectId()));
        } catch (IncorrectObjectTypeException iote) {
          // Not a commit? Skip over it.
        }
      }
    } catch (IOException e) {
      throw new MergeException(
          "Failed to determine already accepted commits.", e);
    }

    logDebug("Found {} existing heads", alreadyAccepted.size());
    return alreadyAccepted;
  }

  private ListMultimap<SubmitType, Change> validateChangeList(
      List<ChangeData> submitted) throws MergeException {
    logDebug("Validating {} changes", submitted.size());
    ListMultimap<SubmitType, Change> toSubmit = ArrayListMultimap.create();

    Map<String, Ref> allRefs;
    try {
      allRefs = repo.getRefDatabase().getRefs(ALL);
    } catch (IOException e) {
      throw new MergeException(e.getMessage(), e);
    }

    Set<ObjectId> tips = new HashSet<>();
    for (Ref r : allRefs.values()) {
      tips.add(r.getObjectId());
    }

    for (ChangeData cd : submitted) {
      ChangeControl ctl;
      Change chg;
      try {
        ctl = cd.changeControl();
        // Reload change in case index was stale.
        chg = cd.reloadChange();
      } catch (OrmException e) {
        throw new MergeException("Failed to validate changes", e);
      }
      Change.Id changeId = cd.getId();
      if (chg.getStatus() != Change.Status.SUBMITTED) {
        logDebug("Change {} is not submitted: {}", changeId, chg.getStatus());
        continue;
      }
      if (chg.currentPatchSetId() == null) {
        logError("Missing current patch set on change " + changeId);
        commits.put(changeId, CodeReviewCommit.noPatchSet(ctl));
        toUpdate.add(chg);
        continue;
      }

      PatchSet ps;
      Branch.NameKey destBranch = chg.getDest();
      try {
        ps = cd.currentPatchSet();
      } catch (OrmException e) {
        throw new MergeException("Cannot query the database", e);
      }
      if (ps == null || ps.getRevision() == null
          || ps.getRevision().get() == null) {
        logError("Missing patch set or revision on change " + changeId);
        commits.put(changeId, CodeReviewCommit.noPatchSet(ctl));
        toUpdate.add(chg);
        continue;
      }

      String idstr = ps.getRevision().get();
      ObjectId id;
      try {
        id = ObjectId.fromString(idstr);
      } catch (IllegalArgumentException iae) {
        logError("Invalid revision on patch set " + ps.getId());
        commits.put(changeId, CodeReviewCommit.noPatchSet(ctl));
        toUpdate.add(chg);
        continue;
      }

      if (!tips.contains(id)) {
        // TODO Technically the proper way to do this test is to use a
        // RevWalk on "$id --not --all" and test for an empty set. But
        // that is way slower than looking for a ref directly pointing
        // at the desired tip. We should always have a ref available.
        //
        // TODO this is actually an error, the branch is gone but we
        // want to merge the issue. We can't safely do that if the
        // tip is not reachable.
        //
        logError("Revision " + idstr + " of patch set " + ps.getId()
            + " is not contained in any ref");
        commits.put(changeId, CodeReviewCommit.revisionGone(ctl));
        toUpdate.add(chg);
        continue;
      }

      CodeReviewCommit commit;
      try {
        commit = (CodeReviewCommit) rw.parseCommit(id);
      } catch (IOException e) {
        logError("Invalid commit " + idstr + " on patch set " + ps.getId(),
            chg.getDest(), e);
        commits.put(changeId, CodeReviewCommit.revisionGone(ctl));
        toUpdate.add(chg);
        continue;
      }

      // TODO(dborowitz): Consider putting ChangeData in CodeReviewCommit.
      commit.setControl(ctl);
      commit.setPatchsetId(ps.getId());
      commits.put(changeId, commit);

      MergeValidators mergeValidators = mergeValidatorsFactory.create();
      try {
        mergeValidators.validatePreMerge(
            repo, commit, destProject, destBranch, ps.getId());
      } catch (MergeValidationException mve) {
        logDebug("Revision {} of patch set {} failed validation: {}",
            idstr, ps.getId(), mve.getStatus());
        commit.setStatusCode(mve.getStatus());
        toUpdate.add(chg);
        continue;
      }

      if (branchTip != null) {
        // If this commit is already merged its a bug in the queuing code
        // that we got back here. Just mark it complete and move on. It's
        // merged and that is all that mattered to the requestor.
        //
        try {
          if (rw.isMergedInto(commit, branchTip)) {
            logDebug("Revision {} of patch set {} is already merged",
                idstr, ps.getId());
            commit.setStatusCode(CommitMergeStatus.ALREADY_MERGED);
            try {
              setMerged(chg, null, commit);
            } catch (OrmException e) {
              logError("Cannot mark change " + chg.getId() + " merged", e);
            }
            continue;
          }
        } catch (IOException err) {
          throw new MergeException("Cannot perform merge base test", err);
        }
      }

      SubmitType submitType;
      submitType = getSubmitType(commit.getControl(), ps);
      if (submitType == null) {
        logError("No submit type for revision " + idstr + " of patch set "
            + ps.getId());
        commit.setStatusCode(CommitMergeStatus.NO_SUBMIT_TYPE);
        toUpdate.add(chg);
        continue;
      }

      commit.add(canMergeFlag);
      toSubmit.put(submitType, chg);
    }
    logDebug("Submitting on this run: {}", toSubmit);
    return toSubmit;
  }

  private SubmitType getSubmitType(ChangeControl ctl, PatchSet ps) {
    try {
      ChangeData cd = changeDataFactory.create(db, ctl);
      SubmitTypeRecord r = new SubmitRuleEvaluator(cd).setPatchSet(ps)
          .getSubmitType();
      if (r.status != SubmitTypeRecord.Status.OK) {
        logError("Failed to get submit type for " + ctl.getChange().getKey());
        return null;
      }
      return r.type;
    } catch (OrmException e) {
      logError("Failed to get submit type for " + ctl.getChange().getKey(), e);
      return null;
    }
  }

  private RefUpdate updateBranch(Branch.NameKey destBranch,
      RefUpdate branchUpdate) throws MergeException {
    CodeReviewCommit currentTip =
        mergeTip != null ? mergeTip.getCurrentTip() : null;
    if (Objects.equals(branchTip, currentTip)) {
      logDebug("Branch already at merge tip {}, no update to perform",
          currentTip.name());
      return null;
    } else if (currentTip == null) {
      logDebug("No merge tip, no update to perform");
      return null;
    }

    if (RefNames.REFS_CONFIG.equals(branchUpdate.getName())) {
      logDebug("Loading new configuration from {}", RefNames.REFS_CONFIG);
      try {
        ProjectConfig cfg =
            new ProjectConfig(destProject.getProject().getNameKey());
        cfg.load(repo, currentTip);
      } catch (Exception e) {
        throw new MergeException("Submit would store invalid"
            + " project configuration " + currentTip.name() + " for "
            + destProject.getProject().getName(), e);
      }
    }

    branchUpdate.setRefLogIdent(refLogIdent);
    branchUpdate.setForceUpdate(false);
    branchUpdate.setNewObjectId(currentTip);
    branchUpdate.setRefLogMessage("merged", true);
    try {
      RefUpdate.Result result = branchUpdate.update(rw);
      logDebug("Update of {}: {}..{} returned status {}",
          branchUpdate.getName(), branchUpdate.getOldObjectId(),
          branchUpdate.getNewObjectId(), result);
      switch (result) {
        case NEW:
        case FAST_FORWARD:
          if (branchUpdate.getResult() == RefUpdate.Result.FAST_FORWARD) {
            tagCache.updateFastForward(destBranch.getParentKey(),
                branchUpdate.getName(),
                branchUpdate.getOldObjectId(),
                currentTip);
          }

          if (RefNames.REFS_CONFIG.equals(branchUpdate.getName())) {
            Project p = destProject.getProject();
            projectCache.evict(p);
            destProject = projectCache.get(p.getNameKey());
            repoManager.setProjectDescription(
                p.getNameKey(), p.getDescription());
          }

          return branchUpdate;

        case LOCK_FAILURE:
          throw new MergeException("Could not get the locks on the filesystem");
        default:
          throw new IOException(branchUpdate.getResult().name()
              + '\n' + branchUpdate);
      }
    } catch (IOException e) {
      throw new MergeException("Cannot update " + branchUpdate.getName(), e);
    }
  }

  private void fireRefUpdated(Branch.NameKey destBranch,
      RefUpdate branchUpdate) {
    logDebug("Firing ref updated hooks for {}", branchUpdate.getName());
    gitRefUpdated.fire(destBranch.getParentKey(), branchUpdate);
    hooks.doRefUpdatedHook(destBranch, branchUpdate,
        getAccount(mergeTip.getCurrentTip()));
  }

  private Account getAccount(CodeReviewCommit codeReviewCommit) {
    Account account = null;
    PatchSetApproval submitter = approvalsUtil.getSubmitter(
        db, codeReviewCommit.notes(), codeReviewCommit.getPatchsetId());
    if (submitter != null) {
      account = accountCache.get(submitter.getAccountId()).getAccount();
    }
    return account;
  }

  private String getByAccountName(CodeReviewCommit codeReviewCommit) {
    Account account = getAccount(codeReviewCommit);
    if (account != null && account.getFullName() != null) {
      return " by " + account.getFullName();
    }
    return "";
  }

  private void updateChangeStatus(List<Change> submitted, MergeTip mergeTip)
      throws NoSuchChangeException, MergeException, ResourceConflictException {
    logDebug("Updating change status for {} changes", submitted.size());
    for (Change c : submitted) {
      CodeReviewCommit commit = commits.get(c.getId());
      CommitMergeStatus s = commit != null ? commit.getStatusCode() : null;
      if (s == null) {
        // Shouldn't ever happen, but leave the change alone. We'll pick
        // it up on the next pass.
        //
        logDebug("Submitted change {} did not appear in set of new commits"
            + " produced by merge strategy", c.getId());
        continue;
      }

      String txt = s.getMessage();
      logDebug("Status of change {} ({}) on {}: {}", c.getId(), commit.name(),
          c.getDest(), s);
      // If mergeTip is null merge failed and mergeResultRev will not be read.
      ObjectId mergeResultRev =
          mergeTip != null ? mergeTip.getMergeResults().get(commit) : null;
      try {
        ChangeMessage msg;
        switch (s) {
          case CLEAN_MERGE:
            setMerged(c, message(c, txt + getByAccountName(commit)),
                mergeResultRev);
            break;

          case CLEAN_REBASE:
          case CLEAN_PICK:
            setMerged(c, message(c, txt + " as " + commit.name()
                + getByAccountName(commit)), mergeResultRev);
            break;

          case ALREADY_MERGED:
            setMerged(c, null, mergeResultRev);
            break;

          case PATH_CONFLICT:
          case MANUAL_RECURSIVE_MERGE:
          case CANNOT_CHERRY_PICK_ROOT:
          case NOT_FAST_FORWARD:
          case INVALID_PROJECT_CONFIGURATION:
          case INVALID_PROJECT_CONFIGURATION_PLUGIN_VALUE_NOT_PERMITTED:
          case INVALID_PROJECT_CONFIGURATION_PLUGIN_VALUE_NOT_EDITABLE:
          case INVALID_PROJECT_CONFIGURATION_PARENT_PROJECT_NOT_FOUND:
          case INVALID_PROJECT_CONFIGURATION_ROOT_PROJECT_CANNOT_HAVE_PARENT:
          case SETTING_PARENT_PROJECT_ONLY_ALLOWED_BY_ADMIN:
            setNew(commit.notes(), message(c, txt));
            throw new ResourceConflictException("Cannot merge " + commit.getId().getName()
                + ".\n" + s.getMessage());

          case MISSING_DEPENDENCY:
            logDebug("Change {} is missing dependency", c.getId());
            throw new MergeException("Cannot merge " + commit.getId().getName()
                + ".\n" + s.getMessage());

          case REVISION_GONE:
            logDebug("Commit not found for change {}", c.getId());
            msg = new ChangeMessage(
                new ChangeMessage.Key(
                    c.getId(),
                    ChangeUtil.messageUUID(db)),
                null,
                TimeUtil.nowTs(),
                c.currentPatchSetId());
            msg.setMessage("Failed to read commit for this patch set");
            setNew(commit.notes(), msg);
            break;

          default:
            msg = message(c, "Unspecified merge failure: " + s.name());
            setNew(commit.notes(), msg);
            throw new MergeException(msg.getMessage());
        }
      } catch (OrmException | IOException err) {
        logWarn("Error updating change status for " + c.getId(), c.getDest(), err);
      }
    }
  }

  private void updateSubscriptions(Branch.NameKey destBranch,
      List<Change> submitted) {
    if (mergeTip != null
        && (branchTip == null || branchTip != mergeTip.getCurrentTip())) {
      logDebug("Updating submodule subscriptions for {} changes",
          submitted.size());
      SubmoduleOp subOp =
          subOpFactory.create(destBranch, mergeTip.getCurrentTip(), rw, repo,
              destProject.getProject(), submitted, commits,
              getAccount(mergeTip.getCurrentTip()));
      try {
        subOp.update();
      } catch (SubmoduleException e) {
        logError(
            "The gitLinks were not updated according to the subscriptions",
            destBranch, e);
      }
    }
  }

  private ChangeMessage message(Change c, String body) {
    String uuid;
    try {
      uuid = ChangeUtil.messageUUID(db);
    } catch (OrmException e) {
      return null;
    }
    ChangeMessage m = new ChangeMessage(new ChangeMessage.Key(c.getId(), uuid),
        null, TimeUtil.nowTs(), c.currentPatchSetId());
    m.setMessage(body);
    return m;
  }

  private void setMerged(Change c, ChangeMessage msg, ObjectId mergeResultRev)
      throws OrmException, IOException {
    logDebug("Setting change {} merged", c.getId());
    ChangeUpdate update = null;
    final PatchSetApproval submitter;
    PatchSet merged;
    try {
      db.changes().beginTransaction(c.getId());

      // We must pull the patchset out of commits, because the patchset ID is
      // modified when using the cherry-pick merge strategy.
      CodeReviewCommit commit = commits.get(c.getId());
      PatchSet.Id mergedId = commit.change().currentPatchSetId();
      merged = db.patchSets().get(mergedId);
      c = setMergedPatchSet(c.getId(), mergedId);
      submitter = approvalsUtil.getSubmitter(db, commit.notes(), mergedId);
      ChangeControl control = commit.getControl();
      update = updateFactory.create(control, c.getLastUpdatedOn());

      // TODO(yyonas): we need to be able to change the author of the message
      // is not the person for whom the change was made. addMergedMessage
      // did this in the past.
      if (msg != null) {
        cmUtil.addChangeMessage(db, update, msg);
      }
      db.commit();

    } finally {
      db.rollback();
    }
    update.commit();
    final Change change = c;
    try {
      threadScoper.scope(new Callable<Void>(){
        @Override
        public Void call() throws Exception {
          sendMergedEmail(change, submitter);
          return null;
        }
      }).call();
    } catch (Exception e) {
      // sendMergedEmail doesn't throw any exception, so we'd have problems with
      // the soping here.
      logError("Errors in thread scoping for sending email");
    }

    indexer.index(db, c);
    if (submitter != null && mergeResultRev != null) {
      try {
        hooks.doChangeMergedHook(c,
            accountCache.get(submitter.getAccountId()).getAccount(),
            merged, db, mergeResultRev.name());
      } catch (OrmException ex) {
        logError("Cannot run hook for submitted patch set " + c.getId(),
            c.getDest(), ex);
      }
    }
  }

  private Change setMergedPatchSet(Change.Id changeId, final PatchSet.Id merged)
      throws OrmException {
    return db.changes().atomicUpdate(changeId, new AtomicUpdate<Change>() {
      @Override
      public Change update(Change c) {
        c.setStatus(Change.Status.MERGED);
        if (!merged.equals(c.currentPatchSetId())) {
          // Uncool; the patch set changed after we merged it.
          // Go back to the patch set that was actually merged.
          //
          try {
            c.setCurrentPatchSet(patchSetInfoFactory.get(db, merged));
          } catch (PatchSetInfoNotAvailableException e1) {
            logError("Cannot read merged patch set " + merged, c.getDest(), e1);
          }
        }
        ChangeUtil.updated(c);
        return c;
      }
    });
  }

  private void sendMergedEmail(final Change c, final PatchSetApproval from) {
    workQueue.getDefaultQueue()
        .submit(new Runnable() {
      @Override
      public void run() {
        PatchSet patchSet;
        try {
          ReviewDb reviewDb = schemaFactory.open();
          try {
            patchSet = reviewDb.patchSets().get(c.currentPatchSetId());
          } finally {
            reviewDb.close();
          }
        } catch (Exception e) {
          logError("Cannot send email for submitted patch set " + c.getId(), e);
          return;
        }

        try {
          MergedSender cm = mergedSenderFactory.create(c.getId());
          if (from != null) {
            cm.setFrom(from.getAccountId());
          }
          cm.setPatchSet(patchSet);
          cm.send();
        } catch (Exception e) {
          logError("Cannot send email for submitted patch set " + c.getId(), e);
        }
      }

      @Override
      public String toString() {
        return "send-email merged";
      }
    });
  }

  private ChangeControl changeControl(Change c) throws NoSuchChangeException {
    return changeControlFactory.controlFor(
        c, identifiedUserFactory.create(c.getOwner()));
  }

  private void setNew(ChangeNotes notes, ChangeMessage msg)
      throws NoSuchChangeException, IOException {
    Change c = notes.getChange();
    Change change = null;
    ChangeUpdate update = null;
    try {
      db.changes().beginTransaction(c.getId());
      try {
        change = db.changes().atomicUpdate(
            c.getId(),
            new AtomicUpdate<Change>() {
          @Override
          public Change update(Change c) {
            if (c.getStatus().isOpen()) {
              c.setStatus(Change.Status.NEW);
              ChangeUtil.updated(c);
            }
            return c;
          }
        });
        ChangeControl control = changeControl(change);

        //TODO(yyonas): atomic change is not propagated.
        update = updateFactory.create(control, c.getLastUpdatedOn());
        if (msg != null) {
          cmUtil.addChangeMessage(db, update, msg);
        }
        db.commit();
      } finally {
        db.rollback();
      }
    } catch (OrmException err) {
      logWarn("Cannot record merge failure message", c.getDest(), err);
    }
    if (update != null) {
      update.commit();
    }

    indexer.index(db, change);

    PatchSetApproval submitter = null;
    try {
      submitter = approvalsUtil.getSubmitter(
          db, notes, notes.getChange().currentPatchSetId());
    } catch (Exception e) {
      logError("Cannot get submitter for change " + notes.getChangeId(),
          notes.getChange().getDest(), e);
    }
    if (submitter != null) {
      try {
        hooks.doMergeFailedHook(c,
            accountCache.get(submitter.getAccountId()).getAccount(),
            db.patchSets().get(c.currentPatchSetId()), msg.getMessage(), db);
      } catch (OrmException ex) {
        logError("Cannot run hook for merge failed " + c.getId(), ex);
      }
    }
  }

  private void abandonAllOpenChanges(Project.NameKey destProject)
      throws NoSuchChangeException {
    try {
      openSchema();
      for (ChangeData cd
          : queryProvider.get().byProjectOpen(destProject)) {
        abandonOneChange(cd.change());
      }
      db.close();
      db = null;
    } catch (IOException | OrmException e) {
      logWarn("Cannot abandon changes for deleted project "
          + destProject.get(), null, e);
    }
  }

  private void abandonOneChange(Change change) throws OrmException,
    NoSuchChangeException,  IOException {
    db.changes().beginTransaction(change.getId());

    //TODO(dborowitz): support InternalUser in ChangeUpdate
    ChangeControl control = changeControlFactory.controlFor(change,
        identifiedUserFactory.create(change.getOwner()));
    ChangeUpdate update = updateFactory.create(control);
    try {
      change = db.changes().atomicUpdate(
        change.getId(),
        new AtomicUpdate<Change>() {
          @Override
          public Change update(Change change) {
            if (change.getStatus().isOpen()) {
              change.setStatus(Change.Status.ABANDONED);
              return change;
            }
            return null;
          }
        });

      if (change != null) {
        ChangeMessage msg = new ChangeMessage(
            new ChangeMessage.Key(
                change.getId(),
                ChangeUtil.messageUUID(db)),
            null,
            change.getLastUpdatedOn(),
            change.currentPatchSetId());
        msg.setMessage("Project was deleted.");

        //TODO(yyonas): atomic change is not propagated.
        cmUtil.addChangeMessage(db, update, msg);
        db.commit();
        indexer.index(db, change);
      }
    } finally {
      db.rollback();
    }
    update.commit();
  }

  private void logDebug(String msg, Object... args) {
    if (log.isDebugEnabled()) {
      log.debug(logPrefix(null) + msg, args);
    }
  }

  private void logWarn(String msg, Branch.NameKey branch, Throwable t) {
    if (log.isWarnEnabled()) {
      log.warn(logPrefix(branch) + msg, t);
    }
  }

  private void logWarn(String msg) {
    if (log.isWarnEnabled()) {
      String logPrefix = String.format("[%s]: ",
          ISODateTimeFormat.hourMinuteSecond().print(TimeUtil.nowMs()));
      log.warn(logPrefix + msg);
    }
  }

  private void logError(String msg, Branch.NameKey branch, Throwable t) {
    if (log.isErrorEnabled()) {
      if (t != null) {
        log.error(logPrefix(branch) + msg, t);
      } else {
        log.error(logPrefix(branch) + msg);
      }
    }
  }

  private void logError(String msg, Throwable t) {
    logError(msg, null, t);
  }

  private void logError(String msg) {
    logError(msg, null, null);
  }

  private String logPrefix(Branch.NameKey branch) {
    if (branch != null) {
      return String.format("[%s@%s]: ", branch.get(),
          ISODateTimeFormat.hourMinuteSecond().print(TimeUtil.nowMs()));
    } else {
      return String.format("[%s]: ",
          ISODateTimeFormat.hourMinuteSecond().print(TimeUtil.nowMs()));
    }
  }
}
