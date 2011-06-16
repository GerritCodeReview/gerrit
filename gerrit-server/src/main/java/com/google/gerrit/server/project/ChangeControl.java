// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.project;

import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.rules.PrologEnvironment;
import com.google.gerrit.rules.StoredValues;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import com.googlecode.prolog_cafe.compiler.CompileException;
import com.googlecode.prolog_cafe.lang.IntegerTerm;
import com.googlecode.prolog_cafe.lang.PrologException;
import com.googlecode.prolog_cafe.lang.StructureTerm;
import com.googlecode.prolog_cafe.lang.Term;
import com.googlecode.prolog_cafe.lang.VariableTerm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;


/** Access control management for a user accessing a single change. */
public class ChangeControl {
  private static final Logger log = LoggerFactory
      .getLogger(ChangeControl.class);

  public static class GenericFactory {
    private final ProjectControl.GenericFactory projectControl;

    @Inject
    GenericFactory(ProjectControl.GenericFactory p) {
      projectControl = p;
    }

    public ChangeControl controlFor(Change change, CurrentUser user)
        throws NoSuchChangeException {
      final Project.NameKey projectKey = change.getProject();
      try {
        return projectControl.controlFor(projectKey, user).controlFor(change);
      } catch (NoSuchProjectException e) {
        throw new NoSuchChangeException(change.getId(), e);
      }
    }
  }

  public static class Factory {
    private final ProjectControl.Factory projectControl;
    private final Provider<ReviewDb> db;

    @Inject
    Factory(final ProjectControl.Factory p, final Provider<ReviewDb> d) {
      projectControl = p;
      db = d;
    }

    public ChangeControl controlFor(final Change.Id id)
        throws NoSuchChangeException {
      final Change change;
      try {
        change = db.get().changes().get(id);
        if (change == null) {
          throw new NoSuchChangeException(id);
        }
      } catch (OrmException e) {
        throw new NoSuchChangeException(id, e);
      }
      return controlFor(change);
    }

    public ChangeControl controlFor(final Change change)
        throws NoSuchChangeException {
      try {
        final Project.NameKey projectKey = change.getProject();
        return projectControl.validateFor(projectKey).controlFor(change);
      } catch (NoSuchProjectException e) {
        throw new NoSuchChangeException(change.getId(), e);
      }
    }

    public ChangeControl validateFor(final Change.Id id)
        throws NoSuchChangeException {
      return validate(controlFor(id));
    }

    public ChangeControl validateFor(final Change change)
        throws NoSuchChangeException {
      return validate(controlFor(change));
    }

    private static ChangeControl validate(final ChangeControl c)
        throws NoSuchChangeException {
      if (!c.isVisible()) {
        throw new NoSuchChangeException(c.getChange().getId());
      }
      return c;
    }
  }

  private final RefControl refControl;
  private final Change change;

  ChangeControl(final RefControl r, final Change c) {
    this.refControl = r;
    this.change = c;
  }

  public ChangeControl forUser(final CurrentUser who) {
    return new ChangeControl(getRefControl().forUser(who), getChange());
  }

  public RefControl getRefControl() {
    return refControl;
  }

  public CurrentUser getCurrentUser() {
    return getRefControl().getCurrentUser();
  }

  public ProjectControl getProjectControl() {
    return getRefControl().getProjectControl();
  }

  public Project getProject() {
    return getProjectControl().getProject();
  }

  public Change getChange() {
    return change;
  }

  /** Can this user see this change? */
  public boolean isVisible() {
    return getRefControl().isVisible();
  }

  /** Can this user abandon this change? */
  public boolean canAbandon() {
    return isOwner() // owner (aka creator) of the change can abandon
        || getRefControl().isOwner() // branch owner can abandon
        || getProjectControl().isOwner() // project owner can abandon
        || getCurrentUser().isAdministrator() // site administers are god
    ;
  }

  /** Can this user restore this change? */
  public boolean canRestore() {
    return canAbandon(); // Anyone who can abandon the change can restore it back
  }

  /** All value ranges of any allowed label permission. */
  public List<PermissionRange> getLabelRanges() {
    return getRefControl().getLabelRanges();
  }

  /** The range of permitted values associated with a label permission. */
  public PermissionRange getRange(String permission) {
    return getRefControl().getRange(permission);
  }

  /** Can this user add a patch set to this change? */
  public boolean canAddPatchSet() {
    return getRefControl().canUpload();
  }

  /** Is this user the owner of the change? */
  public boolean isOwner() {
    if (getCurrentUser() instanceof IdentifiedUser) {
      final IdentifiedUser i = (IdentifiedUser) getCurrentUser();
      return i.getAccountId().equals(change.getOwner());
    }
    return false;
  }

