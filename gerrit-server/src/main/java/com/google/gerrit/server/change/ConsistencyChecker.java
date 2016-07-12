// Copyright (C) 2014 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.gerrit.reviewdb.client.RefNames.REFS_CHANGES;
import static com.google.gerrit.reviewdb.server.ReviewDbUtil.intKeyOrdering;
import static com.google.gerrit.server.ChangeUtil.PS_ID_ORDER;
import static com.google.gerrit.server.ChangeUtil.TO_PS_ID;

import com.google.auto.value.AutoValue;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.common.FooterConstants;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.api.changes.FixInput;
import com.google.gerrit.extensions.common.ProblemInfo;
import com.google.gerrit.extensions.common.ProblemInfo.Status;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.PatchSetState;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks changes for various kinds of inconsistency and corruption.
 * <p>
 * A single instance may be reused for checking multiple changes, but not
 * concurrently.
 */
public class ConsistencyChecker {
  private static final Logger log =
      LoggerFactory.getLogger(ConsistencyChecker.class);

  @AutoValue
  public abstract static class Result {
    private static Result create(ChangeControl ctl,
        List<ProblemInfo> problems) {
      return new AutoValue_ConsistencyChecker_Result(
          ctl.getId(), ctl.getChange(), problems);
    }

    public abstract Change.Id id();

    @Nullable
    public abstract Change change();

    public abstract List<ProblemInfo> problems();
  }

  private final BatchUpdate.Factory updateFactory;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final ChangeNotes.Factory notesFactory;
  private final DynamicItem<AccountPatchReviewStore> accountPatchReviewStore;
  private final GitRepositoryManager repoManager;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final PatchSetInserter.Factory patchSetInserterFactory;
  private final PatchSetUtil psUtil;
  private final Provider<CurrentUser> user;
  private final Provider<PersonIdent> serverIdent;
  private final Provider<ReviewDb> db;

  private FixInput fix;
  private ChangeControl ctl;
  private Repository repo;
  private RevWalk rw;

  private RevCommit tip;
  private Multimap<ObjectId, PatchSet> patchSetsBySha;
  private PatchSet currPs;
  private RevCommit currPsCommit;

  private List<ProblemInfo> problems;

  @Inject
  ConsistencyChecker(
      @GerritPersonIdent Provider<PersonIdent> serverIdent,
      BatchUpdate.Factory updateFactory,
      ChangeControl.GenericFactory changeControlFactory,
      ChangeNotes.Factory notesFactory,
      DynamicItem<AccountPatchReviewStore> accountPatchReviewStore,
      GitRepositoryManager repoManager,
      PatchSetInfoFactory patchSetInfoFactory,
      PatchSetInserter.Factory patchSetInserterFactory,
      PatchSetUtil psUtil,
      Provider<CurrentUser> user,
      Provider<ReviewDb> db) {
    this.accountPatchReviewStore = accountPatchReviewStore;
    this.changeControlFactory = changeControlFactory;
    this.db = db;
    this.notesFactory = notesFactory;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.patchSetInserterFactory = patchSetInserterFactory;
    this.psUtil = psUtil;
    this.repoManager = repoManager;
    this.serverIdent = serverIdent;
    this.updateFactory = updateFactory;
    this.user = user;
    reset();
  }

  private void reset() {
    ctl = null;
    repo = null;
    rw = null;
    problems = new ArrayList<>();
  }

  private Change change() {
    return ctl.getChange();
  }

  public Result check(ChangeControl cc, @Nullable FixInput f) {
    checkNotNull(cc);
    try {
      reset();
      ctl = cc;
      fix = f;
      checkImpl();
      return result();
    } finally {
      if (rw != null) {
        rw.close();
      }
      if (repo != null) {
        repo.close();
      }
    }
  }

  private void checkImpl() {
    checkOwner();
    checkCurrentPatchSetEntity();

    // All checks that require the repo.
    if (!openRepo()) {
      return;
    }
    if (!checkPatchSets()) {
      return;
    }
    checkMerged();
  }

  private void checkOwner() {
    try {
      if (db.get().accounts().get(change().getOwner()) == null) {
        problem("Missing change owner: " + change().getOwner());
      }
    } catch (OrmException e) {
      error("Failed to look up owner", e);
    }
  }

  private void checkCurrentPatchSetEntity() {
    try {
      currPs = psUtil.current(db.get(), ctl.getNotes());
      if (currPs == null) {
        problem(String.format("Current patch set %d not found",
              change().currentPatchSetId().get()));
      }
    } catch (OrmException e) {
      error("Failed to look up current patch set", e);
    }
  }

