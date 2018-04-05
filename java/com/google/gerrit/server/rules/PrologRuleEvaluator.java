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

package com.google.gerrit.server.rules;

import static com.google.gerrit.server.project.SubmitRuleEvaluator.createRuleError;
import static com.google.gerrit.server.project.SubmitRuleEvaluator.defaultRuleError;
import static com.google.gerrit.server.project.SubmitRuleEvaluator.defaultTypeError;

import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.common.data.SubmitTypeRecord;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.Accounts;
import com.google.gerrit.server.account.Emails;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.RuleEvalException;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
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
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evaluates a submit-like Prolog rule found in the rules.pl file of the current project and filters
 * the results through rules found in the parent projects, all the way up to All-Projects.
 */
public class PrologRuleEvaluator {
  private static final Logger log = LoggerFactory.getLogger(PrologRuleEvaluator.class);

  public interface Factory {
    /** Returns a new {@link PrologRuleEvaluator} with the specified options */
    PrologRuleEvaluator create(ChangeData cd, SubmitRuleOptions options);
  }

  /**
   * Exception thrown when the label term of a submit record unexpectedly didn't contain a user
   * term.
   */
  private static class UserTermExpected extends Exception {
    private static final long serialVersionUID = 1L;

    UserTermExpected(SubmitRecord.Label label) {
      super(String.format("A label with the status %s must contain a user.", label.toString()));
    }
  }

  private final AccountCache accountCache;
  private final Accounts accounts;
  private final Emails emails;
  private final RulesCache rulesCache;
  private final PrologEnvironment.Factory envFactory;
  private final ChangeData cd;
  private final ProjectState projectState;
  private final SubmitRuleOptions opts;
  private Term submitRule;

  @AssistedInject
  private PrologRuleEvaluator(
      AccountCache accountCache,
      Accounts accounts,
      Emails emails,
      RulesCache rulesCache,
      PrologEnvironment.Factory envFactory,
      ProjectCache projectCache,
      @Assisted ChangeData cd,
      @Assisted SubmitRuleOptions options) {
    this.accountCache = accountCache;
    this.accounts = accounts;
    this.emails = emails;
    this.rulesCache = rulesCache;
    this.envFactory = envFactory;
    this.cd = cd;
    this.opts = options;

    this.projectState = projectCache.get(cd.project());
  }

  private static Term toListTerm(List<Term> terms) {
    Term list = Prolog.Nil;
    for (int i = terms.size() - 1; i >= 0; i--) {
      list = new ListTerm(terms.get(i), list);
    }
    return list;
  }

  private static boolean isUser(Term who) {
    return who instanceof StructureTerm
        && who.arity() == 1
        && who.name().equals("user")
        && who.arg(0) instanceof IntegerTerm;
  }

  private Term getSubmitRule() {
    return submitRule;
  }

  /**
   * Evaluate the submit rules.
   *
   * @return List of {@link SubmitRecord} objects returned from the evaluated rules, including any
   *     errors.
   */
  public Collection<SubmitRecord> evaluate() {
    Change change;
    try {
      change = cd.change();
      if (change == null) {
        throw new OrmException("No change found");
      }

      if (projectState == null) {
        throw new NoSuchProjectException(cd.project());
      }
    } catch (OrmException | NoSuchProjectException e) {
      return ruleError("Error looking up change " + cd.getId(), e);
    }

    if (!opts.allowClosed() && change.getStatus().isClosed()) {
      SubmitRecord rec = new SubmitRecord();
      rec.status = SubmitRecord.Status.CLOSED;
      return Collections.singletonList(rec);
    }

    List<Term> results;
    try {
      results =
          evaluateImpl(
              "locate_submit_rule", "can_submit", "locate_submit_filter", "filter_submit_results");
    } catch (RuleEvalException e) {
      return ruleError(e.getMessage(), e);
    }

    if (results.isEmpty()) {
      // This should never occur. A well written submit rule will always produce
      // at least one result informing the caller of the labels that are
      // required for this change to be submittable. Each label will indicate
      // whether or not that is actually possible given the permissions.
      return ruleError(
          String.format(
              "Submit rule '%s' for change %s of %s has no solution.",
              getSubmitRuleName(), cd.getId(), projectState.getName()));
    }

    return resultsToSubmitRecord(getSubmitRule(), results);
  }

