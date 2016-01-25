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

import com.google.auto.value.AutoValue;
import com.google.common.primitives.Ints;
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
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.EnumSet;

@Singleton
public class Rebase implements RestModifyView<RevisionResource, RebaseInput>,
    UiAction<RevisionResource> {
  private static final Logger log = LoggerFactory.getLogger(Rebase.class);
  private static final EnumSet<ListChangesOption> OPTIONS = EnumSet.of(
      ListChangesOption.CURRENT_REVISION,
      ListChangesOption.CURRENT_COMMIT);

  private final BatchUpdate.Factory updateFactory;
  private final GitRepositoryManager repoManager;
  private final RebaseChangeOp.Factory rebaseFactory;
  private final RebaseUtil rebaseUtil;
  private final ChangeJson.Factory json;
  private final Provider<ReviewDb> dbProvider;
  private final Provider<InternalChangeQuery> queryProvider;
  private final PatchSetUtil psUtil;

  @Inject
  public Rebase(BatchUpdate.Factory updateFactory,
      GitRepositoryManager repoManager,
      RebaseChangeOp.Factory rebaseFactory,
      RebaseUtil rebaseUtil,
      ChangeJson.Factory json,
      Provider<ReviewDb> dbProvider,
      Provider<InternalChangeQuery> queryProvider,
      PatchSetUtil psUtil) {
    this.updateFactory = updateFactory;
    this.repoManager = repoManager;
    this.rebaseFactory = rebaseFactory;
    this.rebaseUtil = rebaseUtil;
    this.json = json;
    this.dbProvider = dbProvider;
    this.queryProvider = queryProvider;
    this.psUtil = psUtil;
  }

  @Override
  public ChangeInfo apply(RevisionResource rsrc, RebaseInput input)
      throws EmailException, OrmException, UpdateException, RestApiException,
      IOException {
    ChangeControl control = rsrc.getControl();
    Change change = rsrc.getChange();
    try (Repository repo = repoManager.openRepository(change.getProject());
        RevWalk rw = new RevWalk(repo);
        ObjectInserter oi = repo.newObjectInserter();
        BatchUpdate bu = updateFactory.create(dbProvider.get(),
          change.getProject(), rsrc.getUser(), TimeUtil.nowTs())) {
      if (!control.canRebase(dbProvider.get())) {
        throw new AuthException("rebase not permitted");
      } else if (!change.getStatus().isOpen()) {
        throw new ResourceConflictException("change is "
            + change.getStatus().name().toLowerCase());
      } else if (!hasOneParent(rw, rsrc.getPatchSet())) {
        throw new ResourceConflictException(
            "cannot rebase merge commits or commit with no ancestor");
      }
      bu.setRepository(repo, rw, oi);
      bu.addOp(change.getId(), rebaseFactory.create(
            control, rsrc.getPatchSet(),
            findBaseRev(rw, rsrc, input))
          .setForceContentMerge(true)
          .setRunHooks(true)
          .setValidatePolicy(CommitValidators.Policy.GERRIT));
      bu.execute();
    }
    return json.create(OPTIONS).format(change.getId());
  }

  private String findBaseRev(RevWalk rw, RevisionResource rsrc,
      RebaseInput input) throws AuthException, ResourceConflictException,
      OrmException, IOException {
    if (input == null || input.base == null) {
      return null;
    }

    Change change = rsrc.getChange();
    String str = input.base.trim();
    if (str.equals("")) {
      // remove existing dependency to other patch set
      return change.getDest().get();
    }

    @SuppressWarnings("resource")
    ReviewDb db = dbProvider.get();
    Base base = parseBase(rsrc, str);
    if (base == null) {
      throw new ResourceConflictException("base revision is missing: " + str);
    }
    PatchSet.Id baseId = base.patchSet().getId();
    if (!base.control().isPatchVisible(base.patchSet(), db)) {
      throw new AuthException("base revision not accessible: " + str);
    } else if (change.getId().equals(baseId.getParentKey())) {
      throw new ResourceConflictException("cannot depend on self");
    }

    Change baseChange = base.control().getChange();
    if (!baseChange.getProject().equals(change.getProject())) {
      throw new ResourceConflictException(
          "base change is in wrong project: " + baseChange.getProject());
    } else if (!baseChange.getDest().equals(change.getDest())) {
      throw new ResourceConflictException(
          "base change is targeting wrong branch: " + baseChange.getDest());
    } else if (baseChange.getStatus() == Status.ABANDONED) {
      throw new ResourceConflictException(
          "base change is abandoned: " + baseChange.getKey());
    } else if (isMergedInto(rw, rsrc.getPatchSet(), base.patchSet())) {
      throw new ResourceConflictException(
          "base change " + baseChange.getKey()
          + " is a descendant of the current  change - recursion not allowed");
    }
    return base.patchSet().getRevision().get();
  }

  private boolean isMergedInto(RevWalk rw, PatchSet base, PatchSet tip)
      throws IOException {
    ObjectId baseId = ObjectId.fromString(base.getRevision().get());
    ObjectId tipId = ObjectId.fromString(tip.getRevision().get());
    return rw.isMergedInto(rw.parseCommit(baseId), rw.parseCommit(tipId));
  }

  @AutoValue
  static abstract class Base {
    private static Base create(ChangeControl ctl, PatchSet ps) {
      if (ctl == null) {
        return null;
      }
      return new AutoValue_Rebase_Base(ctl, ps);
    }

    abstract ChangeControl control();
    abstract PatchSet patchSet();
  }

  private Base parseBase(RevisionResource rsrc, String base)
      throws OrmException {
    ReviewDb db = dbProvider.get();

    // Try parsing the base as a ref string.
    PatchSet.Id basePatchSetId = PatchSet.Id.fromRef(base);
    if (basePatchSetId != null) {
      Change.Id baseChangeId = basePatchSetId.getParentKey();
      ChangeControl baseCtl = controlFor(rsrc, baseChangeId);
      if (baseCtl != null) {
        return Base.create(
            controlFor(rsrc, basePatchSetId.getParentKey()),
            psUtil.get(db, baseCtl.getNotes(), basePatchSetId));
      }
    }

    // Try parsing base as a change number (assume current patch set).
    Integer baseChangeId = Ints.tryParse(base);
    if (baseChangeId != null) {
      ChangeControl baseCtl = controlFor(rsrc, new Change.Id(baseChangeId));
      if (baseCtl != null) {
        return Base.create(baseCtl, psUtil.current(db, baseCtl.getNotes()));
      }
    }

    // Try parsing as SHA-1.
    Base ret = null;
    for (ChangeData cd : queryProvider.get()
        .byProjectCommit(rsrc.getProject(), base)) {
      for (PatchSet ps : cd.patchSets()) {
        if (!ps.getRevision().matches(base)) {
          continue;
        }
        if (ret == null || ret.patchSet().getId().get() < ps.getId().get()) {
          ret = Base.create(
              rsrc.getControl().getProjectControl().controlFor(cd.change()),
              ps);
        }
      }
    }
    return ret;
  }

  private ChangeControl controlFor(RevisionResource rsrc, Change.Id id)
      throws OrmException {
    if (rsrc.getChange().getId().equals(id)) {
      return rsrc.getControl();
    }
    Change c = dbProvider.get().changes().get(id);
    if (c == null) {
      return null;
    }
    return rsrc.getControl().getProjectControl().controlFor(c);
  }

  private boolean hasOneParent(RevWalk rw, PatchSet ps) throws IOException {
    // Prevent rebase of exotic changes (merge commit, no ancestor).
    RevCommit c = rw.parseCommit(ObjectId.fromString(ps.getRevision().get()));
    return c.getParentCount() == 1;
  }

  @Override
  public UiAction.Description getDescription(RevisionResource resource) {
    PatchSet patchSet = resource.getPatchSet();
    Branch.NameKey dest = resource.getChange().getDest();
    boolean canRebase = false;
    try {
      canRebase = resource.getControl().canRebase(dbProvider.get());
    } catch (OrmException e) {
      log.error("Cannot check canRebase status. Assuming false.", e);
    }
    boolean visible = resource.getChange().getStatus().isOpen()
          && resource.isCurrent()
          && canRebase;
    boolean enabled = true;

    if (visible) {
      try (Repository repo = repoManager.openRepository(dest.getParentKey());
          RevWalk rw = new RevWalk(repo)) {
        visible = hasOneParent(rw, resource.getPatchSet());
        enabled = rebaseUtil.canRebase(patchSet, dest, repo, rw);
      } catch (IOException e) {
        log.error("Failed to check if patch set can be rebased: "
            + resource.getPatchSet(), e);
        visible = false;
      }
    }
    UiAction.Description descr = new UiAction.Description()
      .setLabel("Rebase")
      .setTitle("Rebase onto tip of branch or parent change")
      .setVisible(visible)
      .setEnabled(enabled);
    return descr;
  }

  public static class CurrentRevision implements
      RestModifyView<ChangeResource, RebaseInput> {
    private final Rebase rebase;

    @Inject
    CurrentRevision(Rebase rebase) {
      this.rebase = rebase;
    }

    @Override
    public ChangeInfo apply(ChangeResource rsrc, RebaseInput input)
        throws EmailException, OrmException, UpdateException, RestApiException,
        IOException {
      PatchSet ps =
          rebase.dbProvider.get().patchSets()
              .get(rsrc.getChange().currentPatchSetId());
      if (ps == null) {
        throw new ResourceConflictException("current revision is missing");
      } else if (!rsrc.getControl().isPatchVisible(ps, rebase.dbProvider.get())) {
        throw new AuthException("current revision not accessible");
      }
      return rebase.apply(new RevisionResource(rsrc, ps), input);
    }
  }
}
