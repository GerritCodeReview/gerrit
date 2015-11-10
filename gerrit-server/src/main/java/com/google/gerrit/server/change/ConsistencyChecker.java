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

import static com.google.gerrit.server.ChangeUtil.PS_ID_ORDER;
import static com.google.gerrit.server.ChangeUtil.TO_PS_ID;

import com.google.auto.value.AutoValue;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.FluentIterable;
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
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.RefControl;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.AtomicUpdate;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    private static Result create(Change.Id id, List<ProblemInfo> problems) {
      return new AutoValue_ConsistencyChecker_Result(id, null, problems);
    }

    private static Result create(Change c, List<ProblemInfo> problems) {
      return new AutoValue_ConsistencyChecker_Result(c.getId(), c, problems);
    }

    public abstract Change.Id id();

    @Nullable
    public abstract Change change();

    public abstract List<ProblemInfo> problems();
  }

  private final Provider<ReviewDb> db;
  private final GitRepositoryManager repoManager;
  private final Provider<CurrentUser> user;
  private final Provider<PersonIdent> serverIdent;
  private final ProjectControl.GenericFactory projectControlFactory;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final PatchSetInserter.Factory patchSetInserterFactory;
  private final BatchUpdate.Factory updateFactory;
  private final ChangeIndexer indexer;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final ChangeUpdate.Factory changeUpdateFactory;

  private FixInput fix;
  private Change change;
  private Repository repo;
  private RevWalk rw;

  private RevCommit tip;
  private Multimap<ObjectId, PatchSet> patchSetsBySha;
  private PatchSet currPs;
  private RevCommit currPsCommit;

  private List<ProblemInfo> problems;

  @Inject
  ConsistencyChecker(Provider<ReviewDb> db,
      GitRepositoryManager repoManager,
      Provider<CurrentUser> user,
      @GerritPersonIdent Provider<PersonIdent> serverIdent,
      ProjectControl.GenericFactory projectControlFactory,
      PatchSetInfoFactory patchSetInfoFactory,
      PatchSetInserter.Factory patchSetInserterFactory,
      BatchUpdate.Factory updateFactory,
      ChangeIndexer indexer,
      ChangeControl.GenericFactory changeControlFactory,
      ChangeUpdate.Factory changeUpdateFactory) {
    this.db = db;
    this.repoManager = repoManager;
    this.user = user;
    this.serverIdent = serverIdent;
    this.projectControlFactory = projectControlFactory;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.patchSetInserterFactory = patchSetInserterFactory;
    this.updateFactory = updateFactory;
    this.indexer = indexer;
    this.changeControlFactory = changeControlFactory;
    this.changeUpdateFactory = changeUpdateFactory;
    reset();
  }

  private void reset() {
    change = null;
    repo = null;
    rw = null;
    problems = new ArrayList<>();
  }

  public Result check(ChangeData cd) {
    return check(cd, null);
  }

  public Result check(ChangeData cd, @Nullable FixInput f) {
    reset();
    try {
      return check(cd.change(), f);
    } catch (OrmException e) {
      error("Error looking up change", e);
      return Result.create(cd.getId(), problems);
    }
  }

  public Result check(Change c) {
    return check(c, null);
  }

  public Result check(Change c, @Nullable FixInput f) {
    reset();
    fix = f;
    change = c;
    try {
      checkImpl();
      return Result.create(c, problems);
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
      if (db.get().accounts().get(change.getOwner()) == null) {
        problem("Missing change owner: " + change.getOwner());
      }
    } catch (OrmException e) {
      error("Failed to look up owner", e);
    }
  }

  private void checkCurrentPatchSetEntity() {
    try {
      PatchSet.Id psId = change.currentPatchSetId();
      currPs = db.get().patchSets().get(psId);
      if (currPs == null) {
        problem(String.format("Current patch set %d not found", psId.get()));
      }
    } catch (OrmException e) {
      error("Failed to look up current patch set", e);
    }
  }

  private boolean openRepo() {
    Project.NameKey project = change.getDest().getParentKey();
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
      all = Lists.newArrayList(db.get().patchSets().byChange(change.getId()));
    } catch (OrmException e) {
      return error("Failed to look up patch sets", e);
    }
    // Iterate in descending order so deletePatchSet can assume the latest patch
    // set exists.
    Collections.sort(all, PS_ID_ORDER.reverse());
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
          deletePatchSet(lastProblem(), ps.getId());
        }
        continue;
      } else if (refProblem != null && fix != null) {
        fixPatchSetRef(refProblem, ps);
      }
      if (ps.getId().equals(change.currentPatchSetId())) {
        currPsCommit = psCommit;
      }
    }

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
    String refName = change.getDest().get();
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

  private void checkMergedBitMatchesStatus(PatchSet.Id psId, RevCommit commit,
      boolean merged) {
    String refName = change.getDest().get();
    if (merged && change.getStatus() != Change.Status.MERGED) {
      ProblemInfo p = problem(String.format(
          "Patch set %d (%s) is merged into destination ref %s (%s), but change"
          + " status is %s", psId.get(), commit.name(),
          refName, tip.name(), change.getStatus()));
      if (fix != null) {
        fixMerged(p);
      }
    } else if (!merged && change.getStatus() == Change.Status.MERGED) {
      problem(String.format("Patch set %d (%s) is not merged into"
            + " destination ref %s (%s), but change status is %s",
            currPs.getId().get(), commit.name(), refName, tip.name(),
            change.getStatus()));
    }
  }

  private void checkExpectMergedAs() {
    ObjectId objId =
        parseObjectId(fix.expectMergedAs, "expected merged commit");
    RevCommit commit = parseCommit(objId, "expected merged commit");
    if (commit == null) {
      return;
    }
    if (Objects.equals(commit, currPsCommit)) {
      // Caller gave us latest patch set SHA-1; verified in checkPatchSets.
      return;
    }

    try {
      if (!rw.isMergedInto(commit, tip)) {
        problem(String.format("Expected merged commit %s is not merged into"
              + " destination ref %s (%s)",
              commit.name(), change.getDest().get(), tip.name()));
        return;
      }

      RevId revId = new RevId(commit.name());
      List<PatchSet> patchSets = FluentIterable
          .from(db.get().patchSets().byRevision(revId))
          .filter(new Predicate<PatchSet>() {
            @Override
            public boolean apply(PatchSet ps) {
              try {
                Change c = db.get().changes().get(ps.getId().getParentKey());
                return c != null && c.getDest().equals(change.getDest());
              } catch (OrmException e) {
                warn(e);
                return true; // Should cause an error below, that's good.
              }
            }
          }).toSortedList(ChangeUtil.PS_ID_ORDER);
      switch (patchSets.size()) {
        case 0:
          // No patch set for this commit; insert one.
          rw.parseBody(commit);
          String changeId = Iterables.getFirst(
              commit.getFooterLines(FooterConstants.CHANGE_ID), null);
          // Missing Change-Id footer is ok, but mismatched is not.
          if (changeId != null && !changeId.equals(change.getKey().get())) {
            problem(String.format("Expected merged commit %s has Change-Id: %s,"
                  + " but expected %s",
                  commit.name(), changeId, change.getKey().get()));
            return;
          }
          PatchSet.Id psId = insertPatchSet(commit);
          if (psId != null) {
            checkMergedBitMatchesStatus(psId, commit, true);
          }
          break;

        case 1:
          // Existing patch set of this commit; check that it is the current
          // patch set.
          // TODO(dborowitz): This could be fixed if it's an older patch set of
          // the current change.
          PatchSet.Id id = patchSets.get(0).getId();
          if (!id.equals(change.currentPatchSetId())) {
            problem(String.format("Expected merged commit %s corresponds to"
                  + " patch set %s, which is not the current patch set %s",
                  commit.name(), id, change.currentPatchSetId()));
          }
          break;

        default:
          problem(String.format(
                "Multiple patch sets for expected merged commit %s: %s",
                commit.name(), patchSets));
          break;
      }
    } catch (OrmException | IOException e) {
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
      RefControl ctl = projectControlFactory
          .controlFor(change.getProject(), user.get())
          .controlForRef(change.getDest());
      PatchSet.Id psId =
          ChangeUtil.nextPatchSetId(repo, change.currentPatchSetId());
      PatchSetInserter inserter =
          patchSetInserterFactory.create(ctl, psId, commit);
      try (BatchUpdate bu = updateFactory.create(
            db.get(), change.getProject(), ctl.getUser(), TimeUtil.nowTs());
          ObjectInserter oi = repo.newObjectInserter()) {
        bu.setRepository(repo, rw, oi);
        bu.addOp(change.getId(), inserter
            .setValidatePolicy(CommitValidators.Policy.NONE)
            .setRunHooks(false)
            .setSendMail(false)
            .setAllowClosed(true)
            .setUploader(user.get().getAccountId())
            .setMessage(
                "Patch set for merged commit inserted by consistency checker"));
        bu.execute();
      }
      change = inserter.getChange();
      p.status = Status.FIXED;
      p.outcome = "Inserted as patch set " + psId.get();
      return psId;
    } catch (IOException | NoSuchProjectException | UpdateException
        | RestApiException e) {
      warn(e);
      p.status = Status.FIX_FAILED;
      p.outcome = "Error inserting new patch set";
      return null;
    }
  }

  private void fixMerged(ProblemInfo p) {
    try {
      change = db.get().changes().atomicUpdate(change.getId(),
          new AtomicUpdate<Change>() {
            @Override
            public Change update(Change c) {
              c.setStatus(Change.Status.MERGED);
              return c;
            }
          });
      ChangeUpdate changeUpdate =
          changeUpdateFactory.create(
              changeControlFactory.controlFor(change, user.get()));
      changeUpdate.fixStatus(Change.Status.MERGED);
      changeUpdate.commit();
      indexer.index(db.get(), change);
      p.status = Status.FIXED;
      p.outcome = "Marked change as merged";
    } catch (OrmException | IOException | NoSuchChangeException e) {
      log.warn("Error marking " + change.getId() + "as merged", e);
      p.status = Status.FIX_FAILED;
      p.outcome = "Error updating status to merged";
    }
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

  private void deletePatchSet(ProblemInfo p, PatchSet.Id psId) {
    ReviewDb db = this.db.get();
    Change.Id cid = psId.getParentKey();
    try {
      db.changes().beginTransaction(cid);
      try {
        Change c = db.changes().get(cid);
        if (c == null) {
          throw new OrmException("Change missing: " + cid);
        }

        if (psId.equals(c.currentPatchSetId())) {
          List<PatchSet> all = Lists.newArrayList(db.patchSets().byChange(cid));
          if (all.size() == 1 && all.get(0).getId().equals(psId)) {
            p.status = Status.FIX_FAILED;
            p.outcome = "Cannot delete patch set; no patch sets would remain";
            return;
          }
          // If there were multiple missing patch sets, assumes deletePatchSet
          // has been called in decreasing order, so the max remaining PatchSet
          // is the effective current patch set.
          Collections.sort(all, PS_ID_ORDER.reverse());
          PatchSet.Id latest = null;
          for (PatchSet ps : all) {
            latest = ps.getId();
            if (!ps.getId().equals(psId)) {
              break;
            }
          }
          c.setCurrentPatchSet(patchSetInfoFactory.get(db, latest));
          db.changes().update(Collections.singleton(c));
        }

        // Delete dangling primary key references. Don't delete ChangeMessages,
        // which don't use patch sets as a primary key, and may provide useful
        // historical information.
        db.accountPatchReviews().delete(
            db.accountPatchReviews().byPatchSet(psId));
        db.patchSetApprovals().delete(
            db.patchSetApprovals().byPatchSet(psId));
        db.patchComments().delete(
            db.patchComments().byPatchSet(psId));
        db.patchSets().deleteKeys(Collections.singleton(psId));
        db.commit();

        p.status = Status.FIXED;
        p.outcome = "Deleted patch set";
      } finally {
        db.rollback();
      }
    } catch (PatchSetInfoNotAvailableException | OrmException e) {
      String msg = "Error deleting patch set";
      log.warn(msg + ' ' + psId, e);
      p.status = Status.FIX_FAILED;
      p.outcome = msg;
    }
  }

  private PersonIdent newRefLogIdent() {
    CurrentUser u = user.get();
    if (u.isIdentifiedUser()) {
      return u.asIdentifiedUser().newRefLogIdent();
    } else {
      return serverIdent.get();
    }
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
    log.warn("Error in consistency check of change " + change.getId(), t);
  }
}
