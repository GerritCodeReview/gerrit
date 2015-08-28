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

import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.common.data.SubmitTypeRecord;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * Evaluates a submit-like Prolog rule found in the rules.pl file of the current
 * project and filters the results through rules found in the parent projects,
 * all the way up to All-Projects.
 */
public class SubmitRuleEvaluator {
  public static interface Factory {
    SubmitRuleEvaluator create(ChangeData cd);
  }

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

  private final DynamicMap<SubmitRule> submitRules;
  private final DynamicMap<SubmitTypeRule> submitTypeRules;
  private final ChangeData cd;
  private final SubmitRuleFlags flags;

  private String ruleName;

  @AssistedInject
  SubmitRuleEvaluator(DynamicMap<SubmitRule> submitRules,
      DynamicMap<SubmitTypeRule> submitTypeRules,
      @Assisted ChangeData cd) {
    this.submitRules = submitRules;
    this.submitTypeRules = submitTypeRules;
    this.cd = cd;
    this.flags = new SubmitRuleFlags();
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
    flags.patchSet = ps;
    return this;
  }

  /**
   * @param fast if true, infer label information from rules rather than reading
   *     from project config.
   * @return this
   */
  public SubmitRuleEvaluator setFastEvalLabels(boolean fast) {
    flags.fastEvalLabels = fast;
    return this;
  }

  /**
   * @param allow whether to allow {@link #evaluate()} on closed changes.
   * @return this
   */
  public SubmitRuleEvaluator setAllowClosed(boolean allow) {
    flags.allowClosed = allow;
    return this;
  }

  /**
   * @param allow whether to allow {@link #evaluate()} on draft changes.
   * @return this
   */
  public SubmitRuleEvaluator setAllowDraft(boolean allow) {
    flags.allowDraft = allow;
    return this;
  }

  /**
   * @param log whether to log error messages in addition to returning error
   *     records. If true, error record messages will be less descriptive.
   */
  public SubmitRuleEvaluator setLogErrors(boolean log) {
    flags.logErrors = log;
    return this;
  }

  /**
   * Evaluate the submit rules.
   *
   * @return List of {@link SubmitRecord} objects returned from the evaluated
   *     rules, including any errors.
   */
  public List<SubmitRecord> evaluate() throws OrmException {
    try {
      Project p = cd.changeControl().getProject();
      ruleName = p.getSubmitRule();
      RuleName qn;
      if (ruleName != null) {
        qn = RuleName.parse(ruleName);
        if (qn == null) {
          return ruleError("Wrong format of the submit rule name: "
              + ruleName + " for project: " + p.getName());
        }
      } else {
        qn = RuleName.DEFAULT_RULE;
      }
      return submitRules.get(qn.plugin, qn.name).evaluate(cd, flags);
    } catch (RuleEvalException e) {
      return ruleError(e.getMessage(), e);
    }
  }

  /**
   * Evaluate the submit type rules to get the submit type.
   *
   * @return record from the evaluated rules.
   */
  public SubmitTypeRecord getSubmitType() throws OrmException {
    try {
      Project p = cd.changeControl().getProject();
      ruleName = p.getSubmitTypeRule();
      RuleName qn;
      if (ruleName != null) {
        qn = RuleName.parse(ruleName);
        if (qn == null) {
          return typeError("Wrong format of the submit type rule name: "
              + ruleName + " for project: " + p.getName());
        }
      } else {
        qn = RuleName.DEFAULT_TYPE_RULE;
      }
      return submitTypeRules.get(qn.plugin, qn.name).getSubmitType(cd, flags);
    } catch (RuleEvalException e) {
      return typeError(e.getMessage(), e);
    }
  }

  public String getRuleName() {
    return ruleName.toString();
  }

  private List<SubmitRecord> ruleError(String err) {
    return ruleError(err, null);
  }

  private static class RuleName {
    static RuleName parse(String s) {
      int i = s.indexOf('.');
      if (i < 0) {
        return null;
      }
      if (i == s.length() - 1) {
        return null;
      }
      return new RuleName(s.substring(0, i), s.substring(i + 1));
    }

    static RuleName DEFAULT_RULE = new RuleName("gerrit", "default-submit-rule");
    static RuleName DEFAULT_TYPE_RULE = new RuleName("gerrit", "default-submit-type");

    String plugin;
    String name;

    RuleName(String plugin, String name) {
      this.plugin = plugin;
      this.name = name;
    }
  }

  private List<SubmitRecord> ruleError(String err, Exception e) {
    if (flags.logErrors) {
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

  private SubmitTypeRecord typeError(String err) {
    return typeError(err, null);
  }

  private SubmitTypeRecord typeError(String err, Exception e) {
    if (flags.logErrors) {
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
}
