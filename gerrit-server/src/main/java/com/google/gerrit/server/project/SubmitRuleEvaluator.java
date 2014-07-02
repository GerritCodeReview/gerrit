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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.Lists;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.rules.PrologEnvironment;
import com.google.gerrit.rules.StoredValues;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.query.change.ChangeData;

import com.googlecode.prolog_cafe.compiler.CompileException;
import com.googlecode.prolog_cafe.lang.ListTerm;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.PrologException;
import com.googlecode.prolog_cafe.lang.Term;
import com.googlecode.prolog_cafe.lang.VariableTerm;

import org.eclipse.jgit.lib.Config;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Evaluates a submit-like Prolog rule found in the rules.pl file of the current
 * project and filters the results through rules found in the parent projects,
 * all the way up to All-Projects.
 */
public class SubmitRuleEvaluator implements Callable<List<Term>> {
  private static final TimeUnit TIMEOUT_UNIT = TimeUnit.MILLISECONDS;

  private final ReviewDb db;
  private final PatchSet patchSet;
  private final ProjectControl projectControl;
  private final ChangeControl changeControl;
  private final Change change;
  private final ChangeData cd;
  private final boolean fastEvalLabels;
  private final String userRuleLocatorName;
  private final String userRuleWrapperName;
  private final String filterRuleLocatorName;
  private final String filterRuleWrapperName;
  private final boolean skipFilters;
  private final InputStream rulesInputStream;
  private final long evaluationTimeout;

  private Term submitRule;
  private String projectName;

  /**
   * @param userRuleLocatorName The name of the rule used to locate the
   *        user-supplied rule.
   * @param userRuleWrapperName The name of the wrapper rule used to evaluate
   *        the user-supplied rule.
   * @param filterRuleLocatorName The name of the rule used to locate the filter
   *        rule.
   * @param filterRuleWrapperName The name of the rule used to evaluate the
   *        filter rule.
   */
  public SubmitRuleEvaluator(ReviewDb db,
      @GerritServerConfig Config gerritServerConfig,
      PatchSet patchSet, ProjectControl projectControl,
      ChangeControl changeControl, Change change, ChangeData cd,
      boolean fastEvalLabels,
      String userRuleLocatorName, String userRuleWrapperName,
      String filterRuleLocatorName, String filterRuleWrapperName) {
    this(db, gerritServerConfig, patchSet, projectControl, changeControl, change, cd,
        fastEvalLabels, userRuleLocatorName, userRuleWrapperName,
        filterRuleLocatorName, filterRuleWrapperName, false, null);
  }

  /**
   * @param userRuleLocatorName The name of the rule used to locate the
   *        user-supplied rule.
   * @param userRuleWrapperName The name of the wrapper rule used to evaluate
   *        the user-supplied rule.
   * @param filterRuleLocatorName The name of the rule used to locate the filter
   *        rule.
   * @param filterRuleWrapperName The name of the rule used to evaluate the
   *        filter rule.
   * @param skipSubmitFilters if {@code true} submit filter will not be
   *        applied
   * @param rules when non-null the rules will be read from this input stream
   *        instead of refs/meta/config:rules.pl file
   */
  public SubmitRuleEvaluator(ReviewDb db,
      @GerritServerConfig Config gerritServerConfig,
      PatchSet patchSet, ProjectControl projectControl,
      ChangeControl changeControl, Change change, ChangeData cd,
      boolean fastEvalLabels,
      String userRuleLocatorName, String userRuleWrapperName,
      String filterRuleLocatorName, String filterRuleWrapperName,
      boolean skipSubmitFilters, InputStream rules) {
    this.db = db;
    this.patchSet = patchSet;
    this.projectControl = projectControl;
    this.changeControl = changeControl;
    this.change = change;
    this.cd = checkNotNull(cd, "ChangeData");
    this.fastEvalLabels = fastEvalLabels;
    this.userRuleLocatorName = userRuleLocatorName;
    this.userRuleWrapperName = userRuleWrapperName;
    this.filterRuleLocatorName = filterRuleLocatorName;
    this.filterRuleWrapperName = filterRuleWrapperName;
    this.skipFilters = skipSubmitFilters;
    this.rulesInputStream = rules;
    this.evaluationTimeout =
        ConfigUtil.getTimeUnit(gerritServerConfig, "rules", null,
            "evaluationTimeout", 0, TIMEOUT_UNIT);
  }

