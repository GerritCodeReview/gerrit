// Copyright (C) 2023 The Android Open Source Project
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

import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.entities.SubmitTypeRecord;
import com.google.gerrit.server.query.change.ChangeData;

/** Provides prolog-related operations to different callers. */
public interface PrologSubmitRuleUtil {
  /** Returns true if prolog rules are enabled for the project. */
  boolean isProjectRulesEnabled();

  /**
   * Returns the submit-type of a change depending on the change data and the definition of the
   * prolog rules file.
   *
   * <p>Must only be called when Prolog rules are enabled on the Gerrit server.
   */
  SubmitTypeRecord getSubmitType(ChangeData cd);

  /**
   * Returns the submit-type of a change depending on the change data and the definition of the
   * prolog rules file.
   *
   * <p>Must only be called when Prolog rules are enabled on the Gerrit server.
   */
  SubmitTypeRecord getSubmitType(ChangeData cd, String ruleToTest, boolean skipFilters);

  /**
   * Evaluates a submit rule.
   *
   * <p>Must only be called when Prolog rules are enabled on the Gerrit server.
   */
  SubmitRecord evaluate(ChangeData cd, String ruleToTest, boolean skipFilters);
}
