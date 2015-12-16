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

package com.google.gerrit.server.project;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Lists;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.common.data.SubmitTypeRecord;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.rules.PrologEnvironment;
import com.google.gerrit.rules.StoredValues;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;

import com.googlecode.prolog_cafe.exceptions.CompileException;
import com.googlecode.prolog_cafe.exceptions.ReductionLimitException;
import com.googlecode.prolog_cafe.lang.IntegerTerm;
import com.googlecode.prolog_cafe.lang.ListTerm;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.StructureTerm;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;
import com.googlecode.prolog_cafe.lang.VariableTerm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Evaluates a submit-like Prolog rule found in the rules.pl file of the current
 * project and filters the results through rules found in the parent projects,
 * all the way up to All-Projects.
 */
public class SubmitRuleEvaluator {
  private static final Logger log = LoggerFactory
      .getLogger(SubmitRuleEvaluator.class);

  private static final String DEFAULT_MSG =
      "Error evaluating project rules, check server log";

  public static List<SubmitRecord> defaultRuleError() {
    return createRuleError(DEFAULT_MSG);
  }

  public static List<SubmitRecord> createRuleError(String err) {
    SubmitRecord rec = new SubmitRecord();
    rec.status = SubmitRecord.Status.RULE_ERROR;
    rec.errorMessage = err;
    return Collections.singletonList(rec);
  }

  public static SubmitTypeRecord defaultTypeError() {
    return createTypeError(DEFAULT_MSG);
  }

  public static SubmitTypeRecord createTypeError(String err) {
    SubmitTypeRecord rec = new SubmitTypeRecord();
    rec.status = SubmitTypeRecord.Status.RULE_ERROR;
    rec.errorMessage = err;
    return rec;
  }

  /**
   * Exception thrown when the label term of a submit record
   * unexpectedly didn't contain a user term.
   */
  private static class UserTermExpected extends Exception {
    private static final long serialVersionUID = 1L;

    public UserTermExpected(SubmitRecord.Label label) {
      super(String.format("A label with the status %s must contain a user.",
          label.toString()));
    }
  }

  private final ChangeData cd;
  private final ChangeControl control;

  private PatchSet patchSet;
  private boolean fastEvalLabels;
  private boolean allowDraft;
  private boolean allowClosed;
  private boolean skipFilters;
  private String rule;
  private boolean logErrors = true;
  private long reductionsConsumed;

  private Term submitRule;

  public SubmitRuleEvaluator(ChangeData cd) throws OrmException {
    this.cd = cd;
    this.control = cd.changeControl();
  }

  /**
   * @param ps patch set of the change to evaluate. If not set, the current
   * patch set will be loaded from {@link #evaluate()} or {@link
   * #getSubmitType}.
   * @return this
   */
  public SubmitRuleEvaluator setPatchSet(PatchSet ps) {
    checkArgument(ps.getId().getParentKey().equals(cd.getId()),
        "Patch set %s does not match change %s", ps.getId(), cd.getId());
    patchSet = ps;
    return this;
  }

  /**
   * @param fast if true, infer label information from rules rather than reading
   *     from project config.
   * @return this
   */
  public SubmitRuleEvaluator setFastEvalLabels(boolean fast) {
    fastEvalLabels = fast;
    return this;
  }

  /**
   * @param allow whether to allow {@link #evaluate()} on closed changes.
   * @return this
   */
  public SubmitRuleEvaluator setAllowClosed(boolean allow) {
    allowClosed = allow;
    return this;
  }

  /**
   * @param allow whether to allow {@link #evaluate()} on draft changes.
   * @return this
   */
  public SubmitRuleEvaluator setAllowDraft(boolean allow) {
    allowDraft = allow;
    return this;
  }

  /**
   * @param skip if true, submit filter will not be applied.
   * @return this
   */
  public SubmitRuleEvaluator setSkipSubmitFilters(boolean skip) {
    skipFilters = skip;
    return this;
  }

  /**
   * @param rule custom rule to use, or null to use refs/meta/config:rules.pl.
   * @return this
   */
  public SubmitRuleEvaluator setRule(@Nullable String rule) {
    this.rule = rule;
    return this;
  }

  /**
   * @param log whether to log error messages in addition to returning error
   *     records. If true, error record messages will be less descriptive.
   */
  public SubmitRuleEvaluator setLogErrors(boolean log) {
    logErrors = log;
    return this;
  }

