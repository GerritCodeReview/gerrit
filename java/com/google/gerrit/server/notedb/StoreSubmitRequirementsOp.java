package com.google.gerrit.server.notedb;

import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.server.experiments.ExperimentFeatures;
import com.google.gerrit.server.experiments.ExperimentFeaturesConstants;
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
      ExperimentFeatures experimentFeatures,
      OnStoreSubmitRequirementResultModifier onStoreSubmitRequirementResultModifier,
      @Assisted Collection<SubmitRequirementResult> submitRequirementResults,
      @Assisted ChangeData changeData) {
    this.onStoreSubmitRequirementResultModifier = onStoreSubmitRequirementResultModifier;
    this.submitRequirementResults = submitRequirementResults;
    this.changeData = changeData;
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
