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

package com.google.gerrit.server.project.rules;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Lists;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.common.data.SubmitTypeRecord;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.rules.PrologEnvironment;
import com.google.gerrit.rules.RulesCache;
import com.google.gerrit.rules.StoredValues;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.RuleEvalException;
import com.google.gerrit.server.project.SubmitRuleFlags;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import com.googlecode.prolog_cafe.exceptions.CompileException;
import com.googlecode.prolog_cafe.exceptions.ReductionLimitException;
import com.googlecode.prolog_cafe.lang.IntegerTerm;
import com.googlecode.prolog_cafe.lang.ListTerm;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.PrologMachineCopy;
import com.googlecode.prolog_cafe.lang.StructureTerm;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;
import com.googlecode.prolog_cafe.lang.VariableTerm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Evaluates a submit-like Prolog rule found in the rules.pl file of the current
 * project and filters the results through rules found in the parent projects,
 * all the way up to All-Projects.
 */
public class PrologRuleEvaluator {
  private static final Logger log = LoggerFactory
      .getLogger(PrologRuleEvaluator.class);

  public interface Factory {
    PrologRuleEvaluator create(ChangeData cd, SubmitRuleFlags flags);
  }

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

  private final PrologEnvironment.Factory envFactory;
  private final RulesCache rulesCache;
  private final ChangeData cd;
  private final SubmitRuleFlags flags;
  private final ChangeControl control;

  private boolean skipFilters;
  private String rule;
  private boolean logErrors = true;
  private long reductionsConsumed;

  private Term submitRule;

  @Inject
  PrologRuleEvaluator(PrologEnvironment.Factory envFactory,
      RulesCache rulesCache,
      @Assisted ChangeData cd,
      @Assisted SubmitRuleFlags flags)
      throws OrmException {
    this.envFactory = envFactory;
    this.rulesCache = rulesCache;
    this.cd = cd;
    this.flags = flags;
    this.control = cd.changeControl();
  }

  /**
   * @param skip if true, submit filter will not be applied.
   * @return this
   */
  public PrologRuleEvaluator setSkipSubmitFilters(boolean skip) {
    skipFilters = skip;
    return this;
  }

  /**
   * @param rule custom rule to use, or null to use refs/meta/config:rules.pl.
   * @return this
   */
  public PrologRuleEvaluator setRule(@Nullable String rule) {
    this.rule = rule;
    return this;
  }

  /**
   * @param log whether to log error messages in addition to returning error
   *     records. If true, error record messages will be less descriptive.
   */
  public PrologRuleEvaluator setLogErrors(boolean log) {
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
    if (!flags.allowClosed && c.getStatus().isClosed()) {
      SubmitRecord rec = new SubmitRecord();
      rec.status = SubmitRecord.Status.CLOSED;
      return Collections.singletonList(rec);
    }
    if (!flags.allowDraft) {
      if (c.getStatus() == Change.Status.DRAFT) {
        return cannotSubmitDraft();
      }
      try {
        initPatchSet();
      } catch (OrmException e) {
        return ruleError("Error looking up patch set "
            + control.getChange().currentPatchSetId());
      }
      if (flags.patchSet.isDraft()) {
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
            + "no solution.", getSubmitRule(), cd.getId(), getProjectName()));
    }

    return resultsToSubmitRecord(getSubmitRule(), results);
  }

  private List<SubmitRecord> cannotSubmitDraft() {
    try {
      if (!control.isDraftVisible(cd.db(), cd)) {
        return createRuleError("Patch set " + flags.patchSet.getId() + " not found");
      } else if (flags.patchSet.isDraft()) {
        return createRuleError("Cannot submit draft patch sets");
      } else {
        return createRuleError("Cannot submit draft changes");
      }
    } catch (OrmException err) {
      String msg = "Cannot check visibility of patch set " + flags.patchSet.getId();
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
        return createTypeError("Patch set " + flags.patchSet.getId() + " not found");
      }
      if (flags.patchSet.isDraft() && !control.isDraftVisible(cd.db(), cd)) {
        return createTypeError("Patch set " + flags.patchSet.getId() + " not found");
      }
    } catch (OrmException err) {
      String msg = "Cannot read patch set " + flags.patchSet.getId();
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
      return typeError("Submit rule '" + getSubmitRule() + "' for change "
          + cd.getId() + " of " + getProjectName() + " has no solution.");
    }

    Term typeTerm = results.get(0);
    if (!(typeTerm instanceof SymbolTerm)) {
      return typeError("Submit rule '" + getSubmitRule() + "' for change "
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
      if (flags.fastEvalLabels) {
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
    ProjectState projectState = getProjectState();
    PrologEnvironment env;
    try {
      if (rule == null) {
        env = newPrologEnvironment(projectState);
      } else {
        env = newPrologEnvironment("stdin", new StringReader(rule));
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
    ProjectState projectState = getProjectState();
    PrologEnvironment childEnv = env;
    for (ProjectState parentState : projectState.parents()) {
      PrologEnvironment parentEnv;
      try {
        parentEnv = newPrologEnvironment(parentState);
      } catch (CompileException err) {
        throw new RuleEvalException("Cannot consult rules.pl for "
            + parentState.getProject().getName(), err);
      }

      parentEnv.copyStoredValues(childEnv);
      Term filterRule =
          parentEnv.once("gerrit", filterRuleLocatorName, new VariableTerm());
      try {
        if (flags.fastEvalLabels) {
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

  /** @return Construct a new PrologEnvironment for the calling thread. */
  private PrologEnvironment newPrologEnvironment(ProjectState ps)
      throws CompileException {
    PrologMachineCopy pmc = rulesCache.loadMachine(
        ps.getProject().getNameKey(),
        ps.getConfig().getRulesId());
    return envFactory.create(pmc);
  }

  /**
   * Like {@link #newPrologEnvironment(ProjectState)} but instead of reading the
   * rules.pl read the provided input stream.
   *
   * @param name a name of the input stream. Could be any name.
   * @param in stream to read prolog rules from
   * @throws CompileException
   */
  private PrologEnvironment newPrologEnvironment(String name, Reader in)
      throws CompileException {
    PrologMachineCopy pmc = rulesCache.loadMachine(name, in);
    return envFactory.create(pmc);
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

  private void initPatchSet() throws OrmException {
    if (flags.patchSet == null) {
      flags.patchSet = cd.currentPatchSet();
    }
  }

  private ProjectState getProjectState() {
    return control.getProjectControl().getProjectState();
  }

  private String getProjectName() {
    return getProjectState().getProject().getName();
  }
}
