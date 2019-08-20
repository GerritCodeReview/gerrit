package com.google.gerrit.server.restapi.change;

import static com.google.gerrit.extensions.conditions.BooleanCondition.and;
import static com.google.gerrit.server.permissions.RefPermission.CREATE_CHANGE;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.api.changes.RevertInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.extensions.webui.UiAction.Description;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ContributorAgreementsChecker;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeQueryProcessor;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.restapi.change.RelatedChangesSorter.PatchSetData;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.RandomStringUtils;

public class RevertSubmission
    implements RestModifyView<ChangeResource, RevertInput>, UiAction<ChangeResource> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Revert revert;
  private final Provider<InternalChangeQuery> queryProvider;
  private final ChangeNotes.Factory changeNotesFactory;
  private final ChangeResource.Factory changeResourceFactory;
  private final Provider<CurrentUser> user;
  private final PermissionBackend permissionBackend;
  private final ProjectCache projectCache;
  private final PatchSetUtil psUtil;
  private final ContributorAgreementsChecker contributorAgreements;
  private final RelatedChangesSorter relatedChangesSorter;
  private final ChangeQueryBuilder changeQueryBuilder;
  private final Provider<ChangeQueryProcessor> changeQueryProcessor;

  @Inject
  RevertSubmission(
      Revert revert,
      Provider<InternalChangeQuery> queryProvider,
      ChangeNotes.Factory changeNotesFactory,
      ChangeResource.Factory changeResourceFactory,
      Provider<CurrentUser> user,
      PermissionBackend permissionBackend,
      ProjectCache projectCache,
      PatchSetUtil psUtil,
      ContributorAgreementsChecker contributorAgreements,
      RelatedChangesSorter relatedChangesSorter,
      ChangeQueryBuilder changeQueryBuilder,
      Provider<ChangeQueryProcessor> changeQueryProcessor) {
    this.revert = revert;
    this.queryProvider = queryProvider;
    this.changeNotesFactory = changeNotesFactory;
    this.changeResourceFactory = changeResourceFactory;
    this.user = user;
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;
    this.psUtil = psUtil;
    this.contributorAgreements = contributorAgreements;
    this.relatedChangesSorter = relatedChangesSorter;
    this.changeQueryBuilder = changeQueryBuilder;
    this.changeQueryProcessor = changeQueryProcessor;
  }

  @Override
  public Response<List<ChangeInfo>> apply(ChangeResource changeResource, RevertInput input)
      throws Exception {
    String submissionId = changeResource.getChange().getSubmissionId();
    if (submissionId == null) {
      throw new ResourceConflictException(
          String.format(
              "submissionId doesn't exist for changeId %s, so the change was not submitted",
              changeResource.getChange().getChangeId()));
    }
    List<ChangeData> changeDatas = queryProvider.get().bySubmissionId(submissionId);

    Map<NameKey, List<ChangeData>> changesPerProject = new HashMap<>();

    for (ChangeData changeData : changeDatas) {
      Change change = changeData.change();
      contributorAgreements.check(change.getProject(), changeResource.getUser());
      permissionBackend.user(changeResource.getUser()).ref(change.getDest()).check(CREATE_CHANGE);
      projectCache.checkedGet(change.getProject()).checkStatePermitsWrite();

      PatchSet patch = psUtil.get(changeData.notes(), change.currentPatchSetId());
      if (patch == null) {
        throw new ResourceNotFoundException(changeData.getId().toString());
      }
      Project.NameKey nameKey = changeData.change().getProject();
      if (changesPerProject.containsKey(nameKey)) {
        changesPerProject.get(nameKey).add(changeData);
      } else {
        List<ChangeData> changeDataList = new ArrayList<>();
        changeDataList.add(changeData);
        changesPerProject.put(nameKey, changeDataList);
      }
    }
    String oldTopic = changeResource.getChange().getTopic();
    if (input.topicOfRevert == null) {
      if (oldTopic != null) {
        Predicate<ChangeData> predicate = changeQueryBuilder.parse("topic:" + oldTopic + "-revert");
        List<ChangeData> changesWithTopic = changeQueryProcessor.get().query(predicate).entities();
        if (changesWithTopic.isEmpty()) {
          input.topicOfRevert = oldTopic + "-revert";
        }
      }
      input.topicOfRevert =
          input.topicOfRevert == null ? generateRandomNewTopic() : input.topicOfRevert;
    }

    List<ChangeInfo> results = new ArrayList<>();
    for (Project.NameKey project : changesPerProject.keySet()) {
      List<ChangeData> changeDataList = changesPerProject.get(project);
      PatchSet startPatchSet =
          psUtil.get(
              changeDataList.get(0).notes(), changeDataList.get(0).change().currentPatchSetId());
      List<PatchSetData> sortedChangesInProject =
          relatedChangesSorter.sort(changeDataList, startPatchSet);

      for (int i = 0; i < sortedChangesInProject.size(); i++) {
        ChangeResource change = getChangeResource(sortedChangesInProject.get(i).data().getId());
        ChangeInfo changeInfo = revert.apply(change, input).value();
        input.parentOfRevert =
            getChangeResource(Change.id(changeInfo._number))
                .getNotes()
                .getCurrentPatchSet()
                .commitId()
                .getName();
        results.add(changeInfo);
      }
      input.parentOfRevert = null;
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
      throw new BadRequestException("Cannot retrieve change", e);
    }
  }

  @Override
  public Description getDescription(ChangeResource rsrc) {
    Change change = rsrc.getChange();
    boolean projectStatePermitsWrite = false;
    try {
      projectStatePermitsWrite = projectCache.checkedGet(rsrc.getProject()).statePermitsWrite();
    } catch (IOException e) {
      logger.atSevere().withCause(e).log(
          "Failed to check if project state permits write: %s", rsrc.getProject());
    }
    return new UiAction.Description()
        .setLabel("Revert all changes of the submission id of this change")
        .setTitle("Revert by submission")
        .setVisible(
            and(
                change.isMerged() && projectStatePermitsWrite,
                permissionBackend
                    .user(rsrc.getUser())
                    .ref(change.getDest())
                    .testCond(CREATE_CHANGE)));
  }

  private String generateRandomNewTopic() throws QueryParseException {
    String result = null;
    while (result == null) {
      String topicName = RandomStringUtils.randomAlphabetic(5) + "-revert";
      Predicate<ChangeData> predicate = changeQueryBuilder.parse("topic:" + topicName);
      List<ChangeData> existingTopic = changeQueryProcessor.get().query(predicate).entities();
      if (existingTopic.isEmpty()) {
        result = topicName;
      }
    }
    return result;
  }
}
