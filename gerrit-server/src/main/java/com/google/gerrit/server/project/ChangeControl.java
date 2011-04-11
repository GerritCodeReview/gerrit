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
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.Account;
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
import com.googlecode.prolog_cafe.lang.ListTerm;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.PrologException;
import com.googlecode.prolog_cafe.lang.StructureTerm;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;
import com.googlecode.prolog_cafe.lang.VariableTerm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;


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
        || getCurrentUser().getCapabilities().canAdministrateServer() // site administers are god
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

  /** Can this user add and/or delete labels to this change? */
  public boolean canEditChangeLabels() {
    return getRefControl().canLabelChanges();
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
          || getCurrentUser().getCapabilities().canAdministrateServer()) {
        return true;
      }
    }

    return false;
  }

  public List<SubmitRecord> canSubmit(ReviewDb db, PatchSet.Id patchSetId) {
    if (change.getStatus().isClosed()) {
      SubmitRecord rec = new SubmitRecord();
      rec.status = SubmitRecord.Status.CLOSED;
      return Collections.singletonList(rec);
    }

    if (!patchSetId.equals(change.currentPatchSetId())) {
      return ruleError("Patch set " + patchSetId + " is not current");
    }

    List<Term> results = new ArrayList<Term>();
    Term submitRule;
    ProjectState projectState = getProjectControl().getProjectState();
    PrologEnvironment env;

    try {
      env = projectState.newPrologEnvironment();
    } catch (CompileException err) {
      return logRuleError("Cannot consult rules.pl for "
          + getProject().getName(), err);
    }

    try {
      env.set(StoredValues.REVIEW_DB, db);
      env.set(StoredValues.CHANGE, change);
      env.set(StoredValues.PATCH_SET_ID, patchSetId);
      env.set(StoredValues.CHANGE_CONTROL, this);

      submitRule = env.once(
        "gerrit", "locate_submit_rule",
        new VariableTerm());
      if (submitRule == null) {
        return logRuleError("No user:submit_rule found for "
            + getProject().getName());
      }

      try {
        for (Term[] template : env.all(
            "gerrit", "can_submit",
            submitRule,
            new VariableTerm())) {
          results.add(template[1]);
        }
      } catch (PrologException err) {
        return logRuleError("Exception calling " + submitRule + " on change "
            + change.getId() + " of " + getProject().getName(), err);
      } catch (RuntimeException err) {
        return logRuleError("Exception calling " + submitRule + " on change "
            + change.getId() + " of " + getProject().getName(), err);
      }

      ProjectState parentState = projectState.getParentState();
      PrologEnvironment childEnv = env;
      Set<Project.NameKey> projectsSeen = new HashSet<Project.NameKey>();
      projectsSeen.add(getProject().getNameKey());

      while (parentState != null) {
        if (!projectsSeen.add(parentState.getProject().getNameKey())) {
          //parent has been seen before, stop walk up inheritance tree
          break;
        }
        PrologEnvironment parentEnv;
        try {
          parentEnv = parentState.newPrologEnvironment();
        } catch (CompileException err) {
          return logRuleError("Cannot consult rules.pl for "
              + parentState.getProject().getName(), err);
        }

        parentEnv.copyStoredValues(childEnv);
        Term filterRule =
            parentEnv.once("gerrit", "locate_submit_filter", new VariableTerm());
        if (filterRule != null) {
          try {
            Term resultsTerm = toListTerm(results);
            results.clear();
            Term[] template = parentEnv.once(
                "gerrit", "filter_submit_results",
                filterRule,
                resultsTerm,
                new VariableTerm());
            results.addAll(((ListTerm) template[2]).toJava());
          } catch (PrologException err) {
            return logRuleError("Exception calling " + filterRule + " on change "
                + change.getId() + " of " + parentState.getProject().getName(), err);
          } catch (RuntimeException err) {
            return logRuleError("Exception calling " + filterRule + " on change "
                + change.getId() + " of " + parentState.getProject().getName(), err);
          }
        }

        parentState = parentState.getParentState();
        childEnv = parentEnv;
      }
    } finally {
      env.close();
    }

    if (results.isEmpty()) {
      // This should never occur. A well written submit rule will always produce
      // at least one result informing the caller of the labels that are
      // required for this change to be submittable. Each label will indicate
      // whether or not that is actually possible given the permissions.
      log.error("Submit rule " + submitRule + " for change " + change.getId()
          + " of " + getProject().getName() + " has no solution.");
      return ruleError("Project submit rule has no solution");
    }

    // Convert the results from Prolog Cafe's format to Gerrit's common format.
    // can_submit/1 terminates when an ok(P) record is found. Therefore walk
    // the results backwards, using only that ok(P) record if it exists. This
    // skips partial results that occur early in the output. Later after the loop
    // the out collection is reversed to restore it to the original ordering.
    //
    List<SubmitRecord> out = new ArrayList<SubmitRecord>(results.size());
    for (int resultIdx = results.size() - 1; 0 <= resultIdx; resultIdx--) {
      Term submitRecord = results.get(resultIdx);
      SubmitRecord rec = new SubmitRecord();
      out.add(rec);

      if (!submitRecord.isStructure() || 1 != submitRecord.arity()) {
        return logInvalidResult(submitRule, submitRecord);
      }

      if ("ok".equals(submitRecord.name())) {
        rec.status = SubmitRecord.Status.OK;

      } else if ("not_ready".equals(submitRecord.name())) {
        rec.status = SubmitRecord.Status.NOT_READY;

      } else {
        return logInvalidResult(submitRule, submitRecord);
      }

      // Unpack the one argument. This should also be a structure with one
      // argument per label that needs to be reported on to the caller.
      //
      submitRecord = submitRecord.arg(0);

      if (!submitRecord.isStructure()) {
        return logInvalidResult(submitRule, submitRecord);
      }

      rec.labels = new ArrayList<SubmitRecord.Label> (submitRecord.arity());

      for (Term state : ((StructureTerm) submitRecord).args()) {
        if (!state.isStructure() || 2 != state.arity() || !"label".equals(state.name())) {
          return logInvalidResult(submitRule, submitRecord);
        }

        SubmitRecord.Label lbl = new SubmitRecord.Label();
        rec.labels.add(lbl);

        lbl.label = state.arg(0).name();
        Term status = state.arg(1);

        if ("ok".equals(status.name())) {
          lbl.status = SubmitRecord.Label.Status.OK;
          appliedBy(lbl, status);

        } else if ("reject".equals(status.name())) {
          lbl.status = SubmitRecord.Label.Status.REJECT;
          appliedBy(lbl, status);

        } else if ("need".equals(status.name())) {
          lbl.status = SubmitRecord.Label.Status.NEED;

        } else if ("impossible".equals(status.name())) {
          lbl.status = SubmitRecord.Label.Status.IMPOSSIBLE;

        } else {
          return logInvalidResult(submitRule, submitRecord);
        }
      }

      if (rec.status == SubmitRecord.Status.OK) {
        break;
      }
    }
    Collections.reverse(out);

    return out;
  }

  private List<SubmitRecord> logInvalidResult(Term rule, Term record) {
    return logRuleError("Submit rule " + rule + " for change " + change.getId()
        + " of " + getProject().getName() + " output invalid result: " + record);
  }

  private List<SubmitRecord> logRuleError(String err, Exception e) {
    log.error(err, e);
    return ruleError("Error evaluating project rules, check server log");
  }

  private List<SubmitRecord> logRuleError(String err) {
    log.error(err);
    return ruleError("Error evaluating project rules, check server log");
  }

  private List<SubmitRecord> ruleError(String err) {
    SubmitRecord rec = new SubmitRecord();
    rec.status = SubmitRecord.Status.RULE_ERROR;
    rec.errorMessage = err;
    return Collections.singletonList(rec);
  }

  private void appliedBy(SubmitRecord.Label label, Term status) {
    if (status.isStructure() && status.arity() == 1) {
      Term who = status.arg(0);
      if (isUser(who)) {
        label.appliedBy = new Account.Id(((IntegerTerm) who.arg(0)).intValue());
      }
    }
  }

  private static boolean isUser(Term who) {
    return who.isStructure()
        && who.arity() == 1
        && who.name().equals("user")
        && who.arg(0).isInteger();
  }

  private static Term toListTerm(List<Term> terms) {
    Term list = Prolog.Nil;
    for (int i = terms.size() - 1; i >= 0; i--) {
      list = new ListTerm(terms.get(i), list);
    }
    return list;
  }
}