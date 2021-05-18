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

package com.google.gerrit.server;

import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.annotations.ExtensionPoint;

/**
 * Plugin interface to allow plugins to decide if a label should be copied from an older patchset to
 * a newer one.
 */
@ExtensionPoint
public interface ApprovalCopier {
  /**
   * Returns {@code true} if the provided {@link PatchSetApproval} should be copied to the {@code
   * new} patch set. If {@code true} is returned by any plugin, the approval is copied to the new
   * patchset.
   */
  boolean shouldCopyApproval(
      Project.NameKey project, PatchSetApproval patchSetApproval, PatchSet.Id newPatchSet);
}
