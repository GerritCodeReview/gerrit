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

package com.google.gerrit.server.notedb;

import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.server.project.OnStoreSubmitRequirementResultModifier;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/** A {@link BatchUpdateOp} that stores the evaluated submit requirements of a change in NoteDb. */
public class StoreSubmitRequirementsOp implements BatchUpdateOp {
  private final boolean storeRequirementsInNoteDb;
  private final Collection<SubmitRequirementResult> submitRequirementResults;
  private final ChangeData changeData;
  private final OnStoreSubmitRequirementResultModifier onStoreSubmitRequirementResultModifier;

  public interface Factory {

    /**
     * {@code submitRequirements} are explicitly passed to the operation so that they are evaluated
     * before the {@link #updateChange} is called.
     *
     * <p>This is because the return results of {@link ChangeData#submitRequirements()} depend on
     * the status of the change, which can be modified by other {@link BatchUpdateOp}, sharing the
     * same {@link ChangeContext}.
     */
    StoreSubmitRequirementsOp create(
        Collection<SubmitRequirementResult> submitRequirements, ChangeData changeData);
  }

  @Inject
  public StoreSubmitRequirementsOp(
      OnStoreSubmitRequirementResultModifier onStoreSubmitRequirementResultModifier,
      @Assisted Collection<SubmitRequirementResult> submitRequirementResults,
      @Assisted ChangeData changeData) {
    this.onStoreSubmitRequirementResultModifier = onStoreSubmitRequirementResultModifier;
    this.submitRequirementResults = submitRequirementResults;
    this.changeData = changeData;
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws Exception {
    ChangeUpdate update = ctx.getUpdate(ctx.getChange().currentPatchSetId());
    List<SubmitRequirementResult> nonLegacySubmitRequirements =
        submitRequirementResults.stream()
            // We don't store results for legacy submit requirements in NoteDb. While
            // surfacing submit requirements for closed changes, we load submit records
            // from NoteDb and convert them to submit requirement results. See
            // ChangeData#submitRequirements().
            .filter(srResult -> !srResult.isLegacy())
            .map(
                // Pass to OnStoreSubmitRequirementResultModifier for override
                srResult ->
                    onStoreSubmitRequirementResultModifier.modifyResultOnStore(
                        srResult.submitRequirement(), srResult, changeData, ctx))
            .collect(Collectors.toList());
    update.putSubmitRequirementResults(nonLegacySubmitRequirements);
    return !nonLegacySubmitRequirements.isEmpty();
  }
}
