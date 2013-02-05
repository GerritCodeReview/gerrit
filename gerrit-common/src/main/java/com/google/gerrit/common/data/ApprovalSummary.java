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

import com.google.gerrit.reviewdb.client.PatchSetApproval;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Summarizes the approvals (or negative approvals) for a patch set.
 * This will typically contain zero or one approvals for each
 * category, with all of the approvals coming from a single patch set.
 */
public class ApprovalSummary {
  protected Map<String, PatchSetApproval> approvals;

  protected ApprovalSummary() {
  }

  public ApprovalSummary(final Iterable<PatchSetApproval> list) {
    approvals = new HashMap<String, PatchSetApproval>();
    for (final PatchSetApproval a : list) {
      approvals.put(a.getCategoryId().get(), a);
    }
  }

  // TODO: Convert keys to label names.
  /** @return a map of approvals keyed by ID string. */
  public Map<String, PatchSetApproval> getApprovalMap() {
    return Collections.unmodifiableMap(approvals);
  }
}
