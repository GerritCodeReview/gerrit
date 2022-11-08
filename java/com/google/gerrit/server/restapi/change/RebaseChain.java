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
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.change.RebaseChangeOp;
import com.google.gerrit.server.change.RebaseUtil;
import com.google.gerrit.server.change.RelatedChangesSorter;
import com.google.gerrit.server.change.RelatedChangesSorter.PatchSetData;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.submit.ChangeSet;
import com.google.gerrit.server.submit.MergeSuperSet;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

public class RebaseChain
    implements RestModifyView<RevisionResource, RebaseInput>, UiAction<RevisionResource> {
  private static final ImmutableSet<ListChangesOption> OPTIONS =
      Sets.immutableEnumSet(ListChangesOption.CURRENT_REVISION, ListChangesOption.CURRENT_COMMIT);

  private final GitRepositoryManager repoManager;
  private final RebaseUtil rebaseUtil;
  private final Provider<MergeSuperSet> mergeSuperSet;
  private final ChangeResource.Factory changeResourceFactory;
  private final PermissionBackend permissionBackend;
  private final BatchUpdate.Factory updateFactory;
  private final ProjectCache projectCache;
  private final PatchSetUtil patchSetUtil;
  private final ChangeJson.Factory json;
  private final RelatedChangesSorter sorter;

  @Inject
  RebaseChain(
      GitRepositoryManager repoManager,
      RebaseUtil rebaseUtil,
      Provider<MergeSuperSet> mergeSuperSet,
      ChangeResource.Factory changeResourceFactory,
      PermissionBackend permissionBackend,
      BatchUpdate.Factory updateFactory,
      ProjectCache projectCache,
      PatchSetUtil patchSetUtil,
      ChangeJson.Factory json,
      RelatedChangesSorter sorter) {
    this.repoManager = repoManager;
    this.rebaseUtil = rebaseUtil;
    this.mergeSuperSet = mergeSuperSet;
    this.changeResourceFactory = changeResourceFactory;
    this.permissionBackend = permissionBackend;
    this.updateFactory = updateFactory;
    this.projectCache = projectCache;
    this.patchSetUtil = patchSetUtil;
    this.json = json;
    this.sorter = sorter;
  }

  @Override
  public Response<RebaseChainInfo> apply(RevisionResource tipRsrc, RebaseInput input)
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
      List<PatchSetData> chain = getChain(tipRsrc);
      for (int i = 0; i < chain.size(); i++) {
        PatchSetData patchSetData = chain.get(i);
        PatchSet ps = patchSetData.patchSet();
        if (ps == null) {
          throw new ResourceConflictException(
              "current revision is missing for change " + patchSetData.id());
        }
        RevisionResource revRsrc =
            new RevisionResource(changeResourceFactory.create(patchSetData.data(), user), ps);
        RebaseUtil.verifyRebasePreconditions(projectCache, patchSetUtil, rw, revRsrc);

        RebaseChangeOp rebaseOp;
        if (i == 0) {
          rebaseOp =
              rebaseUtil.getRebaseOp(
                  revRsrc,
                  input,
                  RebaseUtil.findBaseRevision(
                      repo, rw, rebaseUtil, permissionBackend, revRsrc, input));
        } else {
          rebaseOp = rebaseUtil.getRebaseOp(revRsrc, input, chain.get(i - 1).id());
        }
        bu.addOp(revRsrc.getChange().getId(), rebaseOp);
        rebaseOps.put(revRsrc.getChange().getId(), rebaseOp);
      }

      // TODO(dborowitz): Why no notification? This seems wrong; dig up blame.
      bu.setNotify(NotifyResolver.Result.none());
      bu.setRepository(repo, rw, oi);
      bu.execute();
    }

    for (Map.Entry<Change.Id, RebaseChangeOp> e : rebaseOps.entrySet()) {
      Change.Id id = e.getKey();
      RebaseChangeOp op = e.getValue();
      ChangeInfo changeInfo = json.create(OPTIONS).format(tipRsrc.getProject(), id);
      changeInfo.containsGitConflicts =
          !op.getRebasedCommit().getFilesWithGitConflicts().isEmpty() ? true : null;
      res.rebasedChanges.add(changeInfo);
    }
    res.containsGitConflicts =
        res.rebasedChanges.stream()
            .anyMatch(i -> i.containsGitConflicts != null && i.containsGitConflicts);
    return Response.ok(res);
  }

  @Override
  public Description getDescription(RevisionResource tipRsrc) throws Exception {
    UiAction.Description description =
        new UiAction.Description()
            .setLabel("Rebase Chain")
            .setTitle(
                "Rebase the ancestry chain onto the tip of the target branch. Makes you the uploader of the "
                    + "changes which can affect validity of approvals.")
            .setVisible(false);

    Change tip = tipRsrc.getChange();
    if (!(tip.isNew() && tipRsrc.isCurrent())) {
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
    for (PatchSetData ps : getChain(tipRsrc)) {
      Change change = ps.data().change();
      RevisionResource psRsrc =
          new RevisionResource(
              changeResourceFactory.create(ps.data(), tipRsrc.getUser()), ps.patchSet());

      if (!psRsrc.permissions().testOrFalse(ChangePermission.REBASE)) {
        visible = false;
      }

      try (Repository repo = repoManager.openRepository(change.getDest().project());
          RevWalk rw = new RevWalk(repo)) {
        if (patchSetUtil.isPatchSetLocked(psRsrc.getNotes())) {
          enabled = false;
        }
        if (!RebaseUtil.hasOneParent(rw, psRsrc.getPatchSet())) {
          enabled = false;
        }
        if (rebaseUtil.canRebase(psRsrc.getPatchSet(), change.getDest(), repo, rw)) {
          enabled = false;
        }
      }
    }

    return description.setVisible(visible).setEnabled(enabled);
  }

  private List<PatchSetData> getChain(RevisionResource rsrc)
      throws PermissionBackendException, IOException {
    ChangeSet cs =
        mergeSuperSet
            .get()
            .completeChangeSet(rsrc.getChange(), rsrc.getUser(), /*includingTopicClosure= */ false);
    return Lists.reverse(sorter.sort(cs.changes().asList(), rsrc.getPatchSet()));
  }

  public static class CurrentRevision implements RestModifyView<ChangeResource, RebaseInput> {
    private final PatchSetUtil psUtil;
    private final RebaseChain rebaseChain;

    @Inject
    CurrentRevision(PatchSetUtil psUtil, RebaseChain rebaseChain) {
      this.psUtil = psUtil;
      this.rebaseChain = rebaseChain;
    }

    @Override
    public Response<RebaseChainInfo> apply(ChangeResource rsrc, RebaseInput input)
        throws RestApiException, UpdateException, IOException, PermissionBackendException {
      PatchSet ps = psUtil.current(rsrc.getNotes());
      if (ps == null) {
        throw new ResourceConflictException("current revision is missing");
      }
      return Response.ok(rebaseChain.apply(new RevisionResource(rsrc, ps), input).value());
    }
  }
}
