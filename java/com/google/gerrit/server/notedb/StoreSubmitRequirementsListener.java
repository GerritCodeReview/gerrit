package com.google.gerrit.server.notedb;

import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.extensions.events.ChangeMergedListener;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.InternalUser;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;

// TODO: Implement this synchronously in the same transaction when the change is merged
public class StoreSubmitRequirementsListener implements ChangeMergedListener {

  private final BatchUpdate.Factory updateFactory;
  private final InternalUser.Factory internalUserFactory;

  @Inject
  public StoreSubmitRequirementsListener(
      BatchUpdate.Factory updateFactory, InternalUser.Factory internalUserFactory) {
    this.updateFactory = updateFactory;
    this.internalUserFactory = internalUserFactory;
  }

  @Override
  public void onChangeMerged(Event event) {
    // Store submit requirements in notes
    // Which code paths store revision note map?
    // 1. ChangeUpdate
    /**
     * Store submit requirements in notes which code paths store revision note map? 1. ChangeUpdate
     * 2. ChangeDraftUpdate 3. RobotCommentUpdate
     *
     * <p>(2) ChangeDraftUpdate is storing drafts under ref, e.g.
     * refs/draft-comments/95/233595/1087981 the revision note map is still same: we create one file
     * with name = commit ID, and content are json containing the comment entities
     *
     * <p>{@link NoteDbUpdateManager} is responsible of handling all three updates
     *
     * <p>We have separate classes for ChangeDraft and RobotComment because they are stored in
     * different refs, for Submit requirements, I believe the logic should be implemented in
     * ChangeUpdate.
     *
     * <p>What we want to do: * create a ChangeUpdate with the designated update that contains the
     * submit requirements * execute the update
     */
    String changeId = event.getChange().changeId;
    String project = event.getChange().project;
    try (BatchUpdate bu =
        updateFactory.create(
            Project.nameKey(project), internalUserFactory.create(), TimeUtil.nowTs())) {
      bu.addOp(Change.Id.tryParse(changeId).get(), new StoreSubmitRequirementsOp());
      bu.execute();
    } catch (RestApiException | UpdateException e) {
      // log a warning message
    }
  }

  static class StoreSubmitRequirementsOp implements BatchUpdateOp {

    ChangeData.Factory changeDataFactory;

    @Override
    public boolean updateChange(ChangeContext ctx) throws Exception {
      Change change = ctx.getChange();

      // Get the submit requirements
      ChangeData changeData = changeDataFactory.create(change);
      ChangeUpdate update = ctx.getUpdate(change.currentPatchSetId());
      for (SubmitRequirementResult rs : changeData.submitRequirements().values()) {
        update.putSubmitRequirementResult(rs);
      }
      return false;
    }
  }
}
