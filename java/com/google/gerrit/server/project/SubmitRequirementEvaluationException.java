// Copyright (C) 2021 The Android Open Source Project
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

import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.server.query.change.SubmitRequirementPredicate;

/**
 * Exception that might occur when evaluating {@link SubmitRequirementPredicate} in {@link
 * SubmitRequirementExpression}.
 *
 * <p>This exception will result in {@link
 * com.google.gerrit.entities.SubmitRequirementResult.Status#ERROR} overall submit requirement
 * evaluation status.
 */
public class SubmitRequirementEvaluationException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public SubmitRequirementEvaluationException(String errorMessage) {
    super(errorMessage);
  }

  public SubmitRequirementEvaluationException(String errorMessage, Throwable why) {
    super(errorMessage, why);
  }

  public SubmitRequirementEvaluationException(Throwable why) {
    super(why);
  }
}