  /** @return Prolog reductions consumed during evaluation. */
  public long getReductionsConsumed() {
    return reductionsConsumed;
  }

  /**
   * Evaluate the submit rules.
   *
   * @return List of {@link SubmitRecord} objects returned from the evaluated
   *     rules, including any errors.
   */
  public List<SubmitRecord> evaluate() {
    Change c = control.getChange();
    if (!allowClosed && c.getStatus().isClosed()) {
      SubmitRecord rec = new SubmitRecord();
      rec.status = SubmitRecord.Status.CLOSED;
      return Collections.singletonList(rec);
    }
    if (!allowDraft) {
      if (c.getStatus() == Change.Status.DRAFT) {
        return cannotSubmitDraft();
      }
      try {
        initPatchSet();
      } catch (OrmException e) {
        return ruleError("Error looking up patch set "
            + control.getChange().currentPatchSetId());
      }
      if (patchSet.isDraft()) {
        return cannotSubmitDraft();
      }
    }

    List<Term> results;
    try {
      results = evaluateImpl("locate_submit_rule", "can_submit",
          "locate_submit_filter", "filter_submit_results",
          control.getUser());
    } catch (RuleEvalException e) {
      return ruleError(e.getMessage(), e);
    }

    if (results.isEmpty()) {
      // This should never occur. A well written submit rule will always produce
      // at least one result informing the caller of the labels that are
      // required for this change to be submittable. Each label will indicate
      // whether or not that is actually possible given the permissions.
      return ruleError(String.format("Submit rule '%s' for change %s of %s has "
            + "no solution.", getSubmitRuleName(), cd.getId(),
            getProjectName()));
    }

    return resultsToSubmitRecord(getSubmitRule(), results);
  }

