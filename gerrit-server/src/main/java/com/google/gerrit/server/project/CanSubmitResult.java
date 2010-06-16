// Copyright (C) 2010 The Android Open Source Project
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

/**
 * Result from {@code ChangeControl.canSubmit()}.
 *
 * @see ChangeControl#canSubmit(com.google.gerrit.reviewdb.PatchSet.Id,
 *      com.google.gerrit.reviewdb.ReviewDb,
 *      com.google.gerrit.common.data.ApprovalTypes,
 *      com.google.gerrit.server.workflow.FunctionState.Factory)
 */
public class CanSubmitResult {
  /** Magic constant meaning submitting is possible. */
  public static final CanSubmitResult OK = new CanSubmitResult("OK");

  private final String errorMessage;

  CanSubmitResult(String error) {
    this.errorMessage = error;
  }

  public String getMessage() {
    return errorMessage;
  }

  @Override
  public String toString() {
    return "CanSubmitResult[" + getMessage() + "]";
  }
}