  private boolean openRepo() {
    Project.NameKey project = change().getDest().getParentKey();
    try {
      repo = repoManager.openRepository(project);
      rw = new RevWalk(repo);
      return true;
    } catch (RepositoryNotFoundException e) {
      return error("Destination repository not found: " + project, e);
    } catch (IOException e) {
      return error("Failed to open repository: " + project, e);
    }
  }

  private boolean checkPatchSets() {
    List<PatchSet> all;
    try {
      // Iterate in descending order.
      all = PS_ID_ORDER.sortedCopy(psUtil.byChange(db.get(), ctl.getNotes()));
    } catch (OrmException e) {
      return error("Failed to look up patch sets", e);
    }
    patchSetsBySha = MultimapBuilder.hashKeys(all.size())
        .treeSetValues(PS_ID_ORDER)
        .build();

    Map<String, Ref> refs;
    try {
    refs = repo.getRefDatabase().exactRef(
        Lists.transform(all, new Function<PatchSet, String>() {
          @Override
          public String apply(PatchSet ps) {
            return ps.getId().toRefName();
          }
        }).toArray(new String[all.size()]));
    } catch (IOException e) {
      error("error reading refs", e);
      refs = Collections.emptyMap();
    }

    List<DeletePatchSetFromDbOp> deletePatchSetOps = new ArrayList<>();
    for (PatchSet ps : all) {
      // Check revision format.
      int psNum = ps.getId().get();
      String refName = ps.getId().toRefName();
      ObjectId objId =
          parseObjectId(ps.getRevision().get(), "patch set " + psNum);
      if (objId == null) {
        continue;
      }
      patchSetsBySha.put(objId, ps);

      // Check ref existence.
      ProblemInfo refProblem = null;
      Ref ref = refs.get(refName);
      if (ref == null) {
        refProblem = problem("Ref missing: " + refName);
      } else if (!objId.equals(ref.getObjectId())) {
        String actual = ref.getObjectId() != null
            ? ref.getObjectId().name()
            : "null";
        refProblem = problem(String.format(
            "Expected %s to point to %s, found %s",
            ref.getName(), objId.name(), actual));
      }

      // Check object existence.
      RevCommit psCommit = parseCommit(
          objId, String.format("patch set %d", psNum));
      if (psCommit == null) {
        if (fix != null && fix.deletePatchSetIfCommitMissing) {
          deletePatchSetOps.add(
              new DeletePatchSetFromDbOp(lastProblem(), ps.getId()));
        }
        continue;
      } else if (refProblem != null && fix != null) {
        fixPatchSetRef(refProblem, ps);
      }
      if (ps.getId().equals(change().currentPatchSetId())) {
        currPsCommit = psCommit;
      }
    }

    // Delete any bad patch sets found above, in a single update.
    deletePatchSets(deletePatchSetOps);

    // Check for duplicates.
    for (Map.Entry<ObjectId, Collection<PatchSet>> e
        : patchSetsBySha.asMap().entrySet()) {
      if (e.getValue().size() > 1) {
        problem(String.format("Multiple patch sets pointing to %s: %s",
            e.getKey().name(),
            Collections2.transform(e.getValue(), TO_PS_ID)));
      }
    }

    return currPs != null && currPsCommit != null;
  }

  private void checkMerged() {
    String refName = change().getDest().get();
    Ref dest;
    try {
      dest = repo.getRefDatabase().exactRef(refName);
    } catch (IOException e) {
      problem("Failed to look up destination ref: " + refName);
      return;
    }
    if (dest == null) {
      problem("Destination ref not found (may be new branch): " + refName);
      return;
    }
    tip = parseCommit(dest.getObjectId(),
        "destination ref " + refName);
    if (tip == null) {
      return;
    }

    if (fix != null && fix.expectMergedAs != null) {
      checkExpectMergedAs();
    } else {
      boolean merged;
      try {
        merged = rw.isMergedInto(currPsCommit, tip);
      } catch (IOException e) {
        problem("Error checking whether patch set " + currPs.getId().get()
            + " is merged");
        return;
      }
      checkMergedBitMatchesStatus(currPs.getId(), currPsCommit, merged);
    }
  }