  /**
   * Evaluates the given rule and filters.
   *
   * Sets the {@link #submitRule} to the Term found by the
   * {@link #userRuleLocatorName}. This can be used when reporting error(s) on
   * unexpected return value of this method.
   *
   * @return List of {@link Term} objects returned from the evaluated rules.
   * @throws RuleEvalException
   * @throws RuleEvalTimeoutException
   */
  public List<Term> evaluate() throws RuleEvalException, RuleEvalTimeoutException {
    if (evaluationTimeout < 1) {
      return evaluateImpl();
    }
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<List<Term>> future = executor.submit(this);
    try {
      return future.get(evaluationTimeout, TIMEOUT_UNIT);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuleEvalException) {
        throw (RuleEvalException) cause;
      }
      throw new RuleEvalException(
          "Unexpected error during rule evaluation occurs", e);
    } catch (InterruptedException e) {
      throw new RuleEvalException("Rule evaluation interrupted", e);
    } catch (TimeoutException e) {
      throw new RuleEvalTimeoutException(String.format(
          "Rule evaluation killed after timeout of %s %s", evaluationTimeout,
          TIMEOUT_UNIT.toString().toLowerCase()));
    } finally {
      executor.shutdownNow();
    }
  }

  @Override
  public List<Term> call() throws Exception {
    return evaluateImpl();
  }

  private List<Term> evaluateImpl() throws RuleEvalException {
    PrologEnvironment env = getPrologEnvironment();
    try {
      submitRule = env.once("gerrit", userRuleLocatorName, new VariableTerm());
      if (fastEvalLabels) {
        env.once("gerrit", "assume_range_from_label");
      }

      List<Term> results = new ArrayList<>();
      try {
        for (Term[] template : env.all("gerrit", userRuleWrapperName,
            submitRule, new VariableTerm())) {
          results.add(template[1]);
        }
      } catch (PrologException err) {
        throw new RuleEvalException("Exception calling " + submitRule
            + " on change " + change.getId() + " of " + getProjectName(),
            err);
      } catch (RuntimeException err) {
        throw new RuleEvalException("Exception calling " + submitRule
            + " on change " + change.getId() + " of " + getProjectName(),
            err);
      }

      Term resultsTerm = toListTerm(results);
      if (!skipFilters) {
        resultsTerm = runSubmitFilters(resultsTerm, env);
      }
      if (resultsTerm.isList()) {
        List<Term> r = Lists.newArrayList();
        for (Term t = resultsTerm; t.isList();) {
          ListTerm l = (ListTerm) t;
          r.add(l.car().dereference());
          t = l.cdr().dereference();
        }
        return r;
      }
      return Collections.emptyList();
    } finally {
      env.close();
    }
  }

  private PrologEnvironment getPrologEnvironment() throws RuleEvalException {
    ProjectState projectState = projectControl.getProjectState();
    PrologEnvironment env;
    try {
      if (rulesInputStream == null) {
        env = projectState.newPrologEnvironment();
      } else {
        env = projectState.newPrologEnvironment("stdin", rulesInputStream);
      }
    } catch (CompileException err) {
      throw new RuleEvalException("Cannot consult rules.pl for "
          + getProjectName(), err);
    }
    env.set(StoredValues.REVIEW_DB, db);
    env.set(StoredValues.CHANGE_DATA, cd);
    env.set(StoredValues.PATCH_SET, patchSet);
    env.set(StoredValues.CHANGE_CONTROL, changeControl);
    return env;
  }

  private Term runSubmitFilters(Term results, PrologEnvironment env) throws RuleEvalException {
    ProjectState projectState = projectControl.getProjectState();
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
      } catch (PrologException err) {
        throw new RuleEvalException("Exception calling " + filterRule
            + " on change " + change.getId() + " of "
            + parentState.getProject().getName(), err);
      } catch (RuntimeException err) {
        throw new RuleEvalException("Exception calling " + filterRule
            + " on change " + change.getId() + " of "
            + parentState.getProject().getName(), err);
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

  public Term getSubmitRule() {
    return submitRule;
  }

  private String getProjectName() {
    if (projectName == null) {
      projectName = projectControl.getProjectState().getProject().getName();
    }
    return projectName;
  }
}
