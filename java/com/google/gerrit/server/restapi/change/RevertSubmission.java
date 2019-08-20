package com.google.gerrit.server.restapi.change;

import static com.google.gerrit.server.api.ApiUtil.asRestApiException;

import com.google.gerrit.extensions.api.changes.RevertInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.extensions.webui.UiAction.Description;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class RevertSubmission
    implements RestModifyView<ChangeResource, RevertInput>, UiAction<ChangeResource> {

  private final Revert revert;
  private final Provider<InternalChangeQuery> queryProvider;
  private final ChangeNotes.Factory changeNotesFactory;
  private final ChangeResource.Factory changeResourceFactory;
  private final Provider<CurrentUser> user;

  @Inject
  RevertSubmission(
      Revert revert,
      Provider<InternalChangeQuery> queryProvider,
      ChangeNotes.Factory changeNotesFactory,
      ChangeResource.Factory changeResourceFactory,
      Provider<CurrentUser> user) {
    this.revert = revert;
    this.queryProvider = queryProvider;
    this.changeNotesFactory = changeNotesFactory;
    this.changeResourceFactory = changeResourceFactory;
    this.user = user;
  }

  @Override
  public Response<List<ChangeInfo>> apply(ChangeResource changeResource, RevertInput input)
      throws Exception {
    // TODO: all checks that are necessary, permissions etc. detailed in design doc solution,
    // implementation point 3.
    String submissionId = changeResource.getChange().getSubmissionId();
    List<ChangeData> changeDatas = queryProvider.get().bySubmissionId(submissionId);
    List<ChangeResource> changeResources = new ArrayList<>();
    for (ChangeData changeData : changeDatas) {
      changeResources.add(getChangeResource(changeData.getId()));
    }
    List<ChangeInfo> results = new ArrayList<>();
    for (ChangeResource change : changeResources) {
      results.add(revert.apply(change, input).value());
    }
    return Response.ok(results);
  }

  private ChangeResource getChangeResource(Change.Id changeId) throws RestApiException {
    try {
      ChangeNotes notes = changeNotesFactory.createChecked(changeId);
      return changeResourceFactory.create(notes, user.get());
    } catch (NoSuchChangeException e) {
      throw new ResourceNotFoundException(String.format("Change %d not found", changeId.get()), e);
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve change", e);
    }
  }

  // TODO: look into the code of UIAction
  @Override
  public Description getDescription(ChangeResource rsrc) {
    return new UiAction.Description()
        .setLabel("Revert all changes of the submission id of this change")
        .setTitle("Revert by submission");
  }
}