  private ProblemInfo wrongChangeStatus(PatchSet.Id psId, RevCommit commit) {
    String refName = change().getDest().get();
    return problem(String.format(
        "Patch set %d (%s) is merged into destination ref %s (%s), but change"
        + " status is %s", psId.get(), commit.name(),
        refName, tip.name(), change().getStatus()));
  }

  private void checkMergedBitMatchesStatus(PatchSet.Id psId, RevCommit commit,
      boolean merged) {
    String refName = change().getDest().get();
    if (merged && change().getStatus() != Change.Status.MERGED) {
      ProblemInfo p = wrongChangeStatus(psId, commit);
      if (fix != null) {
        fixMerged(p);
      }
    } else if (!merged && change().getStatus() == Change.Status.MERGED) {
      problem(String.format("Patch set %d (%s) is not merged into"
            + " destination ref %s (%s), but change status is %s",
            currPs.getId().get(), commit.name(), refName, tip.name(),
            change().getStatus()));
    }
  }

  private void checkExpectMergedAs() {
    ObjectId objId =
        parseObjectId(fix.expectMergedAs, "expected merged commit");
    RevCommit commit = parseCommit(objId, "expected merged commit");
    if (commit == null) {
      return;
    }

    try {
      if (!rw.isMergedInto(commit, tip)) {
        problem(String.format("Expected merged commit %s is not merged into"
              + " destination ref %s (%s)",
              commit.name(), change().getDest().get(), tip.name()));
        return;
      }

      List<PatchSet.Id> psIds = new ArrayList<>();
      for (Ref ref : repo.getRefDatabase().getRefs(REFS_CHANGES).values()) {
        if (!ref.getObjectId().equals(commit)) {
          continue;
        }
        PatchSet.Id psId = PatchSet.Id.fromRef(ref.getName());
        if (psId == null) {
          continue;
        }
        try {
          Change c = notesFactory.createChecked(
              db.get(), change().getProject(), psId.getParentKey()).getChange();
          if (!c.getDest().equals(change().getDest())) {
            continue;
          }
        } catch (OrmException | NoSuchChangeException e) {
          warn(e);
          // Include this patch set; should cause an error below, which is good.
        }
        psIds.add(psId);
      }
      switch (psIds.size()) {
        case 0:
          // No patch set for this commit; insert one.
          rw.parseBody(commit);
          String changeId = Iterables.getFirst(
              commit.getFooterLines(FooterConstants.CHANGE_ID), null);
          // Missing Change-Id footer is ok, but mismatched is not.
          if (changeId != null && !changeId.equals(change().getKey().get())) {
            problem(String.format("Expected merged commit %s has Change-Id: %s,"
                  + " but expected %s",
                  commit.name(), changeId, change().getKey().get()));
            return;
          }
          // TODO(dborowitz): Combine into one op.
          insertPatchSet(commit);
          fixMerged(problem(String.format(
              "Expected merged commit %s has no associated patch set",
              commit.name())));
          break;

        case 1:
          // Existing patch set of this commit; check that it is the current
          // patch set.
          // TODO(dborowitz): This could be fixed if it's an older patch set of
          // the current change.
          PatchSet.Id id = psIds.get(0);
          if (id.equals(change().currentPatchSetId())) {
            fixMerged(wrongChangeStatus(id, commit));
          } else {
            problem(String.format("Expected merged commit %s corresponds to"
                  + " patch set %s, which is not the current patch set %s",
                  commit.name(), id, change().currentPatchSetId()));
          }
          break;

        default:
          problem(String.format(
                "Multiple patch sets for expected merged commit %s: %s",
                commit.name(), intKeyOrdering().sortedCopy(psIds)));
          break;
      }
    } catch (IOException e) {
      error("Error looking up expected merged commit " + fix.expectMergedAs,
          e);
    }
  }

