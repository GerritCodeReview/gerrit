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

package com.google.gerrit.server.change;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.extensions.api.changes.RebaseInput;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.RebaseUtil.Base;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Rebase extends RetryingRestModifyView<RevisionResource, RebaseInput, ChangeInfo>
    implements RestModifyView<RevisionResource, RebaseInput>, UiAction<RevisionResource> {
  private static final Logger log = LoggerFactory.getLogger(Rebase.class);
  private static final ImmutableSet<ListChangesOption> OPTIONS =
      Sets.immutableEnumSet(ListChangesOption.CURRENT_REVISION, ListChangesOption.CURRENT_COMMIT);

  private final GitRepositoryManager repoManager;
  private final RebaseChangeOp.Factory rebaseFactory;
  private final RebaseUtil rebaseUtil;
  private final ChangeJson.Factory json;
  private final Provider<ReviewDb> dbProvider;

  @Inject
  public Rebase(
      RetryHelper retryHelper,
      GitRepositoryManager repoManager,
      RebaseChangeOp.Factory rebaseFactory,
      RebaseUtil rebaseUtil,
      ChangeJson.Factory json,
      Provider<ReviewDb> dbProvider) {
    super(retryHelper);
    this.repoManager = repoManager;
    this.rebaseFactory = rebaseFactory;
    this.rebaseUtil = rebaseUtil;
    this.json = json;
    this.dbProvider = dbProvider;
  }

  @Override
  protected ChangeInfo applyImpl(
      BatchUpdate.Factory updateFactory, RevisionResource rsrc, RebaseInput input)
      throws EmailException, OrmException, UpdateException, RestApiException, IOException,
          NoSuchChangeException, PermissionBackendException {
    rsrc.permissions().database(dbProvider).check(ChangePermission.REBASE);

    ChangeControl control = rsrc.getControl();
    Change change = rsrc.getChange();
    try (Repository repo = repoManager.openRepository(change.getProject());
        ObjectInserter oi = repo.newObjectInserter();
        ObjectReader reader = oi.newReader();
        RevWalk rw = new RevWalk(reader);
        BatchUpdate bu =
            updateFactory.create(
                dbProvider.get(), change.getProject(), rsrc.getUser(), TimeUtil.nowTs())) {
      if (!change.getStatus().isOpen()) {
        throw new ResourceConflictException("change is " + ChangeUtil.status(change));
      } else if (!hasOneParent(rw, rsrc.getPatchSet())) {
        throw new ResourceConflictException(
            "cannot rebase merge commits or commit with no ancestor");
      }
      bu.setRepository(repo, rw, oi);
      bu.addOp(
          change.getId(),
          rebaseFactory
              .create(control, rsrc.getPatchSet(), findBaseRev(repo, rw, rsrc, input))
              .setForceContentMerge(true)
              .setFireRevisionCreated(true));
      bu.execute();
    }
    return json.create(OPTIONS).format(change.getProject(), change.getId());
  }

  private ObjectId findBaseRev(
      Repository repo, RevWalk rw, RevisionResource rsrc, RebaseInput input)
      throws RestApiException, OrmException, IOException, NoSuchChangeException {
    Branch.NameKey destRefKey = rsrc.getChange().getDest();
    if (input == null || input.base == null) {
      return rebaseUtil.findBaseRevision(rsrc.getPatchSet(), destRefKey, repo, rw);
    }

    Change change = rsrc.getChange();
    String str = input.base.trim();
    if (str.equals("")) {
      // Remove existing dependency to other patch set.
      Ref destRef = repo.exactRef(destRefKey.get());
      if (destRef == null) {
        throw new ResourceConflictException(
            "can't rebase onto tip of branch " + destRefKey.get() + "; branch doesn't exist");
      }
      return destRef.getObjectId();
    }

    @SuppressWarnings("resource")
    ReviewDb db = dbProvider.get();
    Base base = rebaseUtil.parseBase(rsrc, str);
    if (base == null) {
      throw new ResourceConflictException("base revision is missing: " + str);
    }
    PatchSet.Id baseId = base.patchSet().getId();
    if (!base.control().isVisible(db)) {
      throw new AuthException("base revision not accessible: " + str);
    } else if (change.getId().equals(baseId.getParentKey())) {
      throw new ResourceConflictException("cannot rebase change onto itself");
    }

    Change baseChange = base.control().getChange();
    if (!baseChange.getProject().equals(change.getProject())) {
      throw new ResourceConflictException(
          "base change is in wrong project: " + baseChange.getProject());
    } else if (!baseChange.getDest().equals(change.getDest())) {
      throw new ResourceConflictException(
          "base change is targeting wrong branch: " + baseChange.getDest());
    } else if (baseChange.getStatus() == Status.ABANDONED) {
      throw new ResourceConflictException("base change is abandoned: " + baseChange.getKey());
    } else if (isMergedInto(rw, rsrc.getPatchSet(), base.patchSet())) {
      throw new ResourceConflictException(
          "base change "
              + baseChange.getKey()
              + " is a descendant of the current change - recursion not allowed");
    }
    return ObjectId.fromString(base.patchSet().getRevision().get());
  }

  private boolean isMergedInto(RevWalk rw, PatchSet base, PatchSet tip) throws IOException {
    ObjectId baseId = ObjectId.fromString(base.getRevision().get());
    ObjectId tipId = ObjectId.fromString(tip.getRevision().get());
    return rw.isMergedInto(rw.parseCommit(baseId), rw.parseCommit(tipId));
  }

  private boolean hasOneParent(RevWalk rw, PatchSet ps) throws IOException {
    // Prevent rebase of exotic changes (merge commit, no ancestor).
    RevCommit c = rw.parseCommit(ObjectId.fromString(ps.getRevision().get()));
    return c.getParentCount() == 1;
  }

  @Override
  public UiAction.Description getDescription(RevisionResource resource) {
    PatchSet patchSet = resource.getPatchSet();
    Change change = resource.getChange();
    Branch.NameKey dest = change.getDest();
    boolean visible =
        change.getStatus().isOpen()
            && resource.isCurrent()
            && resource.permissions().database(dbProvider).testOrFalse(ChangePermission.REBASE);
    boolean enabled = true;

    if (visible) {
      try (Repository repo = repoManager.openRepository(dest.getParentKey());
          RevWalk rw = new RevWalk(repo)) {
        visible = hasOneParent(rw, resource.getPatchSet());
        enabled = rebaseUtil.canRebase(patchSet, dest, repo, rw);
      } catch (IOException e) {
        log.error("Failed to check if patch set can be rebased: " + resource.getPatchSet(), e);
        visible = false;
      }
    }
    return new UiAction.Description()
        .setLabel("Rebase")
        .setTitle("Rebase onto tip of branch or parent change")
        .setVisible(visible)
        .setEnabled(enabled);
  }

  public static class CurrentRevision
      extends RetryingRestModifyView<ChangeResource, RebaseInput, ChangeInfo> {
    private final PatchSetUtil psUtil;
    private final Rebase rebase;

    @Inject
    CurrentRevision(RetryHelper retryHelper, PatchSetUtil psUtil, Rebase rebase) {
      super(retryHelper);
      this.psUtil = psUtil;
      this.rebase = rebase;
    }

    @Override
    protected ChangeInfo applyImpl(
        BatchUpdate.Factory updateFactory, ChangeResource rsrc, RebaseInput input)
        throws EmailException, OrmException, UpdateException, RestApiException, IOException,
            PermissionBackendException {
      PatchSet ps = psUtil.current(rebase.dbProvider.get(), rsrc.getNotes());
      if (ps == null) {
        throw new ResourceConflictException("current revision is missing");
      } else if (!rsrc.getControl().isVisible(rebase.dbProvider.get())) {
        throw new AuthException("current revision not accessible");
      }
      return rebase.applyImpl(updateFactory, new RevisionResource(rsrc, ps), input);
    }
  }
}
