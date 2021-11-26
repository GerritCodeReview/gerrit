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
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.util.Collection;

/** A {@link BatchUpdateOp} that stores the evaluated submit requirements of a change in NoteDb. */
public class StoreSubmitRequirementsOp implements BatchUpdateOp {
  private final boolean storeRequirementsInNoteDb;
  private final Collection<SubmitRequirementResult> submitRequirementResults;

  public interface Factory {
    StoreSubmitRequirementsOp create(Collection<SubmitRequirementResult> submitRequirements);
  }

  @AssistedInject
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
    update.putSubmitRequirementResults(submitRequirementResults);
    return !submitRequirementResults.isEmpty();
  }
}
