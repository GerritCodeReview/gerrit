package com.google.gerrit.server.restapi.change;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Change.Id;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.api.changes.RebaseInput;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RebaseChainInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.change.RebaseChangeOp;
import com.google.gerrit.server.change.RebaseUtil;
import com.google.gerrit.server.change.RelatedChangesSorter;
import com.google.gerrit.server.change.RelatedChangesSorter.PatchSetData;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.change.ValidationOptionsUtil;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.GitRepositoryManager;
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

public class RebaseChain implements RestModifyView<RevisionResource, RebaseInput> {
  private static final ImmutableSet<ListChangesOption> OPTIONS =
      Sets.immutableEnumSet(ListChangesOption.CURRENT_REVISION, ListChangesOption.CURRENT_COMMIT);

  private final GitRepositoryManager repoManager;
  private final RebaseUtil rebaseUtil;
  private final Provider<MergeSuperSet> mergeSuperSet;
  private final RebaseChangeOp.Factory rebaseFactory;
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
      RebaseChangeOp.Factory rebaseFactory,
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
    this.rebaseFactory = rebaseFactory;
    this.changeResourceFactory = changeResourceFactory;
    this.permissionBackend = permissionBackend;
    this.updateFactory = updateFactory;
    this.projectCache = projectCache;
    this.patchSetUtil = patchSetUtil;
    this.json = json;
    this.sorter = sorter;
  }

  @Override
  public Response<RebaseChainInfo> apply(RevisionResource rsrc, RebaseInput input)
      throws RestApiException, UpdateException, IOException, PermissionBackendException {
    ChangeSet cs =
        mergeSuperSet
            .get()
            .completeChangeSet(
                rsrc.getChange(), rsrc.getUser(), /*includingTopicClosure= */ false);
    RebaseChainInfo res = new RebaseChainInfo();
    res.rebasedChanges = new ArrayList<>();
    Map<String, RebaseChangeOp> rebaseOps = new HashMap<>();
    try (Repository repo = repoManager.openRepository(rsrc.getProject());
        ObjectInserter oi = repo.newObjectInserter();
        ObjectReader reader = oi.newReader();
        RevWalk rw = CodeReviewCommit.newRevWalk(reader);
        BatchUpdate bu =
            updateFactory.create(rsrc.getProject(), rsrc.getUser(), TimeUtil.now())) {

      List<PatchSetData> chain =
          Lists.reverse(sorter.sort(cs.changes().asList(), rsrc.getPatchSet()));
      for (int i = 0; i < chain.size(); i++) {
        PatchSetData patchSetData = chain.get(i);
        PatchSet ps = patchSetData.patchSet();
        if (ps == null) {
          throw new ResourceConflictException(
              "current revision is missing for change " + patchSetData.id());
        }
        RevisionResource revRsrc =
            new RevisionResource(
                changeResourceFactory.create(patchSetData.data(), rsrc.getUser()), ps);
        RebaseUtil.verifyRebasePreconditions(projectCache, patchSetUtil, rw, revRsrc);

        RebaseChangeOp rebaseOp;
        if (i == 0) {
          rebaseOp =
              rebaseFactory.create(
                  revRsrc.getNotes(),
                  revRsrc.getPatchSet(),
                  RebaseUtil.findBaseRevision(repo, rw, rebaseUtil, permissionBackend, revRsrc,
                      input));
        } else {
          rebaseOp =
              rebaseFactory.create(
                  revRsrc.getNotes(), revRsrc.getPatchSet(), chain.get(i - 1).id());
        }
        rebaseOp =
            rebaseOp
                .setForceContentMerge(true)
                .setAllowConflicts(input.allowConflicts)
                .setValidationOptions(
                    ValidationOptionsUtil.getValidateOptionsAsMultimap(input.validationOptions))
                .setFireRevisionCreated(true);
        bu.addOp(revRsrc.getChange().getId(), rebaseOp);
        ChangeInfo changeInfo = json.create(OPTIONS)
            .format(rsrc.getProject(), patchSetData.id());
        rebaseOps.put(changeInfo.changeId, rebaseOp);
        res.rebasedChanges.add(changeInfo);
      }

      // TODO(dborowitz): Why no notification? This seems wrong; dig up blame.
      bu.setNotify(NotifyResolver.Result.none());
      bu.setRepository(repo, rw, oi);
      bu.execute();
    }
    for(ChangeInfo changeInfo : res.rebasedChanges) {
      changeInfo.containsGitConflicts =
          !rebaseOps.get(changeInfo.changeId).getRebasedCommit().getFilesWithGitConflicts().isEmpty() ? true : null;
    }
    res.containsGitConflicts =
        res.rebasedChanges.stream()
            .anyMatch(i -> i.containsGitConflicts != null && i.containsGitConflicts);
    return Response.ok(res);
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
