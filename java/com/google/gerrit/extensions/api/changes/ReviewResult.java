// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.extensions.api.changes;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.common.ChangeInfo;
import java.util.Map;

/** Result object representing the outcome of a review request. */
public class ReviewResult {
  /**
   * Map of labels to values after the review was posted. Null if any reviewer additions were
   * rejected.
   */
  @Nullable public Map<String, Short> labels;

  /**
   * Map of account or group identifier to outcome of adding as a reviewer. Null if no reviewer
   * additions were requested.
   */
  @Nullable public Map<String, ReviewerResult> reviewers;

  /**
   * Boolean indicating whether the change was moved out of WIP by this review. Either true or null.
   */
  @Nullable public Boolean ready;

  /** Error message for non-200 responses. */
  @Nullable public String error;

  /** Change after applying the update. */
  @Nullable public ChangeInfo changeInfo;
}
