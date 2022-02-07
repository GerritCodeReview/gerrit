// Copyright (C) 2022 The Android Open Source Project
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

import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.ChangeContext;
import com.google.inject.ImplementedBy;

/**
 * Extension point that allows to modify the {@link SubmitRequirementResult} when it is stored
 * NoteDB.
 *
 * <p>The submit requirements are only stored for the closed (merged and abandoned) changes and the
 * modifier only affects what {@link SubmitRequirementResult} will be stored in NoteDB.
 *
 * <p>It has no impact on open changes or evaluations on merge, i.e. does not affect the
 * submittability of the change (never blocks the ready change from submission or allows bypassing
 * unsatisfied submit requirement).
 *
 * <p>The extension point only applies to non-legacy submit requirements (including non-applicable,
 * since they are stored too) and does not affect submit rule results.
 */
@ExtensionPoint
@ImplementedBy(OnStoreSubmitRequirementResultModifierImpl.class)
public interface OnStoreSubmitRequirementResultModifier {

  /**
   * Evaluate a single {@link SubmitRequirement} using the change data, modifying the original
   * {@code SubmitRequirementResult}, if needed.
   *
   * <p>Only affects how the submit requirement is stored in NoteDb for closed changes.
   */
  SubmitRequirementResult modifyResultOnStore(
      SubmitRequirement submitRequirement,
      SubmitRequirementResult submitRequirementResult,
      ChangeData changeData,
      ChangeContext ctx);
}
