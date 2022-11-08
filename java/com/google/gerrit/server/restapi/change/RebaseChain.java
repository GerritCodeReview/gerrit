package com.google.gerrit.server.restapi.change;

import static com.google.gerrit.server.project.ProjectCache.illegalState;

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
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.CurrentUser;
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
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
  private final ProjectCache projectCache;
  private final PatchSetUtil patchSetUtil;
  private final ChangeJson.Factory json;

  @Inject
  RebaseChain(
      GitRepositoryManager repoManager,
      RebaseUtil rebaseUtil,
      GetRelatedChangesUtil getRelatedChangesUtil,
      ChangeResource.Factory changeResourceFactory,
      ChangeData.Factory changeDataFactory,
      PermissionBackend permissionBackend,
      BatchUpdate.Factory updateFactory,
      ProjectCache projectCache,
      PatchSetUtil patchSetUtil,
      ChangeJson.Factory json) {
    this.repoManager = repoManager;
    this.getRelatedChangesUtil = getRelatedChangesUtil;
    this.changeDataFactory = changeDataFactory;
    this.rebaseUtil = rebaseUtil;
    this.changeResourceFactory = changeResourceFactory;
    this.permissionBackend = permissionBackend;
    this.updateFactory = updateFactory;
    this.projectCache = projectCache;
    this.patchSetUtil = patchSetUtil;
    this.json = json;
  }

  @Override
  public Response<RebaseChainInfo> apply(ChangeResource tipRsrc, RebaseInput input)
      throws RestApiException, UpdateException, IOException, PermissionBackendException {
    Project.NameKey project = tipRsrc.getProject();
    CurrentUser user = tipRsrc.getUser();

    RebaseChainInfo res = new RebaseChainInfo();
    res.rebasedChanges = new ArrayList<>();
    Map<Change.Id, RebaseChangeOp> rebaseOps = new HashMap<>();
    try (Repository repo = repoManager.openRepository(project);
        ObjectInserter oi = repo.newObjectInserter();
        ObjectReader reader = oi.newReader();
        RevWalk rw = CodeReviewCommit.newRevWalk(reader);
        BatchUpdate bu = updateFactory.create(project, user, TimeUtil.now())) {
      List<PatchSetData> chain = getChainForCurrentPatchSet(tipRsrc);
      for (int i = 0; i < chain.size(); i++) {
        ChangeData changeData = chain.get(i).data();
        PatchSet ps = patchSetUtil.current(changeData.notes());
        if (ps == null) {
          throw new ResourceConflictException(
              "current revision is missing for change " + changeData.getId());
        }

        RevisionResource revRsrc =
            new RevisionResource(changeResourceFactory.create(changeData, user), ps);
        RebaseUtil.verifyRebasePreconditions(projectCache, patchSetUtil, rw, revRsrc);

        RebaseChangeOp rebaseOp;
        if (i == 0) {
          rebaseOp =
              rebaseUtil.getRebaseOp(
                  revRsrc,
                  input,
                  rebaseUtil.parseOrFindBaseRevision(repo, rw, permissionBackend, revRsrc, input));
        } else {
          rebaseOp = rebaseUtil.getRebaseOp(revRsrc, input, chain.get(i - 1).id());
        }
        bu.addOp(revRsrc.getChange().getId(), rebaseOp);
        rebaseOps.put(revRsrc.getChange().getId(), rebaseOp);
      }

      bu.setNotify(NotifyResolver.Result.none());
      bu.setRepository(repo, rw, oi);
      bu.execute();
    }

    ChangeJson changeJson = json.create(OPTIONS);
    for (Map.Entry<Change.Id, RebaseChangeOp> e : rebaseOps.entrySet()) {
      Change.Id id = e.getKey();
      RebaseChangeOp op = e.getValue();
      ChangeInfo changeInfo = changeJson.format(tipRsrc.getProject(), id);
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
    boolean enabled = true;
    try (Repository repo = repoManager.openRepository(tipRsrc.getProject());
        RevWalk rw = new RevWalk(repo)) {
      List<PatchSetData> chain = getChainForCurrentPatchSet(tipRsrc);
      PatchSetData oldestAncestor = chain.get(0);
      if (rebaseUtil.canRebase(
          oldestAncestor.patchSet(), oldestAncestor.data().change().getDest(), repo, rw)) {
        enabled = false;
      }

      for (PatchSetData ps : chain) {
        RevisionResource psRsrc =
            new RevisionResource(
                changeResourceFactory.create(ps.data(), tipRsrc.getUser()), ps.patchSet());

        if (!psRsrc.permissions().testOrFalse(ChangePermission.REBASE)) {
          visible = false;
          break;
        }

        if (patchSetUtil.isPatchSetLocked(psRsrc.getNotes())) {
          enabled = false;
        }
        if (!RebaseUtil.hasOneParent(rw, psRsrc.getPatchSet())) {
          enabled = false;
        }
      }
    }
    return description.setVisible(visible).setEnabled(enabled);
  }

  private List<PatchSetData> getChainForCurrentPatchSet(ChangeResource rsrc)
      throws PermissionBackendException, IOException {
    return Lists.reverse(
        getRelatedChangesUtil.getAncestors(
            changeDataFactory.create(rsrc.getNotes()),
            patchSetUtil.current(rsrc.getNotes()),
            true));
  }
}