  private PatchSet.Id insertPatchSet(RevCommit commit) {
    ProblemInfo p =
        problem("No patch set found for merged commit " + commit.name());
    if (!user.get().isIdentifiedUser()) {
      p.status = Status.FIX_FAILED;
      p.outcome =
          "Must be called by an identified user to insert new patch set";
      return null;
    }

    try {
      PatchSet.Id psId =
          ChangeUtil.nextPatchSetId(repo, change().currentPatchSetId());
      PatchSetInserter inserter =
          patchSetInserterFactory.create(ctl, psId, commit);
      try (BatchUpdate bu = newBatchUpdate();
          ObjectInserter oi = repo.newObjectInserter()) {
        bu.setRepository(repo, rw, oi);
        bu.addOp(ctl.getId(), inserter
            .setValidatePolicy(CommitValidators.Policy.NONE)
            .setFireRevisionCreated(false)
            .setSendMail(false)
            .setAllowClosed(true)
            .setMessage(
                "Patch set for merged commit inserted by consistency checker"));
        bu.execute();
      }
      ctl = changeControlFactory.controlFor(
          db.get(), inserter.getChange(), ctl.getUser());
      p.status = Status.FIXED;
      p.outcome = "Inserted as patch set " + psId.get();
      return psId;
    } catch (OrmException | IOException | NoSuchChangeException
        | UpdateException | RestApiException e) {
      warn(e);
      p.status = Status.FIX_FAILED;
      p.outcome = "Error inserting new patch set";
      return null;
    }
  }

  private void fixMerged(ProblemInfo p) {
    try (BatchUpdate bu = newBatchUpdate();
        ObjectInserter oi = repo.newObjectInserter()) {
      bu.setRepository(repo, rw, oi);
      bu.addOp(ctl.getId(), new BatchUpdate.Op() {
        @Override
        public boolean updateChange(ChangeContext ctx) throws OrmException {
          ctx.getChange().setStatus(Change.Status.MERGED);
          ctx.getUpdate(ctx.getChange().currentPatchSetId())
            .fixStatus(Change.Status.MERGED);
          return true;
        }
      });
      bu.execute();
      p.status = Status.FIXED;
      p.outcome = "Marked change as merged";
    } catch (UpdateException | RestApiException e) {
      log.warn("Error marking " + ctl.getId() + "as merged", e);
      p.status = Status.FIX_FAILED;
      p.outcome = "Error updating status to merged";
    }
  }

  private BatchUpdate newBatchUpdate() {
    return updateFactory.create(
        db.get(), change().getProject(), ctl.getUser(), TimeUtil.nowTs());
  }

  private void fixPatchSetRef(ProblemInfo p, PatchSet ps) {
    try {
      RefUpdate ru = repo.updateRef(ps.getId().toRefName());
      ru.setForceUpdate(true);
      ru.setNewObjectId(ObjectId.fromString(ps.getRevision().get()));
      ru.setRefLogIdent(newRefLogIdent());
      ru.setRefLogMessage("Repair patch set ref", true);
      RefUpdate.Result result = ru.update();
      switch (result) {
        case NEW:
        case FORCED:
        case FAST_FORWARD:
        case NO_CHANGE:
          p.status = Status.FIXED;
          p.outcome = "Repaired patch set ref";
          return;
        case IO_FAILURE:
        case LOCK_FAILURE:
        case NOT_ATTEMPTED:
        case REJECTED:
        case REJECTED_CURRENT_BRANCH:
        case RENAMED:
        default:
          p.status = Status.FIX_FAILED;
          p.outcome = "Failed to update patch set ref: " + result;
          return;
      }
    } catch (IOException e) {
      String msg = "Error fixing patch set ref";
      log.warn(msg + ' ' + ps.getId().toRefName(), e);
      p.status = Status.FIX_FAILED;
      p.outcome = msg;
    }
  }

  private void deletePatchSets(List<DeletePatchSetFromDbOp> ops) {
    try (BatchUpdate bu = newBatchUpdate();
        ObjectInserter oi = repo.newObjectInserter()) {
      bu.setRepository(repo, rw, oi);
      for (DeletePatchSetFromDbOp op : ops) {
        checkArgument(op.psId.getParentKey().equals(ctl.getId()));
        bu.addOp(ctl.getId(), op);
      }
      bu.addOp(ctl.getId(), new UpdateCurrentPatchSetOp(ops));
      bu.execute();
    } catch (NoPatchSetsWouldRemainException e) {
      for (DeletePatchSetFromDbOp op : ops) {
        op.p.status = Status.FIX_FAILED;
        op.p.outcome = e.getMessage();
      }
    } catch (UpdateException | RestApiException e) {
      String msg = "Error deleting patch set";
      log.warn(msg + " of change " + ops.get(0).psId.getParentKey(), e);
      for (DeletePatchSetFromDbOp op : ops) {
        // Overwrite existing statuses that were set before the transaction was
        // rolled back.
        op.p.status = Status.FIX_FAILED;
        op.p.outcome = msg;
      }
    }
  }

  private class DeletePatchSetFromDbOp extends BatchUpdate.Op {
    private final ProblemInfo p;
    private final PatchSet.Id psId;

