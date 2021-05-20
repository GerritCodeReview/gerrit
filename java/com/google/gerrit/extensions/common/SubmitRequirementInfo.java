// Copyright (C) 2022 The Android Open Source Project
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

public class SubmitRequirementInfo {
  /** Name of the submit requirement. */
  public String name;

  /** Description of the submit requirement. */
  public String description;

  /**
   * Expression string to be evaluated on a change. Decides if this submit requirement is applicable
   * on the given change.
   */
  public String applicabilityExpression;

  /**
   * Expression string to be evaluated on a change. When evaluated to true, this submit requirement
   * becomes fulfilled for this change and not blocking change submission.
   */
  public String submittabilityExpression;

  /**
   * Expression string to be evaluated on a change. When evaluated to true, this submit requirement
   * becomes fulfilled for this change regardless of the evaluation of the {@link
   * #submittabilityExpression}.
   */
  public String overrideExpression;

  /** Boolean indicating if this submit requirement can be overridden in child projects. */
  public boolean allowOverrideInChildProjects;
}
