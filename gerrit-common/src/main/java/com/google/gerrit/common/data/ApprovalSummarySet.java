// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.common.data;

import com.google.gerrit.reviewdb.Change;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Contains a set of ApprovalSummary objects, keyed by the change id
 * from which they were derived.
 */
public class ApprovalSummarySet {
  protected AccountInfoCache accounts;

  protected Map<Change.Id, ApprovalSummary> summaries;

  protected ApprovalSummarySet() {
  }

  public ApprovalSummarySet(final AccountInfoCache accts,
      final Map<Change.Id, ApprovalSummary> map) {
    accounts = accts;

    summaries = new HashMap<Change.Id, ApprovalSummary>();
    summaries.putAll(map);
  }

  public AccountInfoCache getAccountInfoCache() {
    return accounts;
  }

  public Map<Change.Id, ApprovalSummary> getSummaryMap() {
    return Collections.unmodifiableMap(summaries);
  }
}
