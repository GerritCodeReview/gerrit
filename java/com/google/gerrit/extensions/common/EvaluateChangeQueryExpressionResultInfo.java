// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.extensions.common;

import com.google.gerrit.common.Nullable;
import java.util.List;
import java.util.Map;

/** Result of evaluating a change query expression. */
public class EvaluateChangeQueryExpressionResultInfo {
  /** Whether the change matches the change query expression. */
  public boolean status;

  /** List of passing leaf atoms (atoms that match the change). */
  public List<String> passingAtoms;

  /** List of failing leaf atoms (atoms that do not match the change). */
  public List<String> failingAtoms;

  /**
   * Explanations for why atoms pass or fail.
   *
   * <p>Explanations are only available for a few atoms, for most atoms no explanation is provided.
   *
   * <p>Not set if none of the atoms has an explanation.
   */
  @Nullable public Map<String, String> atomExplanations;
}
