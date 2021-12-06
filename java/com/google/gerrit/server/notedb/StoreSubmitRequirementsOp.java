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

package com.google.gerrit.server.notedb;

import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.server.experiments.ExperimentFeatures;
import com.google.gerrit.server.experiments.ExperimentFeaturesConstants;
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

  public interface Factory {
    StoreSubmitRequirementsOp create(Collection<SubmitRequirementResult> submitRequirements);
  }

  @Inject
  public StoreSubmitRequirementsOp(
      ExperimentFeatures experimentFeatures,
      @Assisted Collection<SubmitRequirementResult> submitRequirementResults) {
    this.submitRequirementResults = submitRequirementResults;
    this.storeRequirementsInNoteDb =
        experimentFeatures.isFeatureEnabled(
            ExperimentFeaturesConstants
                .GERRIT_BACKEND_REQUEST_FEATURE_STORE_SUBMIT_REQUIREMENTS_ON_MERGE);
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws Exception {
    if (!storeRequirementsInNoteDb) {
      // Temporarily stop storing submit requirements in NoteDb when the change is merged.
      return false;
    }
    ChangeUpdate update = ctx.getUpdate(ctx.getChange().currentPatchSetId());
    List<SubmitRequirementResult> nonLegacySubmitRequirements =
        submitRequirementResults.stream()
            // We don't store results for legacy submit requirements in NoteDb. While
            // surfacing submit requirements for closed changes, we load submit records
            // from NoteDb and convert them to submit requirement results. See
            // ChangeData#submitRequirements().
            .filter(srResult -> !srResult.isLegacy())
            .collect(Collectors.toList());
    update.putSubmitRequirementResults(nonLegacySubmitRequirements);
    return !submitRequirementResults.isEmpty();
  }
}