  private String getSubmitRuleName() {
    return submitRule == null ? "<unknown>" : submitRule.name();
  }

  /**
   * Convert the results from Prolog Cafe's format to Gerrit's common format.
   *
   * <p>can_submit/1 terminates when an ok(P) record is found. Therefore walk the results backwards,
   * using only that ok(P) record if it exists. This skips partial results that occur early in the
   * output. Later after the loop the out collection is reversed to restore it to the original
   * ordering.
   */
  public List<SubmitRecord> resultsToSubmitRecord(Term submitRule, List<Term> results) {
    boolean foundOk = false;
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
        foundOk = true;
        break;
      }
    }
    Collections.reverse(out);

    // This transformation is required to adapt Prolog's behavior to the way Gerrit handles
    // SubmitRecords, as defined in the SubmitRecord#allRecordsOK method.
    // When several rules are defined in Prolog, they are all matched to a SubmitRecord. We want
    // the change to be submittable when at least one result is OK.
    if (foundOk) {
      for (SubmitRecord record : out) {
        record.status = SubmitRecord.Status.OK;
      }
    }

    return out;
  }

  private List<SubmitRecord> invalidResult(Term rule, Term record, String reason) {
    return ruleError(
        String.format(
            "Submit rule %s for change %s of %s output invalid result: %s%s",
            rule,
            cd.getId(),
            cd.project().get(),
            record,
            (reason == null ? "" : ". Reason: " + reason)));
  }

  private List<SubmitRecord> invalidResult(Term rule, Term record) {
    return invalidResult(rule, record, null);
  }

  private List<SubmitRecord> ruleError(String err) {
    return ruleError(err, null);
  }

  private List<SubmitRecord> ruleError(String err, Exception e) {
    if (opts.logErrors()) {
      if (e == null) {
        log.error(err);
      } else {
        log.error(err, e);
      }
      return defaultRuleError();
    }
    return createRuleError(err);
  }

  /**
   * Evaluate the submit type rules to get the submit type.
   *
   * @return record from the evaluated rules.
   */
  public SubmitTypeRecord getSubmitType() {
    try {
      if (projectState == null) {
        throw new NoSuchProjectException(cd.project());
      }
    } catch (NoSuchProjectException e) {
      return typeError("Error looking up change " + cd.getId(), e);
    }

    List<Term> results;
    try {
      results =
          evaluateImpl(
              "locate_submit_type",
              "get_submit_type",
              "locate_submit_type_filter",
              "filter_submit_type_results");
    } catch (RuleEvalException e) {
      return typeError(e.getMessage(), e);
    }

    if (results.isEmpty()) {
      // Should never occur for a well written rule
      return typeError(
          "Submit rule '"
              + getSubmitRuleName()
              + "' for change "
              + cd.getId()
              + " of "
              + projectState.getName()
              + " has no solution.");
    }

    Term typeTerm = results.get(0);
    if (!(typeTerm instanceof SymbolTerm)) {
      return typeError(
          "Submit rule '"
              + getSubmitRuleName()
              + "' for change "
              + cd.getId()
              + " of "
              + projectState.getName()
              + " did not return a symbol.");
    }

    String typeName = typeTerm.name();
    try {
      return SubmitTypeRecord.OK(SubmitType.valueOf(typeName.toUpperCase()));
    } catch (IllegalArgumentException e) {
      return typeError(
          "Submit type rule "
              + getSubmitRule()
              + " for change "
              + cd.getId()
              + " of "
              + projectState.getName()
              + " output invalid result: "
              + typeName);
    }
  }

  private SubmitTypeRecord typeError(String err) {
    return typeError(err, null);
  }

  private SubmitTypeRecord typeError(String err, Exception e) {
    if (opts.logErrors()) {
      if (e == null) {
        log.error(err);
      } else {
        log.error(err, e);
      }
      return defaultTypeError();
    }
    return SubmitTypeRecord.error(err);
  }

  private List<Term> evaluateImpl(
      String userRuleLocatorName,
      String userRuleWrapperName,
      String filterRuleLocatorName,
      String filterRuleWrapperName)
      throws RuleEvalException {
    PrologEnvironment env = getPrologEnvironment();
    try {
      Term sr = env.once("gerrit", userRuleLocatorName, new VariableTerm());
      List<Term> results = new ArrayList<>();
      try {
        for (Term[] template : env.all("gerrit", userRuleWrapperName, sr, new VariableTerm())) {
          results.add(template[1]);
        }
      } catch (ReductionLimitException err) {
        throw new RuleEvalException(
            String.format(
                "%s on change %d of %s",
                err.getMessage(), cd.getId().get(), projectState.getName()));
      } catch (RuntimeException err) {
        throw new RuleEvalException(
            String.format(
                "Exception calling %s on change %d of %s",
                sr, cd.getId().get(), projectState.getName()),
            err);
      }

      Term resultsTerm = toListTerm(results);
      if (!opts.skipFilters()) {
        resultsTerm =
            runSubmitFilters(resultsTerm, env, filterRuleLocatorName, filterRuleWrapperName);
      }
      List<Term> r;
      if (resultsTerm instanceof ListTerm) {
        r = new ArrayList<>();
        for (Term t = resultsTerm; t instanceof ListTerm; ) {
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

  private PrologEnvironment getPrologEnvironment() throws RuleEvalException {
    PrologEnvironment env;
    try {
      PrologMachineCopy pmc;
      if (opts.rule() == null) {
        pmc =
            rulesCache.loadMachine(
                projectState.getNameKey(), projectState.getConfig().getRulesId());
      } else {
        pmc = rulesCache.loadMachine("stdin", new StringReader(opts.rule()));
      }
      env = envFactory.create(pmc);
    } catch (CompileException err) {
      String msg;
      if (opts.rule() == null) {
        msg =
            String.format(
                "Cannot load rules.pl for %s: %s", projectState.getName(), err.getMessage());
      } else {
        msg = err.getMessage();
      }
      throw new RuleEvalException(msg, err);
    }
    env.set(StoredValues.ACCOUNTS, accounts);
    env.set(StoredValues.ACCOUNT_CACHE, accountCache);
    env.set(StoredValues.EMAILS, emails);
    env.set(StoredValues.REVIEW_DB, cd.db());
    env.set(StoredValues.CHANGE_DATA, cd);
    env.set(StoredValues.PROJECT_STATE, projectState);
    return env;
  }

  private Term runSubmitFilters(
      Term results,
      PrologEnvironment env,
      String filterRuleLocatorName,
      String filterRuleWrapperName)
      throws RuleEvalException {
    PrologEnvironment childEnv = env;
    ChangeData cd = env.get(StoredValues.CHANGE_DATA);
    ProjectState projectState = env.get(StoredValues.PROJECT_STATE);
    for (ProjectState parentState : projectState.parents()) {
      PrologEnvironment parentEnv;
      try {
        parentEnv =
            envFactory.create(
                rulesCache.loadMachine(
                    parentState.getNameKey(), parentState.getConfig().getRulesId()));
      } catch (CompileException err) {
        throw new RuleEvalException("Cannot consult rules.pl for " + parentState.getName(), err);
      }

      parentEnv.copyStoredValues(childEnv);
      Term filterRule = parentEnv.once("gerrit", filterRuleLocatorName, new VariableTerm());
      try {
        Term[] template =
            parentEnv.once(
                "gerrit", filterRuleWrapperName, filterRule, results, new VariableTerm());
        results = template[2];
      } catch (ReductionLimitException err) {
        throw new RuleEvalException(
            String.format(
                "%s on change %d of %s",
                err.getMessage(), cd.getId().get(), parentState.getName()));
      } catch (RuntimeException err) {
        throw new RuleEvalException(
            String.format(
                "Exception calling %s on change %d of %s",
                filterRule, cd.getId().get(), parentState.getName()),
            err);
      }
      childEnv = parentEnv;
    }
    return results;
  }

  private void appliedBy(SubmitRecord.Label label, Term status) throws UserTermExpected {
    if (status instanceof StructureTerm && status.arity() == 1) {
      Term who = status.arg(0);
      if (isUser(who)) {
        label.appliedBy = new Account.Id(((IntegerTerm) who.arg(0)).intValue());
      } else {
        throw new UserTermExpected(label);
      }
    }
  }
}