  private List<SubmitRecord> cannotSubmitDraft() {
    try {
      if (!control.isDraftVisible(cd.db(), cd)) {
        return createRuleError("Patch set " + patchSet.getId() + " not found");
      } else if (patchSet.isDraft()) {
        return createRuleError("Cannot submit draft patch sets");
      } else {
        return createRuleError("Cannot submit draft changes");
      }
    } catch (OrmException err) {
      String msg = "Cannot check visibility of patch set " + patchSet.getId();
      log.error(msg, err);
      return createRuleError(msg);
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
  private List<SubmitRecord> resultsToSubmitRecord(
      Term submitRule, List<Term> results) {
    List<SubmitRecord> out = new ArrayList<>(results.size());
    for (int resultIdx = results.size() - 1; 0 <= resultIdx; resultIdx--) {
      Term submitRecord = results.get(resultIdx);
      SubmitRecord rec = new SubmitRecord();
      out.add(rec);

      if (!(submitRecord instanceof StructureTerm) || 1 != submitRecord.arity()) {
        return invalidResult(submitRule, submitRecord);
      }

      if ("ok".equals(submitRecord.name())) {
        rec.status = SubmitRecord.Status.OK;

      } else if ("not_ready".equals(submitRecord.name())) {
        rec.status = SubmitRecord.Status.NOT_READY;

      } else {
        return invalidResult(submitRule, submitRecord);
      }

      // Unpack the one argument. This should also be a structure with one
      // argument per label that needs to be reported on to the caller.
      //
      submitRecord = submitRecord.arg(0);

      if (!(submitRecord instanceof StructureTerm)) {
        return invalidResult(submitRule, submitRecord);
      }

      rec.labels = new ArrayList<>(submitRecord.arity());

      for (Term state : ((StructureTerm) submitRecord).args()) {
        if (!(state instanceof StructureTerm)
            || 2 != state.arity()
            || !"label".equals(state.name())) {
          return invalidResult(submitRule, submitRecord);
        }

        SubmitRecord.Label lbl = new SubmitRecord.Label();
        rec.labels.add(lbl);

        lbl.label = state.arg(0).name();
        Term status = state.arg(1);

        try {
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
            return invalidResult(submitRule, submitRecord);
          }
        } catch (UserTermExpected e) {
          return invalidResult(submitRule, submitRecord, e.getMessage());
        }
      }

      if (rec.status == SubmitRecord.Status.OK) {
        break;
      }
    }
    Collections.reverse(out);

    return out;
  }

  private List<SubmitRecord> invalidResult(Term rule, Term record, String reason) {
    return ruleError(String.format("Submit rule %s for change %s of %s output "
        + "invalid result: %s%s", rule, cd.getId(), getProjectName(), record,
        (reason == null ? "" : ". Reason: " + reason)));
  }

  private List<SubmitRecord> invalidResult(Term rule, Term record) {
    return invalidResult(rule, record, null);
  }

  private List<SubmitRecord> ruleError(String err) {
    return ruleError(err, null);
  }

  private List<SubmitRecord> ruleError(String err, Exception e) {
    if (logErrors) {
      if (e == null) {
        log.error(err);
      } else {
        log.error(err, e);
      }
      return defaultRuleError();
    } else {
      return createRuleError(err);
    }
  }

  /**
   * Evaluate the submit type rules to get the submit type.
   *
   * @return record from the evaluated rules.
   */
  public SubmitTypeRecord getSubmitType() {
    try {
      initPatchSet();
    } catch (OrmException e) {
      return typeError("Error looking up patch set "
          + control.getChange().currentPatchSetId());
    }

    try {
      if (control.getChange().getStatus() == Change.Status.DRAFT
          && !control.isDraftVisible(cd.db(), cd)) {
        return createTypeError("Patch set " + patchSet.getId() + " not found");
      }
      if (patchSet.isDraft() && !control.isDraftVisible(cd.db(), cd)) {
        return createTypeError("Patch set " + patchSet.getId() + " not found");
      }
    } catch (OrmException err) {
      String msg = "Cannot read patch set " + patchSet.getId();
      log.error(msg, err);
      return createTypeError(msg);
    }

    List<Term> results;
    try {
      results = evaluateImpl("locate_submit_type", "get_submit_type",
          "locate_submit_type_filter", "filter_submit_type_results",
          // Do not include current user in submit type evaluation. This is used
          // for mergeability checks, which are stored persistently and so must
          // have a consistent view of the submit type.
          null);
    } catch (RuleEvalException e) {
      return typeError(e.getMessage(), e);
    }

    if (results.isEmpty()) {
      // Should never occur for a well written rule
      return typeError("Submit rule '" + getSubmitRuleName() + "' for change "
          + cd.getId() + " of " + getProjectName() + " has no solution.");
    }

    Term typeTerm = results.get(0);
    if (!(typeTerm instanceof SymbolTerm)) {
      return typeError("Submit rule '" + getSubmitRuleName() + "' for change "
          + cd.getId() + " of " + getProjectName()
          + " did not return a symbol.");
    }

    String typeName = ((SymbolTerm) typeTerm).name();
    try {
      return SubmitTypeRecord.OK(
          SubmitType.valueOf(typeName.toUpperCase()));
    } catch (IllegalArgumentException e) {
      return typeError("Submit type rule " + getSubmitRule() + " for change "
          + cd.getId() + " of " + getProjectName() + " output invalid result: "
          + typeName);
    }
  }

  private SubmitTypeRecord typeError(String err) {
    return typeError(err, null);
  }

  private SubmitTypeRecord typeError(String err, Exception e) {
    if (logErrors) {
      if (e == null) {
        log.error(err);
      } else {
        log.error(err, e);
      }
      return defaultTypeError();
    } else {
      return createTypeError(err);
    }
  }

  private List<Term> evaluateImpl(
      String userRuleLocatorName,
      String userRuleWrapperName,
      String filterRuleLocatorName,
      String filterRuleWrapperName,
      CurrentUser user) throws RuleEvalException {
    PrologEnvironment env = getPrologEnvironment(user);
    try {
      Term sr = env.once("gerrit", userRuleLocatorName, new VariableTerm());
      if (fastEvalLabels) {
        env.once("gerrit", "assume_range_from_label");
      }

      List<Term> results = new ArrayList<>();
      try {
        for (Term[] template : env.all("gerrit", userRuleWrapperName, sr,
              new VariableTerm())) {
          results.add(template[1]);
        }
      } catch (ReductionLimitException err) {
        throw new RuleEvalException(String.format(
            "%s on change %d of %s",
            err.getMessage(), cd.getId().get(), getProjectName()));
      } catch (RuntimeException err) {
        throw new RuleEvalException(String.format(
            "Exception calling %s on change %d of %s",
            sr, cd.getId().get(), getProjectName()), err);
      } finally {
        reductionsConsumed = env.getReductions();
      }

      Term resultsTerm = toListTerm(results);
      if (!skipFilters) {
        resultsTerm = runSubmitFilters(
            resultsTerm, env, filterRuleLocatorName, filterRuleWrapperName);
      }
      List<Term> r;
      if (resultsTerm instanceof ListTerm) {
        r = Lists.newArrayList();
        for (Term t = resultsTerm; t instanceof ListTerm;) {
          ListTerm l = (ListTerm) t;
          r.add(l.car().dereference());
          t = l.cdr().dereference();
        }
      } else {
        r = Collections.emptyList();
      }
      submitRule = sr;
      return r;
    } finally {
      env.close();
    }
  }

  private PrologEnvironment getPrologEnvironment(CurrentUser user)
      throws RuleEvalException {
    ProjectState projectState = control.getProjectControl().getProjectState();
    PrologEnvironment env;
    try {
      if (rule == null) {
        env = projectState.newPrologEnvironment();
      } else {
        env = projectState.newPrologEnvironment("stdin", new StringReader(rule));
      }
    } catch (CompileException err) {
      String msg;
      if (rule == null && control.getProjectControl().isOwner()) {
        msg = String.format(
            "Cannot load rules.pl for %s: %s",
            getProjectName(), err.getMessage());
      } else if (rule != null) {
        msg = err.getMessage();
      } else {
        msg = String.format("Cannot load rules.pl for %s", getProjectName());
      }
      throw new RuleEvalException(msg, err);
    }
    env.set(StoredValues.REVIEW_DB, cd.db());
    env.set(StoredValues.CHANGE_DATA, cd);
    env.set(StoredValues.CHANGE_CONTROL, control);
    if (user != null) {
      env.set(StoredValues.CURRENT_USER, user);
    }
    return env;
  }

  private Term runSubmitFilters(Term results, PrologEnvironment env,
      String filterRuleLocatorName, String filterRuleWrapperName)
      throws RuleEvalException {
    ProjectState projectState = control.getProjectControl().getProjectState();
    PrologEnvironment childEnv = env;
    for (ProjectState parentState : projectState.parents()) {
      PrologEnvironment parentEnv;
      try {
        parentEnv = parentState.newPrologEnvironment();
      } catch (CompileException err) {
        throw new RuleEvalException("Cannot consult rules.pl for "
            + parentState.getProject().getName(), err);
      }

      parentEnv.copyStoredValues(childEnv);
      Term filterRule =
          parentEnv.once("gerrit", filterRuleLocatorName, new VariableTerm());
      try {
        if (fastEvalLabels) {
          env.once("gerrit", "assume_range_from_label");
        }

        Term[] template =
            parentEnv.once("gerrit", filterRuleWrapperName, filterRule,
                results, new VariableTerm());
        results = template[2];
      } catch (ReductionLimitException err) {
        throw new RuleEvalException(String.format(
            "%s on change %d of %s",
            err.getMessage(), cd.getId().get(), parentState.getProject().getName()));
      } catch (RuntimeException err) {
        throw new RuleEvalException(String.format(
            "Exception calling %s on change %d of %s",
            filterRule, cd.getId().get(), parentState.getProject().getName()), err);
      } finally {
        reductionsConsumed += env.getReductions();
      }
      childEnv = parentEnv;
    }
    return results;
  }

  private static Term toListTerm(List<Term> terms) {
    Term list = Prolog.Nil;
    for (int i = terms.size() - 1; i >= 0; i--) {
      list = new ListTerm(terms.get(i), list);
    }
    return list;
  }

  private void appliedBy(SubmitRecord.Label label, Term status)
      throws UserTermExpected {
    if (status instanceof StructureTerm && status.arity() == 1) {
      Term who = status.arg(0);
      if (isUser(who)) {
        label.appliedBy = new Account.Id(((IntegerTerm) who.arg(0)).intValue());
      } else {
        throw new UserTermExpected(label);
      }
    }
  }

  private static boolean isUser(Term who) {
    return who instanceof StructureTerm
        && who.arity() == 1
        && who.name().equals("user")
        && who.arg(0) instanceof IntegerTerm;
  }

  public Term getSubmitRule() {
    checkState(submitRule != null, "getSubmitRule() invalid before evaluation");
    return submitRule;
  }

  public String getSubmitRuleName() {
    return submitRule != null ? submitRule.toString() : "<unknown rule>";
  }

  private void initPatchSet() throws OrmException {
    if (patchSet == null) {
      patchSet = cd.currentPatchSet();
    }
  }

  private String getProjectName() {
    return control.getProjectControl().getProjectState().getProject().getName();
  }
}
