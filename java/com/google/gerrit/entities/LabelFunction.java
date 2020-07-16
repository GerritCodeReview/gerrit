// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.entities;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.SubmitRecord.Label;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Functions for determining submittability based on label votes.
 *
 * <p>Only describes built-in label functions. Admins can extend the logic arbitrarily using Prolog
 * rules, in which case the choice of function in the project config is ignored.
 *
 * <p>Function semantics are documented in {@code config-labels.txt}, and actual behavior is
 * implemented both in Prolog in {@code gerrit_common.pl} and in the {@link #check} method.
 */
public enum LabelFunction {
  ANY_WITH_BLOCK("AnyWithBlock", true, false, false),
  MAX_WITH_BLOCK("MaxWithBlock", true, true, true),
  MAX_NO_BLOCK("MaxNoBlock", false, true, true),
  NO_BLOCK("NoBlock"),
  NO_OP("NoOp"),
  PATCH_SET_LOCK("PatchSetLock");

  public static final Map<String, LabelFunction> ALL;

  static {
    Map<String, LabelFunction> all = new LinkedHashMap<>();
    for (LabelFunction f : values()) {
      all.put(f.getFunctionName(), f);
    }
    ALL = Collections.unmodifiableMap(all);
  }

  public static Optional<LabelFunction> parse(@Nullable String str) {
    return Optional.ofNullable(ALL.get(str));
  }

  private final String name;
  private final boolean isBlock;
  private final boolean isRequired;
  private final boolean requiresMaxValue;

  LabelFunction(String name) {
    this(name, false, false, false);
  }

  LabelFunction(String name, boolean isBlock, boolean isRequired, boolean requiresMaxValue) {
    this.name = name;
    this.isBlock = isBlock;
    this.isRequired = isRequired;
    this.requiresMaxValue = requiresMaxValue;
  }

  /** The function name as defined in documentation and {@code project.config}. */
  public String getFunctionName() {
    return name;
  }

  /** Whether the label is a "block" label, meaning a minimum vote will prevent submission. */
  public boolean isBlock() {
    return isBlock;
  }

  /** Whether the label is a mandatory label, meaning absence of votes will prevent submission. */
  public boolean isRequired() {
    return isRequired;
  }

  /** Whether the label requires a vote with the maximum value to allow submission. */
  public boolean isMaxValueRequired() {
    return requiresMaxValue;
  }

  public Label check(LabelType labelType, Iterable<PatchSetApproval> approvals) {
    Label submitRecordLabel = new Label();
    submitRecordLabel.label = labelType.getName();

    submitRecordLabel.status = SubmitRecord.Label.Status.MAY;
    if (isRequired) {
      submitRecordLabel.status = SubmitRecord.Label.Status.NEED;
    }

    for (PatchSetApproval a : approvals) {
      if (a.value() == 0) {
        continue;
      }

      if (isBlock && labelType.isMaxNegative(a)) {
        submitRecordLabel.appliedBy = a.accountId();
        submitRecordLabel.status = SubmitRecord.Label.Status.REJECT;
        return submitRecordLabel;
      }

      if (labelType.isMaxPositive(a) || !requiresMaxValue) {
        submitRecordLabel.appliedBy = a.accountId();

        submitRecordLabel.status = SubmitRecord.Label.Status.MAY;
        if (isRequired) {
          submitRecordLabel.status = SubmitRecord.Label.Status.OK;
        }
      }
    }

    return submitRecordLabel;
  }
}
