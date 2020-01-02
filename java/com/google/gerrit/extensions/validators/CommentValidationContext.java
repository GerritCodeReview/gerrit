// Copyright (C) 2020 The Android Open Source Project
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
 * <p>This is used to provided additional context around that comment that can be used by the
 * validator to determine what validations should be run. For example, a comment validator may only
 * want to validate a comment if it's on a change in the project foo.
 *
 * @see CommentValidator
 */
@AutoValue
public abstract class CommentValidationContext {

  /** Returns the change id the comment is being added to. */
  public abstract int getChangeId();

  /** Returns the project the comment is being added to. */
  public abstract String getProject();

  public static CommentValidationContext create(int changeId, String project) {
    return new AutoValue_CommentValidationContext(changeId, project);
  }
}
