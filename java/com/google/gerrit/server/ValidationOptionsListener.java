// Copyright (C) 2024 The Android Open Source Project
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

import com.google.common.collect.ImmutableListMultimap;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.annotations.ExtensionPoint;

/**
 * Hook to get to know about validation options that have been specified by the user.
 *
 * <p>For example, this extension point can be used to log validation options for auditing purposes.
 */
@ExtensionPoint
public interface ValidationOptionsListener {
  void onPatchSetCreation(
      BranchNameKey projectAndBranch,
      PatchSet.Id patchSetId,
      ImmutableListMultimap<String, String> validationOptions);
}
