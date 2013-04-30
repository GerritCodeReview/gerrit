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

import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.common.data.SubmitTypeRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.util.Providers;

import com.googlecode.prolog_cafe.lang.IntegerTerm;
import com.googlecode.prolog_cafe.lang.ListTerm;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.StructureTerm;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;


/** Access control management for a user accessing a single change. */
public class ChangeControl {
  private static final Logger log = LoggerFactory
      .getLogger(ChangeControl.class);

  public static class GenericFactory {
    private final ProjectControl.GenericFactory projectControl;
    private final Provider<ReviewDb> db;

    @Inject
    GenericFactory(ProjectControl.GenericFactory p, Provider<ReviewDb> d) {
      projectControl = p;
      db = d;
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

    public ChangeControl controlFor(Change.Id id, CurrentUser user)
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
      return controlFor(change, user);
    }

    public ChangeControl validateFor(Change.Id id, CurrentUser user)
        throws NoSuchChangeException, OrmException {
      ChangeControl c = controlFor(id, user);
      if (!c.isVisible(db.get())) {
        throw new NoSuchChangeException(c.getChange().getId());
      }
      return c;
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
        throws NoSuchChangeException, OrmException {
      return validate(controlFor(id), db.get());
    }

    public ChangeControl validateFor(final Change change)
        throws NoSuchChangeException, OrmException {
      return validate(controlFor(change), db.get());
    }

    private static ChangeControl validate(final ChangeControl c, final ReviewDb db)
        throws NoSuchChangeException, OrmException{
      if (!c.isVisible(db)) {
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
  public boolean isVisible(ReviewDb db) throws OrmException {
    if (change.getStatus() == Change.Status.DRAFT && !isDraftVisible(db, null)) {
      return false;
    }
    return isRefVisible();
  }

  /** Can the user see this change? Does not account for draft status */
  public boolean isRefVisible() {
    return getRefControl().isVisible();
  }

  /** Can this user see the given patchset? */
  public boolean isPatchVisible(PatchSet ps, ReviewDb db) throws OrmException {
    if (ps.isDraft() && !isDraftVisible(db, null)) {
      return false;
    }
    return isVisible(db);
  }

  /** Can this user abandon this change? */
  public boolean canAbandon() {
    return isOwner() // owner (aka creator) of the change can abandon
        || getRefControl().isOwner() // branch owner can abandon
        || getProjectControl().isOwner() // project owner can abandon
        || getCurrentUser().getCapabilities().canAdministrateServer() // site administers are god
        || getRefControl().canAbandon() // user can abandon a specific ref
    ;
  }

  /** Can this user publish this draft change or any draft patch set of this change? */
  public boolean canPublish(final ReviewDb db) throws OrmException {
    return (isOwner() || getRefControl().canPublishDrafts())
        && isVisible(db);
  }

  /** Can this user delete this draft change or any draft patch set of this change? */
  public boolean canDeleteDraft(final ReviewDb db) throws OrmException {
    return (isOwner() || getRefControl().canDeleteDrafts())
        && isVisible(db);
  }

  /** Can this user rebase this change? */
  public boolean canRebase() {
    return isOwner() || getRefControl().canSubmit()
        || getRefControl().canRebase();
  }

  /** Can this user restore this change? */
  public boolean canRestore() {
    return canAbandon() // Anyone who can abandon the change can restore it back
        && getRefControl().canUpload(); // as long as you can upload too
  }

  /** All available label types for this project. */
  public LabelTypes getLabelTypes() {
    return getProjectControl().getLabelTypes();
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

  /** Is this user a reviewer for the change? */
  public boolean isReviewer(ReviewDb db) throws OrmException {
    return isReviewer(db, null);
  }

  /** Is this user a reviewer for the change? */
  public boolean isReviewer(ReviewDb db, @Nullable ChangeData cd)
      throws OrmException {
    if (getCurrentUser() instanceof IdentifiedUser) {
      final IdentifiedUser user = (IdentifiedUser) getCurrentUser();
      Iterable<PatchSetApproval> results;
      if (cd != null) {
        results = cd.currentApprovals(Providers.of(db));
      } else {
        results = db.patchSetApprovals().byChange(change.getId());
      }
      for (PatchSetApproval approval : results) {
        if (user.getAccountId().equals(approval.getAccountId())) {
          return true;
        }
      }
    }
    return false;
  }

  /** @return true if the user is allowed to remove this reviewer. */
  public boolean canRemoveReviewer(PatchSetApproval approval) {
    return canRemoveReviewer(approval.getAccountId(), approval.getValue());
  }

  public boolean canRemoveReviewer(Account.Id reviewer, int value) {
    if (getChange().getStatus().isOpen()) {
      // A user can always remove themselves.
      //
      if (getCurrentUser() instanceof IdentifiedUser) {
        final IdentifiedUser i = (IdentifiedUser) getCurrentUser();
        if (i.getAccountId().equals(reviewer)) {
          return true; // can remove self
        }
      }

      // The change owner may remove any zero or positive score.
      //
      if (isOwner() && 0 <= value) {
        return true;
      }

      // Users with the remove reviewer permission, the branch owner, project
      // owner and site admin can remove anyone
      if (getRefControl().canRemoveReviewer() // has removal permissions
          || getRefControl().isOwner() // branch owner
          || getProjectControl().isOwner() // project owner
          || getCurrentUser().getCapabilities().canAdministrateServer()) {
        return true;
      }
    }

    return false;
  }

  /** Can this user edit the topic name? */
  public boolean canEditTopicName() {
    if (change.getStatus().isOpen()) {
      return isOwner() // owner (aka creator) of the change can edit topic
          || getRefControl().isOwner() // branch owner can edit topic
          || getProjectControl().isOwner() // project owner can edit topic
          || getCurrentUser().getCapabilities().canAdministrateServer() // site administers are god
          || getRefControl().canEditTopicName() // user can edit topic on a specific ref
      ;
    } else {
      return getRefControl().canForceEditTopicName();
    }
  }

  public List<SubmitRecord> getSubmitRecords(ReviewDb db, PatchSet patchSet) {
    return canSubmit(db, patchSet, null, false, true, false);
  }

  public boolean canSubmit() {
    return getRefControl().canSubmit();
  }

  public List<SubmitRecord> canSubmit(ReviewDb db, PatchSet patchSet) {
    return canSubmit(db, patchSet, null, false, false, false);
  }

  public List<SubmitRecord> canSubmit(ReviewDb db, PatchSet patchSet,
      @Nullable ChangeData cd, boolean fastEvalLabels, boolean allowClosed,
      boolean allowDraft) {
    if (!allowClosed && change.getStatus().isClosed()) {
      SubmitRecord rec = new SubmitRecord();
      rec.status = SubmitRecord.Status.CLOSED;
      return Collections.singletonList(rec);
    }

    if (!patchSet.getId().equals(change.currentPatchSetId())) {
      return ruleError("Patch set " + patchSet.getPatchSetId() + " is not current");
    }

    if ((change.getStatus() == Change.Status.DRAFT || patchSet.isDraft())
        && !allowDraft) {
      return cannotSubmitDraft(db, patchSet, cd);
    }

    List<Term> results;
    SubmitRuleEvaluator evaluator;
    try {
      evaluator = new SubmitRuleEvaluator(db, patchSet,
          getProjectControl(),
          this, change, cd,
          fastEvalLabels,
          "locate_submit_rule", "can_submit",
          "locate_submit_filter", "filter_submit_results");
      results = evaluator.evaluate();
    } catch (RuleEvalException e) {
      return logRuleError(e.getMessage(), e);
    }

    if (results.isEmpty()) {
      // This should never occur. A well written submit rule will always produce
      // at least one result informing the caller of the labels that are
      // required for this change to be submittable. Each label will indicate
      // whether or not that is actually possible given the permissions.
      log.error("Submit rule '" + evaluator.getSubmitRule() + "' for change "
          + change.getId() + " of " + getProject().getName()
          + " has no solution.");
      return ruleError("Project submit rule has no solution");
    }

    return resultsToSubmitRecord(evaluator.getSubmitRule(), results);
  }

  private List<SubmitRecord> cannotSubmitDraft(ReviewDb db, PatchSet patchSet,
      ChangeData cd) {
    try {
      if (!isDraftVisible(db, cd)) {
        return ruleError("Patch set " + patchSet.getPatchSetId() + " not found");
      } else if (patchSet.isDraft()) {
        return ruleError("Cannot submit draft patch sets");
      } else {
        return ruleError("Cannot submit draft changes");
      }
    } catch (OrmException err) {
      return logRuleError("Cannot read patch set " + patchSet.getId(), err);
    }
  }

  /**
   * Convert the results from Prolog Cafe's format to Gerrit's common format.
   *
   * can_submit/1 terminates when an ok(P) record is found. Therefore walk
   * the results backwards, using only that ok(P) record if it exists. This
   * skips partial results that occur early in the output. Later after the loop
   * the out collection is reversed to restore it to the original ordering.
   */
  public List<SubmitRecord> resultsToSubmitRecord(Term submitRule, List<Term> results) {
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

        } else if ("may".equals(status.name())) {
          lbl.status = SubmitRecord.Label.Status.MAY;

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

  public SubmitTypeRecord getSubmitTypeRecord(ReviewDb db, PatchSet patchSet) {
    return getSubmitTypeRecord(db, patchSet, null);
  }

  public SubmitTypeRecord getSubmitTypeRecord(ReviewDb db, PatchSet patchSet,
      @Nullable ChangeData cd) {
    try {
      if (change.getStatus() == Change.Status.DRAFT && !isDraftVisible(db, cd)) {
        return typeRuleError("Patch set " + patchSet.getPatchSetId()
            + " not found");
      }
      if (patchSet.isDraft() && !isDraftVisible(db, cd)) {
        return typeRuleError("Patch set " + patchSet.getPatchSetId()
            + " not found");
      }
    } catch (OrmException err) {
      return logTypeRuleError("Cannot read patch set " + patchSet.getId(),
          err);
    }

    List<Term> results;
    SubmitRuleEvaluator evaluator;
    try {
      evaluator = new SubmitRuleEvaluator(db, patchSet,
          getProjectControl(), this, change, cd,
          false,
          "locate_submit_type", "get_submit_type",
          "locate_submit_type_filter", "filter_submit_type_results");
      results = evaluator.evaluate();
    } catch (RuleEvalException e) {
      return logTypeRuleError(e.getMessage(), e);
    }

    if (results.isEmpty()) {
      // Should never occur for a well written rule
      log.error("Submit rule '" + evaluator.getSubmitRule() + "' for change "
          + change.getId() + " of " + getProject().getName()
          + " has no solution.");
      return typeRuleError("Project submit rule has no solution");
    }

    Term typeTerm = results.get(0);
    if (!typeTerm.isSymbol()) {
      log.error("Submit rule '" + evaluator.getSubmitRule() + "' for change "
          + change.getId() + " of " + getProject().getName()
          + " did not return a symbol.");
      return typeRuleError("Project submit rule has invalid solution");
    }

    String typeName = ((SymbolTerm)typeTerm).name();
    try {
      return SubmitTypeRecord.OK(
          Project.SubmitType.valueOf(typeName.toUpperCase()));
    } catch (IllegalArgumentException e) {
      return logInvalidType(evaluator.getSubmitRule(), typeName);
    }
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

  private SubmitTypeRecord logInvalidType(Term rule, String record) {
    return logTypeRuleError("Submit type rule " + rule + " for change "
        + change.getId() + " of " + getProject().getName()
        + " output invalid result: " + record);
  }

  private SubmitTypeRecord logTypeRuleError(String err, Exception e) {
    log.error(err, e);
    return typeRuleError("Error evaluating project type rules, check server log");
  }

  private SubmitTypeRecord logTypeRuleError(String err) {
    log.error(err);
    return typeRuleError("Error evaluating project type rules, check server log");
  }

  private SubmitTypeRecord typeRuleError(String err) {
    SubmitTypeRecord rec = new SubmitTypeRecord();
    rec.status = SubmitTypeRecord.Status.RULE_ERROR;
    rec.errorMessage = err;
    return rec;
  }

  private void appliedBy(SubmitRecord.Label label, Term status) {
    if (status.isStructure() && status.arity() == 1) {
      Term who = status.arg(0);
      if (isUser(who)) {
        label.appliedBy = new Account.Id(((IntegerTerm) who.arg(0)).intValue());
      }
    }
  }

  private boolean isDraftVisible(ReviewDb db, ChangeData cd)
      throws OrmException {
    return isOwner() || isReviewer(db, cd) || getRefControl().canViewDrafts();
  }

  private static boolean isUser(Term who) {
    return who.isStructure()
        && who.arity() == 1
        && who.name().equals("user")
        && who.arg(0).isInteger();
  }

  public static Term toListTerm(List<Term> terms) {
    Term list = Prolog.Nil;
    for (int i = terms.size() - 1; i >= 0; i--) {
      list = new ListTerm(terms.get(i), list);
    }
    return list;
  }
}
