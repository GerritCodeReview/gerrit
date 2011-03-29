// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc.project.constraints;

import com.google.gerrit.reviewdb.ApprovalCategory;

/**
 * Thrown when an "allow" key of an "access" (sub)section in gerrit.config
 * contains an invalid value, like for example an non-parseable lower/upper
 * range number.
 */
class InvalidConstraintException extends Exception {

  private static final long serialVersionUID = 1L;

  private String invalidAllowValue;

  private final ApprovalCategory.Id categoryId;

  InvalidConstraintException(String invalidAllowValue,
      ApprovalCategory.Id categoryId, Throwable cause) {
    super("Invalid allow constraint \"" + invalidAllowValue + "\" for category "
        + categoryId.get(), cause);
    this.invalidAllowValue = invalidAllowValue;
    this.categoryId = categoryId;
  }

  public ApprovalCategory.Id getCategoryId() {
    return categoryId;
  }

  public String getInvalidAllowValue() {
    return invalidAllowValue;
  }
}
