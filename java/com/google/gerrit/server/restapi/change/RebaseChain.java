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

package com.google.gerrit.server.restapi.change;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.CHANGE_MODIFICATION;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.changes.RebaseInput;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RebaseChainInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.GetRelatedChangesUtil;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.change.RebaseChangeOp;
import com.google.gerrit.server.change.RebaseUtil;
import com.google.gerrit.server.change.RelatedChangesSorter.PatchSetData;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

/** Rest API for rebasing an ancestry chain of changes. */
@Singleton
public class RebaseChain
    implements RestModifyView<ChangeResource, RebaseInput>, UiAction<ChangeResource> {
  private static final ImmutableSet<ListChangesOption> OPTIONS =
      Sets.immutableEnumSet(ListChangesOption.CURRENT_REVISION, ListChangesOption.CURRENT_COMMIT);

  private final GitRepositoryManager repoManager;
  private final RebaseUtil rebaseUtil;
  private final GetRelatedChangesUtil getRelatedChangesUtil;
  private final ChangeResource.Factory changeResourceFactory;
  private final ChangeData.Factory changeDataFactory;
  private final PermissionBackend permissionBackend;
  private final BatchUpdate.Factory updateFactory;
  private final ChangeNotes.Factory notesFactory;
  private final ProjectCache projectCache;
  private final PatchSetUtil patchSetUtil;
  private final ChangeJson.Factory json;
  private final RebaseMetrics rebaseMetrics;
  private final IdentifiedUser.GenericFactory userFactory;

  @Inject
  RebaseChain(
      GitRepositoryManager repoManager,
      RebaseUtil rebaseUtil,
      GetRelatedChangesUtil getRelatedChangesUtil,
      ChangeResource.Factory changeResourceFactory,
      ChangeData.Factory changeDataFactory,
      PermissionBackend permissionBackend,
      BatchUpdate.Factory updateFactory,
      ChangeNotes.Factory notesFactory,
      ProjectCache projectCache,
      PatchSetUtil patchSetUtil,
      ChangeJson.Factory json,
      RebaseMetrics rebaseMetrics,
      IdentifiedUser.GenericFactory userFactory) {
    this.repoManager = repoManager;
    this.getRelatedChangesUtil = getRelatedChangesUtil;
    this.changeDataFactory = changeDataFactory;
    this.rebaseUtil = rebaseUtil;
    this.changeResourceFactory = changeResourceFactory;
    this.permissionBackend = permissionBackend;
    this.updateFactory = updateFactory;
    this.notesFactory = notesFactory;
    this.projectCache = projectCache;
    this.patchSetUtil = patchSetUtil;
    this.json = json;
    this.rebaseMetrics = rebaseMetrics;
    this.userFactory = userFactory;
  }

  @Override
  public Response<RebaseChainInfo> apply(ChangeResource tipRsrc, RebaseInput input)
      throws IOException, PermissionBackendException, RestApiException, UpdateException {
    IdentifiedUser rebaseAsUser;
    if (input.committerEmail != null) {
      // TODO: committer_email can be supported if all changes in the chain
      //  belong to the same uploader. It can be attempted in future as needed.
      throw new BadRequestException("committer_email is not supported when rebasing a chain");
    }
    if (input.onBehalfOfUploader) {
      tipRsrc.permissions().check(ChangePermission.REBASE_ON_BEHALF_OF_UPLOADER);
      if (input.allowConflicts) {
        throw new BadRequestException(
            "allow_conflicts and on_behalf_of_uploader are mutually exclusive");
      }
    } else {
      tipRsrc.permissions().check(ChangePermission.REBASE);
    }

    Project.NameKey project = tipRsrc.getProject();
    projectCache.get(project).orElseThrow(illegalState(project)).checkStatePermitsWrite();

    CurrentUser user = tipRsrc.getUser();

    boolean anyRebaseOnBehalfOfUploader = false;
    List<Change.Id> upToDateAncestors = new ArrayList<>();
    Map<Change.Id, RebaseChangeOp> rebaseOps = new LinkedHashMap<>();
    try (RefUpdateContext ctx = RefUpdateContext.open(CHANGE_MODIFICATION)) {
      try (Repository repo = repoManager.openRepository(project);
          ObjectInserter oi = repo.newObjectInserter();
          ObjectReader reader = oi.newReader();
          RevWalk rw = CodeReviewCommit.newRevWalk(reader);
          BatchUpdate bu = updateFactory.create(project, user, TimeUtil.now())) {
        List<PatchSetData> chain = getChainForCurrentPatchSet(tipRsrc);

        boolean ancestorsAreUpToDate = true;
        for (int i = 0; i < chain.size(); i++) {
          ChangeData changeData = chain.get(i).data();
          PatchSet ps = patchSetUtil.current(changeData.notes());
          if (ps == null) {
            throw new IllegalStateException(
                "current revision is missing for change " + changeData.getId());
          }

          RevisionResource revRsrc =
              new RevisionResource(changeResourceFactory.create(changeData, user), ps);
          if (input.onBehalfOfUploader
              && !revRsrc.getPatchSet().uploader().equals(revRsrc.getAccountId())) {
            rebaseAsUser =
                userFactory.runAs(
                    /*remotePeer= */ null, revRsrc.getPatchSet().uploader(), revRsrc.getUser());
            rebaseUtil.checkCanRebaseOnBehalfOf(revRsrc, input);
            revRsrc.permissions().check(ChangePermission.REBASE_ON_BEHALF_OF_UPLOADER);
            anyRebaseOnBehalfOfUploader = true;
          } else {
            rebaseAsUser = revRsrc.getUser().asIdentifiedUser();
            revRsrc.permissions().check(ChangePermission.REBASE);
          }
          rebaseUtil.verifyRebasePreconditions(rw, changeData.notes(), ps);

          boolean isUpToDate = false;
          RebaseChangeOp rebaseOp = null;
          if (i == 0) {
            ObjectId desiredBase =
                rebaseUtil.parseOrFindBaseRevision(
                    repo, rw, permissionBackend, revRsrc, input, false);
            if (currentBase(rw, ps).equals(desiredBase)) {
              isUpToDate = true;
            } else {
              rebaseOp = rebaseUtil.getRebaseOp(rw, revRsrc, input, desiredBase, rebaseAsUser);
            }
          } else {
            if (ancestorsAreUpToDate) {
              ObjectId latestCommittedBase =
                  PatchSetUtil.getCurrentCommittedRevCommit(
                      project, rw, notesFactory, chain.get(i - 1).id());
              isUpToDate = currentBase(rw, ps).equals(latestCommittedBase);
            }
            if (!isUpToDate) {
              rebaseOp =
                  rebaseUtil.getRebaseOp(rw, revRsrc, input, chain.get(i - 1).id(), rebaseAsUser);
            }
          }

          if (isUpToDate) {
            upToDateAncestors.add(changeData.getId());
            continue;
          }
          ancestorsAreUpToDate = false;
          bu.addOp(revRsrc.getChange().getId(), rebaseAsUser, rebaseOp);
          rebaseOps.put(revRsrc.getChange().getId(), rebaseOp);
        }

        if (ancestorsAreUpToDate) {
          throw new ResourceConflictException("The whole chain is already up to date.");
        }

        bu.setNotify(NotifyResolver.Result.none());
        bu.setRepository(repo, rw, oi);
        bu.execute();
      }
    }

    rebaseMetrics.countRebaseChain(anyRebaseOnBehalfOfUploader, input.allowConflicts);

    RebaseChainInfo res = new RebaseChainInfo();
    res.rebasedChanges = new ArrayList<>();
    ChangeJson changeJson = json.create(OPTIONS);
    for (Change.Id c : upToDateAncestors) {
      res.rebasedChanges.add(changeJson.format(project, c));
    }
    for (Map.Entry<Change.Id, RebaseChangeOp> e : rebaseOps.entrySet()) {
      Change.Id id = e.getKey();
      RebaseChangeOp op = e.getValue();
      ChangeInfo changeInfo = changeJson.format(project, id);
      changeInfo.containsGitConflicts =
          !op.getRebasedCommit().getFilesWithGitConflicts().isEmpty() ? true : null;
      res.rebasedChanges.add(changeInfo);
    }
    if (res.rebasedChanges.stream()
        .anyMatch(i -> i.containsGitConflicts != null && i.containsGitConflicts)) {
      res.containsGitConflicts = true;
    }
    return Response.ok(res);
  }

  @Override
  public Description getDescription(ChangeResource tipRsrc) throws Exception {
    UiAction.Description description =
        new UiAction.Description()
            .setLabel("Rebase Chain")
            .setTitle(
                "Rebase the ancestry chain onto the tip of the target branch. Makes you the "
                    + "uploader of the changes which can affect validity of approvals.")
            .setVisible(false);

    Change tip = tipRsrc.getChange();
    if (!tip.isNew()) {
      return description;
    }
    if (!projectCache
        .get(tipRsrc.getProject())
        .orElseThrow(illegalState(tipRsrc.getProject()))
        .statePermitsWrite()) {
      return description;
    }

    if (patchSetUtil.isPatchSetLocked(tipRsrc.getNotes())) {
      return description;
    }

    boolean visible = true;
    boolean enabled;
    try (Repository repo = repoManager.openRepository(tipRsrc.getProject());
        RevWalk rw = new RevWalk(repo)) {
      List<PatchSetData> chain = getChainForCurrentPatchSet(tipRsrc);
      if (chain.size() <= 1) {
        return description;
      }
      PatchSetData oldestAncestor = chain.get(0);
      enabled =
          rebaseUtil.canRebase(
              oldestAncestor.patchSet(), oldestAncestor.data().change().getDest(), repo, rw);

      ImmutableList<RevisionResource> chainAsRevisionResources =
          chain.stream()
              .map(
                  ps ->
                      new RevisionResource(
                          changeResourceFactory.create(ps.data(), tipRsrc.getUser()),
                          ps.patchSet()))
              .collect(toImmutableList());

      boolean canRebase =
          chainAsRevisionResources.stream()
              .allMatch(psRsrc -> psRsrc.permissions().testOrFalse(ChangePermission.REBASE));
      boolean canRebaseOnBehalfOfUploader =
          chainAsRevisionResources.stream()
              .allMatch(
                  psRsrc ->
                      psRsrc
                          .permissions()
                          .testOrFalse(ChangePermission.REBASE_ON_BEHALF_OF_UPLOADER));

      if (!canRebase && !canRebaseOnBehalfOfUploader) {
        visible = false;
      } else {
        for (RevisionResource psRsrc : chainAsRevisionResources) {
          if (patchSetUtil.isPatchSetLocked(psRsrc.getNotes())
              || !RebaseUtil.hasOneParent(rw, psRsrc.getPatchSet())) {
            enabled = false;
            break;
          }
        }
      }

      return description
          .setVisible(visible)
          .setOption("rebase", canRebase)
          .setOption("rebase_on_behalf_of_uploader", canRebaseOnBehalfOfUploader)
          .setEnabled(enabled);
    }
  }

  private ObjectId currentBase(RevWalk rw, PatchSet ps) throws IOException {
    return rw.parseCommit(ps.commitId()).getParent(0);
  }

  private List<PatchSetData> getChainForCurrentPatchSet(ChangeResource rsrc)
      throws PermissionBackendException, IOException {
    List<PatchSetData> ancestors =
        Lists.reverse(
            getRelatedChangesUtil.getAncestors(
                changeDataFactory.create(rsrc.getNotes()),
                patchSetUtil.current(rsrc.getNotes()),
                true));
    int eldestOpenAncestor = 0;
    for (PatchSetData ps : ancestors) {
      if (ps.data().change().isMerged()) {
        eldestOpenAncestor++;
      }
    }
    return ancestors.subList(eldestOpenAncestor, ancestors.size());
  }
}
