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

package com.google.gerrit.common.errors;

import java.util.List;

/**
 * Thrown when an access right is forbidden to be assigned due to the
 * {@link ApprovalCategoryConstraintsConfig}
 */
public class ForbiddenRefRightException extends Exception {

  private static final long serialVersionUID = 1L;

  public static final String MESSAGE = ForbiddenRefRightException.class
      .getName();

  private final List<String> allows;

  /**
   * @param allows The list of allow values from gerrit.config for a category.
   */
  public ForbiddenRefRightException(List<String> allows) {
    this.allows = allows;
  }

  @Override
  public String getMessage() {
    if (allows.isEmpty()) {
      return MESSAGE;
    }
    StringBuilder b = new StringBuilder();
    for (String a : allows) {
      b.append(" [").append(a).append("]");
    }
    return MESSAGE + b.toString();
  }
}
