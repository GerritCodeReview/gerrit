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

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.experiments.ExperimentFeatures;
import com.google.gerrit.server.experiments.ExperimentFeaturesConstants;
import com.google.gerrit.server.project.SubmitRequirementsEvaluator;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.Config;

/** A {@link BatchUpdateOp} that stores the evaluated submit requirements of a change in NoteDb. */
public class StoreSubmitRequirementsOp implements BatchUpdateOp {
  private final ChangeData.Factory changeDataFactory;
  private final ExperimentFeatures experimentFeatures;
  private final SubmitRequirementsEvaluator evaluator;
  private final boolean storeRequirementsInNoteDb;

  public interface Factory {
    StoreSubmitRequirementsOp create();
  }

  @Inject
  public StoreSubmitRequirementsOp(
      @GerritServerConfig Config cfg,
      ChangeData.Factory changeDataFactory,
      ExperimentFeatures experimentFeatures,
      SubmitRequirementsEvaluator evaluator) {
    this.changeDataFactory = changeDataFactory;
    this.experimentFeatures = experimentFeatures;
    this.evaluator = evaluator;
    this.storeRequirementsInNoteDb =
        cfg.getBoolean("change", null, "storeSubmitRequirementsInNoteDb", false);
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws Exception {
    if (!storeRequirementsInNoteDb
        || !experimentFeatures.isFeatureEnabled(
            ExperimentFeaturesConstants
                .GERRIT_BACKEND_REQUEST_FEATURE_ENABLE_LEGACY_SUBMIT_REQUIREMENTS)) {
      // Temporarily stop storing submit requirements in NoteDb when the change is merged.
      return false;
    }
    // Create ChangeData using the project/change IDs instead of ctx.getChange(). We do that because
    // for changes requiring a rebase before submission (e.g. if submit type = RebaseAlways), the
    // RebaseOp inserts a new patchset that is visible here (via Change#getCurrentPatchset). If we
    // then try to get ChangeData#currentPatchset it will return null, since it loads patchsets from
    // NoteDb but tries to find the patchset with the ID of the one just inserted by the rebase op.
    // Note that this implementation means that, in this case, submit requirement results will be
    // stored in change notes of the pre last patchset commit. This is fine since submit requirement
    // results should evaluate to the exact same results for both commits. Additionally, the
    // pre-last commit is the one for which we displayed the submit requirement results of the last
    // patchset to the user before it was merged.
    ChangeData changeData = changeDataFactory.create(ctx.getProject(), ctx.getChange().getId());
    ChangeUpdate update = ctx.getUpdate(ctx.getChange().currentPatchSetId());
    // We do not want to store submit requirements in NoteDb for legacy submit records
    update.putSubmitRequirementResults(
        evaluator.evaluateAllRequirements(changeData, /* includeLegacy= */ false).values());
    return !changeData.submitRequirements().isEmpty();
  }
}
