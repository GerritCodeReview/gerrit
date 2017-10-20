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
 * implemented in Prolog in {@code gerrit_common.pl}.
 */
public enum LabelFunction {
  MAX_WITH_BLOCK("MaxWithBlock", true),
  ANY_WITH_BLOCK("AnyWithBlock", true),
  MAX_NO_BLOCK("MaxNoBlock", false),
  NO_BLOCK("NoBlock", false),
  NO_OP("NoOp", false),
  PATCH_SET_LOCK("PatchSetLock", false);

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

  private LabelFunction(String name, boolean isBlock) {
    this.name = name;
    this.isBlock = isBlock;
  }

  /** The function name as defined in documentation and {@code project.config}. */
  public String getFunctionName() {
    return name;
  }

  /** Whether the label is a "block" label, meaning a minimum vote will prevent submission. */
  public boolean isBlock() {
    return isBlock;
  }
}
