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

package com.google.gerrit.common.data;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
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
  ANY_WITH_BLOCK("AnyWithBlock", true, true, false),
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
  private final boolean isMandatory;
  private final boolean requiresMaxValue;

  LabelFunction(String name) {
    this(name, false, false, false);
  }

  LabelFunction(String name, boolean isBlock, boolean isMandatory, boolean requiresMaxValue) {
    this.name = name;
    this.isBlock = isBlock;
    this.isMandatory = isMandatory;
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
  public boolean isMandatory() {
    return isMandatory;
  }

  /** Whether the label requires a vote with the maximum value to allow submission. */
  public boolean isMaxValueMandatory() {
    return requiresMaxValue;
  }

  public SubmitRecord.Label check(LabelType t, Iterable<PatchSetApproval> approvals) {
    SubmitRecord.Label l = new SubmitRecord.Label();
    l.label = t.getName();

    l.status = SubmitRecord.Label.Status.MAY;
    if (isMandatory) {
      l.status = SubmitRecord.Label.Status.NEED;
    }

    for (PatchSetApproval a : approvals) {
      if (a.getValue() == 0) {
        continue;
      }

      if (isBlock && t.isMaxNegative(a)) {
        l.appliedBy = a.getAccountId();
        l.status = SubmitRecord.Label.Status.REJECT;
        return l;
      }

      if (t.isMaxPositive(a) || !requiresMaxValue) {
        l.appliedBy = a.getAccountId();

        l.status = SubmitRecord.Label.Status.MAY;
        if (isMandatory) {
          l.status = SubmitRecord.Label.Status.OK;
        }
      }
    }
    return l;
  }
}
