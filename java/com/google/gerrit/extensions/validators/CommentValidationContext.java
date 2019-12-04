// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.extensions.validators;

import com.google.auto.value.AutoValue;

/**
 * Holds a comment validators context in order to pass it to a validation plugin.
 *
 * @see CommentValidator
 */
@AutoValue
public abstract class CommentValidationContext {

  public static CommentValidationContext create(int changeId, String projectId) {
    return new AutoValue_CommentValidationContext(changeId, projectId);
  }

  public abstract int getChangeId();

  public abstract String getProjectId();
}
