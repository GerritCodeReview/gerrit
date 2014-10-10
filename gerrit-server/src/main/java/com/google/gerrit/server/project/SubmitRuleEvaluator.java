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

import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.Lists;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.rules.PrologEnvironment;
import com.google.gerrit.rules.StoredValues;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;

import com.googlecode.prolog_cafe.compiler.CompileException;
import com.googlecode.prolog_cafe.lang.ListTerm;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.PrologException;
import com.googlecode.prolog_cafe.lang.Term;
import com.googlecode.prolog_cafe.lang.VariableTerm;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Evaluates a submit-like Prolog rule found in the rules.pl file of the current
 * project and filters the results through rules found in the parent projects,
 * all the way up to All-Projects.
 */
public class SubmitRuleEvaluator {
  private final ChangeData cd;
  private final PatchSet patchSet;
  private final ChangeControl control;

  private boolean fastEvalLabels;
  private boolean skipFilters;
  private String rule;

  private Term submitRule;

  public SubmitRuleEvaluator(ChangeData cd) throws OrmException {
    this(cd, cd.currentPatchSet());
  }

  public SubmitRuleEvaluator(ChangeData cd, PatchSet patchSet)
      throws OrmException {
    this.cd = cd;
    this.patchSet = patchSet;
    this.control = cd.changeControl();
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
   * Evaluate the submit rules.
   *
   * @return List of {@link Term} objects returned from the evaluated rules.
   * @throws RuleEvalException
   */
  public List<Term> evaluate() throws RuleEvalException {
    return evaluateImpl("locate_submit_rule", "can_submit",
          "locate_submit_filter", "filter_submit_results");
  }

  /**
   * Evaluate the submit type rules.
   *
   * @return List of {@link Term} objects returned from the evaluated rules.
   * @throws RuleEvalException
   */
  public List<Term> evaluateSubmitType() throws RuleEvalException {
    return evaluateImpl("locate_submit_type", "get_submit_type",
          "locate_submit_type_filter", "filter_submit_type_results");
  }

  private List<Term> evaluateImpl(
      String userRuleLocatorName,
      String userRuleWrapperName,
      String filterRuleLocatorName,
      String filterRuleWrapperName) throws RuleEvalException {
    PrologEnvironment env = getPrologEnvironment();
    try {
      Term submitRule = env.once("gerrit", userRuleLocatorName, new VariableTerm());
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
            + " on change " + cd.getId() + " of " + getProjectName(),
            err);
      } catch (RuntimeException err) {
        throw new RuleEvalException("Exception calling " + submitRule
            + " on change " + cd.getId() + " of " + getProjectName(),
            err);
      }

      Term resultsTerm = toListTerm(results);
      if (!skipFilters) {
        resultsTerm = runSubmitFilters(
            resultsTerm, env, filterRuleLocatorName, filterRuleWrapperName);
      }
      List<Term> r;
      if (resultsTerm.isList()) {
        r = Lists.newArrayList();
        for (Term t = resultsTerm; t.isList();) {
          ListTerm l = (ListTerm) t;
          r.add(l.car().dereference());
          t = l.cdr().dereference();
        }
      } else {
        r = Collections.emptyList();
      }
      this.submitRule = submitRule;
      return r;
    } finally {
      env.close();
    }
  }

  private PrologEnvironment getPrologEnvironment() throws RuleEvalException {
    ProjectState projectState = control.getProjectControl().getProjectState();
    PrologEnvironment env;
    try {
      if (rule == null) {
        env = projectState.newPrologEnvironment();
      } else {
        env = projectState.newPrologEnvironment(
            "stdin", new ByteArrayInputStream(rule.getBytes(UTF_8)));
      }
    } catch (CompileException err) {
      throw new RuleEvalException("Cannot consult rules.pl for "
          + getProjectName(), err);
    }
    env.set(StoredValues.REVIEW_DB, cd.db());
    env.set(StoredValues.CHANGE_DATA, cd);
    env.set(StoredValues.PATCH_SET, patchSet);
    env.set(StoredValues.CHANGE_CONTROL, control);
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
      } catch (PrologException err) {
        throw new RuleEvalException("Exception calling " + filterRule
            + " on change " + cd.getId() + " of "
            + parentState.getProject().getName(), err);
      } catch (RuntimeException err) {
        throw new RuleEvalException("Exception calling " + filterRule
            + " on change " + cd.getId() + " of "
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
    checkState(submitRule != null, "getSubmitRule() invalid before evaluation");
    return submitRule;
  }

  private String getProjectName() {
    return control.getProjectControl().getProjectState().getProject().getName();
  }
}