  /** @return true if the user is allowed to remove this reviewer. */
  public boolean canRemoveReviewer(PatchSetApproval approval) {
    if (getChange().getStatus().isOpen()) {
      // A user can always remove themselves.
      //
      if (getCurrentUser() instanceof IdentifiedUser) {
        final IdentifiedUser i = (IdentifiedUser) getCurrentUser();
        if (i.getAccountId().equals(approval.getAccountId())) {
          return true; // can remove self
        }
      }

      // The change owner may remove any zero or positive score.
      //
      if (isOwner() && 0 <= approval.getValue()) {
        return true;
      }

      // The branch owner, project owner, site admin can remove anyone.
      //
      if (getRefControl().isOwner() // branch owner
          || getProjectControl().isOwner() // project owner
          || getCurrentUser().isAdministrator()) {
        return true;
      }
    }

    return false;
  }

  /** @return {@link CanSubmitResult#OK}, or a result with an error message. */
  public CanSubmitResult canSubmit(final PatchSet.Id patchSetId) {
    if (change.getStatus().isClosed()) {
      return new CanSubmitResult("Change " + change.getId() + " is closed");
    }
    if (!patchSetId.equals(change.currentPatchSetId())) {
      return new CanSubmitResult("Patch set " + patchSetId + " is not current");
    }
    if (!getRefControl().canSubmit()) {
      return new CanSubmitResult("User does not have permission to submit");
    }
    if (!(getCurrentUser() instanceof IdentifiedUser)) {
      return new CanSubmitResult("User is not signed-in");
    }
    return CanSubmitResult.OK;
  }

  /** @return {@link CanSubmitResult#OK}, or a result with an error message. */
  public CanSubmitResult canSubmit(ReviewDb db, PatchSet.Id patchSetId) {
    CanSubmitResult result = canSubmit(patchSetId);
    if (result != CanSubmitResult.OK) {
      return result;
    }

    PrologEnvironment env;
    try {
      env = getProjectControl().getProjectState().newPrologEnvironment();
    } catch (CompileException err) {
      log.error("cannot consult rules.pl", err);
      return new CanSubmitResult("Error reading submit rule");
    }

    env.set(StoredValues.REVIEW_DB, db);
    env.set(StoredValues.CHANGE, change);
    env.set(StoredValues.PATCH_SET_ID, patchSetId);
    env.set(StoredValues.CHANGE_CONTROL, this);

    Term submitRule = env.once(
      "gerrit", "locate_submit_rule",
      new VariableTerm());
    if (submitRule == null) {
      log.error("Error in locate_submit_rule: no submit_rule found");
      return new CanSubmitResult("Error in finding submit rule");
    }

    List<Term> results = new ArrayList<Term>();
    try {
      for (Term[] template : env.all(
          "gerrit", "can_submit",
          submitRule,
          new VariableTerm())) {
        results.add(template[1]);
      }
    } catch (PrologException err) {
      log.error("PrologException calling " + submitRule, err);
      return new CanSubmitResult("Error in submit rule");
    }

    if (results.isEmpty()) {
      // This should never occur. A well written submit rule will always produce
      // at least one result informing the caller of the labels that are
      // required for this change to be submittable. Each label will indicate
      // whether or not that is actually possible given the permissions.
      log.error("Submit rule has no solution: " + submitRule);
      return new CanSubmitResult("Error in submit rule (no solution possible)");
    }

    // The last result produced will be an "ok(P)" format if submit is possible.
    // This is always true because can_submit (called above) will cut away all
    // choice points once a solution is found.
    Term last = results.get(results.size() - 1);
    if (last.isStructure() && 1 == last.arity() && "ok".equals(last.name())) {
      // Term solution = last.arg(0);
      return CanSubmitResult.OK;
    }

    // For now only process the first result. Later we can examine all of the
    // results and proposes different alternative paths to a submit solution.
    Term first = results.get(0);
    if (!first.isStructure() || 1 != first.arity() || !"not_ready".equals(first.name())) {
      log.error("Unexpected result from can_submit: " + first);
      return new CanSubmitResult("Error in submit rule");
    }

    Term submitRecord = first.arg(0);
    if (!submitRecord.isStructure()) {
      log.error("Invalid result from submit rule " + submitRule + ": " + submitRecord);
      return new CanSubmitResult("Error in submit rule");
    }

    for (Term state : ((StructureTerm) submitRecord).args()) {
      if (!state.isStructure() || 2 != state.arity() || !"label".equals(state.name())) {
        log.error("Invalid result from submit rule " + submitRule + ": " + submitRecord);
        return new CanSubmitResult("Invalid submit rule result");
      }

      String label = state.arg(0).name();
      Term status = state.arg(1);

      if ("ok".equals(status.name())) {
        continue;

      } else if ("reject".equals(status.name())) {
        return new CanSubmitResult("Submit blocked by " + label);

      } else if ("need".equals(status.name())) {
        if (status.isStructure() && status.arg(0).isInteger()) {
          IntegerTerm val = (IntegerTerm) status.arg(0);
          if (1 < val.intValue()) {
            label += "+" + val.intValue();
          }
        }
        return new CanSubmitResult("Requires " + label);

      } else if ("impossble".equals(status.name())) {
        return new CanSubmitResult("Requires " + label + " (check permissions)");

      } else {
        return new CanSubmitResult("Invalid submit rule result");
      }
    }

    return CanSubmitResult.OK;
  }
}