    private DeletePatchSetFromDbOp(ProblemInfo p, PatchSet.Id psId) {
      this.p = p;
      this.psId = psId;
    }

    @Override
    public boolean updateChange(ChangeContext ctx)
        throws OrmException, PatchSetInfoNotAvailableException {
      // Delete dangling key references.
      ReviewDb db = DeleteDraftChangeOp.unwrap(ctx.getDb());
      accountPatchReviewStore.get().clearReviewed(psId);
      db.changeMessages().delete(
          db.changeMessages().byChange(psId.getParentKey()));
      db.patchSetApprovals().delete(
          db.patchSetApprovals().byPatchSet(psId));
      db.patchComments().delete(
          db.patchComments().byPatchSet(psId));
      db.patchSets().deleteKeys(Collections.singleton(psId));

      // NoteDb requires no additional fiddling; setting the state to deleted is
      // sufficient to filter everything else out.
      ctx.getUpdate(psId).setPatchSetState(PatchSetState.DELETED);

      p.status = Status.FIXED;
      p.outcome = "Deleted patch set";
      return true;
    }
  }

  private static class NoPatchSetsWouldRemainException
      extends RestApiException {
    private static final long serialVersionUID = 1L;

    private NoPatchSetsWouldRemainException() {
      super("Cannot delete patch set; no patch sets would remain");
    }
  }

  private class UpdateCurrentPatchSetOp extends BatchUpdate.Op {
    private final Set<PatchSet.Id> toDelete;

    private UpdateCurrentPatchSetOp(List<DeletePatchSetFromDbOp> deleteOps) {
      toDelete = new HashSet<>();
      for (DeletePatchSetFromDbOp op : deleteOps) {
        toDelete.add(op.psId);
      }
    }

    @Override
    public boolean updateChange(ChangeContext ctx)
        throws OrmException, PatchSetInfoNotAvailableException,
        NoPatchSetsWouldRemainException {
      if (!toDelete.contains(ctx.getChange().currentPatchSetId())) {
        return false;
      }
      Set<PatchSet.Id> all = new HashSet<>();
      // Doesn't make any assumptions about the order in which deletes happen
      // and whether they are seen by this op; we are already given the full set
      // of patch sets that will eventually be deleted in this update.
      for (PatchSet ps : psUtil.byChange(ctx.getDb(), ctx.getNotes())) {
        if (!toDelete.contains(ps.getId())) {
          all.add(ps.getId());
        }
      }
      if (all.isEmpty()) {
        throw new NoPatchSetsWouldRemainException();
      }
      PatchSet.Id latest = ReviewDbUtil.intKeyOrdering().max(all);
      ctx.getChange().setCurrentPatchSet(
          patchSetInfoFactory.get(ctx.getDb(), ctx.getNotes(), latest));
      return true;
    }
  }

  private PersonIdent newRefLogIdent() {
    CurrentUser u = user.get();
    if (u.isIdentifiedUser()) {
      return u.asIdentifiedUser().newRefLogIdent();
    }
    return serverIdent.get();
  }

  private ObjectId parseObjectId(String objIdStr, String desc) {
    try {
      return ObjectId.fromString(objIdStr);
    } catch (IllegalArgumentException e) {
      problem(String.format("Invalid revision on %s: %s", desc, objIdStr));
      return null;
    }
  }

  private RevCommit parseCommit(ObjectId objId, String desc) {
    try {
      return rw.parseCommit(objId);
    } catch (MissingObjectException e) {
      problem(String.format("Object missing: %s: %s", desc, objId.name()));
    } catch (IncorrectObjectTypeException e) {
      problem(String.format("Not a commit: %s: %s", desc, objId.name()));
    } catch (IOException e) {
      problem(String.format("Failed to look up: %s: %s", desc, objId.name()));
    }
    return null;
  }

  private ProblemInfo problem(String msg) {
    ProblemInfo p = new ProblemInfo();
    p.message = msg;
    problems.add(p);
    return p;
  }

  private ProblemInfo lastProblem() {
    return problems.get(problems.size() - 1);
  }

  private boolean error(String msg, Throwable t) {
    problem(msg);
    // TODO(dborowitz): Expose stack trace to administrators.
    warn(t);
    return false;
  }

  private void warn(Throwable t) {
    log.warn("Error in consistency check of change " + ctl.getId(), t);
  }

  private Result result() {
    return Result.create(ctl, problems);
  }
}
