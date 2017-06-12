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
import com.google.gerrit.extensions.common.AccountInfo;
import java.util.Map;

/** Account and approval details for an added reviewer. */
public class ReviewerInfo extends AccountInfo {
  /**
   * {@link Map} of label name to initial value for each approval the reviewer is responsible for.
   */
  @Nullable public Map<String, String> approvals;

  public ReviewerInfo(Integer id) {
    super(id);
  }

  @Override
  public String toString() {
    return username;
  }
}
