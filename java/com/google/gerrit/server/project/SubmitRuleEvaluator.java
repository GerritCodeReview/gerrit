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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.common.data.SubmitTypeRecord;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evaluates a submit-like Prolog rule found in the rules.pl file of the current project and filters
 * the results through rules found in the parent projects, all the way up to All-Projects.
 */
public class SubmitRuleEvaluator {
  private static final Logger log = LoggerFactory.getLogger(SubmitRuleEvaluator.class);

  private static final String DEFAULT_MSG = "Error evaluating project rules, check server log";

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
    return SubmitTypeRecord.error(DEFAULT_MSG);
  }

  public String getSubmitRuleName() {
    // TODO(maximeg) Adjust this.
    return "<unknown rule>";
  }

  public interface Factory {
    SubmitRuleEvaluator create(CurrentUser user, ChangeData cd);
  }

  private final ProjectCache projectCache;
  private final ChangeData cd;

  private SubmitRuleOptions.Builder optsBuilder = SubmitRuleOptions.builder();
  private SubmitRuleOptions opts;
  private Change change;
  private PatchSet patchSet;
  private boolean logErrors = true;
  private ProjectState projectState;

  @Inject
  SubmitRuleEvaluator(
      ProjectCache projectCache,
      @Assisted ChangeData cd) {
    this.projectCache = projectCache;
    this.cd = cd;
  }

  /**
   * @return immutable snapshot of options configured so far. If {@link #getSubmitType()} has
   * not been called yet, state within this instance is still
   *     mutable, so may change before evaluation. The instance's options are frozen at evaluation
   *     time.
   */
  public SubmitRuleOptions getOptions() {
    if (opts != null) {
      return opts;
    }
    return optsBuilder.build();
  }

  public SubmitRuleEvaluator setOptions(SubmitRuleOptions opts) {
    checkNotStarted();
    if (opts != null) {
      optsBuilder = opts.toBuilder();
    } else {
      optsBuilder = SubmitRuleOptions.builder();
    }
    return this;
  }

  /**
   * @param ps patch set of the change to evaluate. If not set, the current patch set will be loaded
   *     from {@link #evaluate()} or {@link #getSubmitType}.
   * @return this
   */
  public SubmitRuleEvaluator setPatchSet(PatchSet ps) {
    checkArgument(
        ps.getId().getParentKey().equals(cd.getId()),
        "Patch set %s does not match change %s",
        ps.getId(),
        cd.getId());
    patchSet = ps;
    return this;
  }

  /**
   * @param allow whether to allow {@link #evaluate()} on closed changes.
   * @return this
   */
  public SubmitRuleEvaluator setAllowClosed(boolean allow) {
    checkNotStarted();
    optsBuilder.allowClosed(allow);
    return this;
  }

  /**
   * @param skip if true, submit filter will not be applied.
   * @return this
   */
  public SubmitRuleEvaluator setSkipSubmitFilters(boolean skip) {
    checkNotStarted();
    optsBuilder.skipFilters(skip);
    return this;
  }

  /**
   * @param rule custom rule to use, or null to use refs/meta/config:rules.pl.
   * @return this
   */
  public SubmitRuleEvaluator setRule(@Nullable String rule) {
    checkNotStarted();
    optsBuilder.rule(rule);
    return this;
  }

  /**
   * @param log whether to log error messages in addition to returning error records. If true, error
   *     record messages will be less descriptive.
   */
  public SubmitRuleEvaluator setLogErrors(boolean log) {
    logErrors = log;
    return this;
  }

  /** @return Prolog reductions consumed during evaluation. */
  // TODO(maximeg) fix this or remove this?
  public long getReductionsConsumed() {
    return 0;
  }

  /**
   * Evaluate the submit rules.
   *
   * @return List of {@link SubmitRecord} objects returned from the evaluated rules, including any
   *     errors.
   */
  public List<SubmitRecord> evaluate() {
    initOptions();
    try {
      init();
    } catch (OrmException | NoSuchProjectException e) {
      return ruleError("Error looking up change " + cd.getId(), e);
    }

    if (!opts.allowClosed() && change.getStatus().isClosed()) {
      SubmitRecord rec = new SubmitRecord();
      rec.status = SubmitRecord.Status.CLOSED;
      return Collections.singletonList(rec);
    }

    // TODO(maximeg) fix this and call the prolog implementation
    return new ArrayList<>();
  }

  private List<SubmitRecord> ruleError(String err, Exception e) {
    if (logErrors) {
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
    initOptions();
    try {
      init();
    } catch (OrmException | NoSuchProjectException e) {
      return typeError("Error looking up change " + cd.getId(), e);
    }

    // TODO(maximeg): fix this and add support for Prolog
    return SubmitTypeRecord.OK(SubmitType.FAST_FORWARD_ONLY);
  }

  private SubmitTypeRecord typeError(String err, Exception e) {
    if (logErrors) {
      if (e == null) {
        log.error(err);
      } else {
        log.error(err, e);
      }
      return defaultTypeError();
    }
    return SubmitTypeRecord.error(err);
  }

  private void checkNotStarted() {
    checkState(opts == null, "cannot set options after starting evaluation");
  }

  private void initOptions() {
    if (opts == null) {
      opts = optsBuilder.build();
      optsBuilder = null;
    }
  }

  private void init() throws OrmException, NoSuchProjectException {
    if (change == null) {
      change = cd.change();
      if (change == null) {
        throw new OrmException("No change found");
      }
    }

    if (projectState == null) {
      projectState = projectCache.get(change.getProject());
      if (projectState == null) {
        throw new NoSuchProjectException(change.getProject());
      }
    }

    if (patchSet == null) {
      patchSet = cd.currentPatchSet();
      if (patchSet == null) {
        throw new OrmException("No patch set found");
      }
    }
  }
}
