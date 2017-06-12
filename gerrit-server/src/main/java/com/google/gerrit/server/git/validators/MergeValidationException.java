// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.git.validators;

import com.google.gerrit.server.validators.ValidationException;

/**
 * Exception that occurs during a validation step before merging changes.
 *
 * <p>Used by {@link MergeValidationListener}s provided by plugins. Messages should be considered
 * human-readable.
 */
public class MergeValidationException extends ValidationException {
  private static final long serialVersionUID = 1L;

  public MergeValidationException(String msg) {
    super(msg);
  }
}
