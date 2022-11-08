// Copyright (C) 2013 The Android Open Source Project
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

import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.api.changes.RebaseInput;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RebaseChainInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.change.RebaseChangeOp;
import com.google.gerrit.server.change.RebaseUtil;
import com.google.gerrit.server.change.RebaseUtil.Base;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.submit.ChangeSet;
import com.google.gerrit.server.submit.MergeSuperSet;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

@Singleton
public class Rebase
    implements RestModifyView<RevisionResource, RebaseInput>, UiAction<RevisionResource> {
  private static final ImmutableSet<ListChangesOption> OPTIONS =
      Sets.immutableEnumSet(ListChangesOption.CURRENT_REVISION, ListChangesOption.CURRENT_COMMIT);

  private final BatchUpdate.Factory updateFactory;
  private final GitRepositoryManager repoManager;
  private final RebaseChangeOp.Factory rebaseFactory;
  private final RebaseUtil rebaseUtil;
  private final ChangeJson.Factory json;
  private final PermissionBackend permissionBackend;
  private final ProjectCache projectCache;
  private final PatchSetUtil patchSetUtil;

  @Inject
  public Rebase(
      BatchUpdate.Factory updateFactory,
      GitRepositoryManager repoManager,
      RebaseChangeOp.Factory rebaseFactory,
      RebaseUtil rebaseUtil,
      ChangeJson.Factory json,
      PermissionBackend permissionBackend,
      ProjectCache projectCache,
      PatchSetUtil patchSetUtil) {
    this.updateFactory = updateFactory;
    this.repoManager = repoManager;
    this.rebaseFactory = rebaseFactory;
    this.rebaseUtil = rebaseUtil;
    this.json = json;
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;
    this.patchSetUtil = patchSetUtil;
  }

  @Override
  public Response<ChangeInfo> apply(RevisionResource rsrc, RebaseInput input)
      throws UpdateException, RestApiException, IOException, PermissionBackendException {
    Change change = rsrc.getChange();
    try (Repository repo = repoManager.openRepository(change.getProject());
        ObjectInserter oi = repo.newObjectInserter();
        ObjectReader reader = oi.newReader();
        RevWalk rw = CodeReviewCommit.newRevWalk(reader);
        BatchUpdate bu =
            updateFactory.create(change.getProject(), rsrc.getUser(), TimeUtil.now())) {
      verifyRebasePreconditions(projectCache, patchSetUtil, rw, rsrc);
      RebaseChangeOp rebaseOp =
          rebaseFactory
              .create(
                  rsrc.getNotes(),
                  rsrc.getPatchSet(),
                  findBaseRev(repo, rw, rebaseUtil, permissionBackend, rsrc, input))
              .setForceContentMerge(true)
              .setAllowConflicts(input.allowConflicts)
              .setValidationOptions(getValidateOptionsAsMultimap(input.validationOptions))
              .setFireRevisionCreated(true);
      // TODO(dborowitz): Why no notification? This seems wrong; dig up blame.
      bu.setNotify(NotifyResolver.Result.none());
      bu.setRepository(repo, rw, oi);
      bu.addOp(change.getId(), rebaseOp);
      bu.execute();

      ChangeInfo changeInfo = json.create(OPTIONS).format(change.getProject(), change.getId());
      changeInfo.containsGitConflicts =
          !rebaseOp.getRebasedCommit().getFilesWithGitConflicts().isEmpty() ? true : null;
      return Response.ok(changeInfo);
    }
  }

  private static ObjectId findBaseRev(
      Repository repo,
      RevWalk rw,
      RebaseUtil rebaseUtil,
      PermissionBackend permissionBackend,
      RevisionResource rsrc,
      RebaseInput input)
      throws RestApiException, IOException, NoSuchChangeException, AuthException,
          PermissionBackendException {
    BranchNameKey destRefKey = rsrc.getChange().getDest();
    if (input == null || input.base == null) {
      return rebaseUtil.findBaseRevision(rsrc.getPatchSet(), destRefKey, repo, rw);
    }

    Change change = rsrc.getChange();
    String str = input.base.trim();
    if (str.equals("")) {
      // Remove existing dependency to other patch set.
      Ref destRef = repo.exactRef(destRefKey.branch());
      if (destRef == null) {
        throw new ResourceConflictException(
            "can't rebase onto tip of branch " + destRefKey.branch() + "; branch doesn't exist");
      }
      return destRef.getObjectId();
    }

    Base base;
    try {
      base = rebaseUtil.parseBase(rsrc, str);
      if (base == null) {
        throw new ResourceConflictException(
            "base revision is missing from the destination branch: " + str);
      }
    } catch (NoSuchChangeException e) {
      throw new UnprocessableEntityException(
          String.format("Base change not found: %s", input.base), e);
    }

    PatchSet.Id baseId = base.patchSet().id();
    if (change.getId().equals(baseId.changeId())) {
      throw new ResourceConflictException("cannot rebase change onto itself");
    }

    permissionBackend.user(rsrc.getUser()).change(base.notes()).check(ChangePermission.READ);

    Change baseChange = base.notes().getChange();
    if (!baseChange.getProject().equals(change.getProject())) {
      throw new ResourceConflictException(
          "base change is in wrong project: " + baseChange.getProject());
    } else if (!baseChange.getDest().equals(change.getDest())) {
      throw new ResourceConflictException(
          "base change is targeting wrong branch: " + baseChange.getDest());
    } else if (baseChange.isAbandoned()) {
      throw new ResourceConflictException("base change is abandoned: " + baseChange.getKey());
    } else if (isMergedInto(rw, rsrc.getPatchSet(), base.patchSet())) {
      throw new ResourceConflictException(
          "base change "
              + baseChange.getKey()
              + " is a descendant of the current change - recursion not allowed");
    }
    return base.patchSet().commitId();
  }

  private static boolean isMergedInto(RevWalk rw, PatchSet base, PatchSet tip) throws IOException {
    ObjectId baseId = base.commitId();
    ObjectId tipId = tip.commitId();
    return rw.isMergedInto(rw.parseCommit(baseId), rw.parseCommit(tipId));
  }

  private static void verifyRebasePreconditions(
      ProjectCache projectCache, PatchSetUtil patchSetUtil, RevWalk rw, RevisionResource rsrc)
      throws ResourceConflictException, IOException, AuthException, PermissionBackendException {
    // Not allowed to rebase if the current patch set is locked.
    patchSetUtil.checkPatchSetNotLocked(rsrc.getNotes());

    rsrc.permissions().check(ChangePermission.REBASE);
    projectCache
        .get(rsrc.getProject())
        .orElseThrow(illegalState(rsrc.getProject()))
        .checkStatePermitsWrite();

    if (!rsrc.getChange().isNew()) {
      throw new ResourceConflictException("change is " + ChangeUtil.status(rsrc.getChange()));
    } else if (!hasOneParent(rw, rsrc.getPatchSet())) {
      throw new ResourceConflictException("cannot rebase merge commits or commit with no ancestor");
    }
  }

  private static boolean hasOneParent(RevWalk rw, PatchSet ps) throws IOException {
    // Prevent rebase of exotic changes (merge commit, no ancestor).
    RevCommit c = rw.parseCommit(ps.commitId());
    return c.getParentCount() == 1;
  }

  @Override
  public UiAction.Description getDescription(RevisionResource rsrc) throws IOException {
    UiAction.Description description =
        new UiAction.Description()
            .setLabel("Rebase")
            .setTitle(
                "Rebase onto tip of branch or parent change. Makes you the uploader of this "
                    + "change which can affect validity of approvals.")
            .setVisible(false);

    Change change = rsrc.getChange();
    if (!(change.isNew() && rsrc.isCurrent())) {
      return description;
    }
    if (!projectCache
        .get(rsrc.getProject())
        .orElseThrow(illegalState(rsrc.getProject()))
        .statePermitsWrite()) {
      return description;
    }
    if (patchSetUtil.isPatchSetLocked(rsrc.getNotes())) {
      return description;
    }

    boolean enabled = false;
    try (Repository repo = repoManager.openRepository(change.getDest().project());
        RevWalk rw = new RevWalk(repo)) {
      if (hasOneParent(rw, rsrc.getPatchSet())) {
        enabled = rebaseUtil.canRebase(rsrc.getPatchSet(), change.getDest(), repo, rw);
      }
    }

    if (rsrc.permissions().testOrFalse(ChangePermission.REBASE)) {
      return description.setVisible(true).setEnabled(enabled);
    }
    return description;
  }

  private static ImmutableListMultimap<String, String> getValidateOptionsAsMultimap(
      @Nullable Map<String, String> validationOptions) {
    if (validationOptions == null) {
      return ImmutableListMultimap.of();
    }

    ImmutableListMultimap.Builder<String, String> validationOptionsBuilder =
        ImmutableListMultimap.builder();
    validationOptions
        .entrySet()
        .forEach(e -> validationOptionsBuilder.put(e.getKey(), e.getValue()));
    return validationOptionsBuilder.build();
  }

  public static class CurrentRevision implements RestModifyView<ChangeResource, RebaseInput> {
    private final PatchSetUtil psUtil;
    private final Rebase rebase;

    @Inject
    CurrentRevision(PatchSetUtil psUtil, Rebase rebase) {
      this.psUtil = psUtil;
      this.rebase = rebase;
    }

    @Override
    public Response<ChangeInfo> apply(ChangeResource rsrc, RebaseInput input)
        throws RestApiException, UpdateException, IOException, PermissionBackendException {
      PatchSet ps = psUtil.current(rsrc.getNotes());
      if (ps == null) {
        throw new ResourceConflictException("current revision is missing");
      }
      return Response.ok(rebase.apply(new RevisionResource(rsrc, ps), input).value());
    }
  }

  public static class Chain implements RestModifyView<ChangeResource, RebaseInput> {
    private final GitRepositoryManager repoManager;
    private final PatchSetUtil psUtil;
    private final RebaseUtil rebaseUtil;
    private final Provider<MergeSuperSet> mergeSuperSet;
    private final RebaseChangeOp.Factory rebaseFactory;
    private final ChangeResource.Factory changeResourceFactory;
    private final Provider<InternalChangeQuery> queryProvider;
    private final PermissionBackend permissionBackend;
    private final BatchUpdate.Factory updateFactory;
    private final ProjectCache projectCache;
    private final PatchSetUtil patchSetUtil;
    private final ChangeJson.Factory json;

    @Inject
    Chain(
        GitRepositoryManager repoManager,
        PatchSetUtil psUtil,
        RebaseUtil rebaseUtil,
        Provider<MergeSuperSet> mergeSuperSet,
        RebaseChangeOp.Factory rebaseFactory,
        ChangeResource.Factory changeResourceFactory,
        Provider<InternalChangeQuery> queryProvider,
        PermissionBackend permissionBackend,
        BatchUpdate.Factory updateFactory,
        ProjectCache projectCache,
        PatchSetUtil patchSetUtil,
        ChangeJson.Factory json) {
      this.repoManager = repoManager;
      this.psUtil = psUtil;
      this.rebaseUtil = rebaseUtil;
      this.mergeSuperSet = mergeSuperSet;
      this.rebaseFactory = rebaseFactory;
      this.changeResourceFactory = changeResourceFactory;
      this.queryProvider = queryProvider;
      this.permissionBackend = permissionBackend;
      this.updateFactory = updateFactory;
      this.projectCache = projectCache;
      this.patchSetUtil = patchSetUtil;
      this.json = json;
    }

    @Override
    public Response<RebaseChainInfo> apply(ChangeResource rsrc, RebaseInput input)
        throws RestApiException, UpdateException, IOException, PermissionBackendException {
      ChangeSet cs =
          mergeSuperSet
              .get()
              .completeChangeSet(
                  rsrc.getChange(), rsrc.getUser(), /*includingTopicClosure= */ false);
      RebaseChainInfo res = new RebaseChainInfo();
      res.rebasedChanges = new ArrayList<>();
      try (Repository repo = repoManager.openRepository(rsrc.getProject());
          ObjectInserter oi = repo.newObjectInserter();
          ObjectReader reader = oi.newReader();
          RevWalk rw = CodeReviewCommit.newRevWalk(reader);
          BatchUpdate bu =
              updateFactory.create(rsrc.getProject(), rsrc.getUser(), TimeUtil.now())) {

        // DO NOT SUBMIT - iterate through reversions while excluding target branch.
        List<ChangeData> chain = cs.sortedChangeChainByAncestry(repo, queryProvider, rw);
        for (int i = 0; i < chain.size(); i++) {
          ChangeData change = chain.get(i);
          PatchSet ps = psUtil.current(change.notes());
          if (ps == null) {
            throw new ResourceConflictException(
                "current revision is missing for change " + change.getId());
          }
          RevisionResource revRsrc =
              new RevisionResource(changeResourceFactory.create(change, rsrc.getUser()), ps);
          verifyRebasePreconditions(projectCache, patchSetUtil, rw, revRsrc);

          RebaseChangeOp rebaseOp;
          if (i == 0) {
            rebaseOp =
                rebaseFactory.create(
                    revRsrc.getNotes(),
                    revRsrc.getPatchSet(),
                    findBaseRev(repo, rw, rebaseUtil, permissionBackend, revRsrc, input));
          } else {
            rebaseOp =
                rebaseFactory.create(
                    revRsrc.getNotes(), revRsrc.getPatchSet(), chain.get(i - 1).getId());
          }
          rebaseOp =
              rebaseOp
                  .setForceContentMerge(true)
                  .setAllowConflicts(input.allowConflicts)
                  .setValidationOptions(getValidateOptionsAsMultimap(input.validationOptions))
                  .setFireRevisionCreated(true);
          bu.addOp(revRsrc.getChange().getId(), rebaseOp);
          ChangeInfo changeInfo = json.create(OPTIONS).format(rsrc.getProject(), change.getId());
          res.rebasedChanges.add(changeInfo);
        }

        // TODO(dborowitz): Why no notification? This seems wrong; dig up blame.
        bu.setNotify(NotifyResolver.Result.none());
        bu.setRepository(repo, rw, oi);
        bu.execute();
      }
      res.containsGitConflicts =
          res.rebasedChanges.stream()
              .anyMatch(i -> i.containsGitConflicts != null && i.containsGitConflicts);
      return Response.ok(res);
    }
  }
}
