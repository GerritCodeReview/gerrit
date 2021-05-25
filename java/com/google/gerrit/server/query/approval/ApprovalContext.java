// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.query.approval;

import com.google.auto.value.AutoValue;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Project;

/** Entity representing all required information to match predicates for copying approvals. */
@AutoValue
public abstract class ApprovalContext {
  /** Project that approvals are copied in. */
  public abstract Project.NameKey project();

  /** Approval on the source patch set to be copied. */
  public abstract PatchSetApproval patchSetApproval();

  /** Target change and patch set for the approval. */
  public abstract PatchSet.Id target();

  public static ApprovalContext create(
      Project.NameKey project, PatchSetApproval psa, PatchSet.Id id) {
    return new AutoValue_ApprovalContext(project, psa, id);
  }
}
