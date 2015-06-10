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

import com.google.common.primitives.Ints;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.extensions.api.changes.RebaseInput;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Singleton
public class Rebase implements RestModifyView<RevisionResource, RebaseInput>,
    UiAction<RevisionResource> {

  private static final Logger log = LoggerFactory.getLogger(Rebase.class);

  private final GitRepositoryManager repoManager;
  private final Provider<RebaseChange> rebaseChange;
  private final ChangeJson json;
  private final Provider<ReviewDb> dbProvider;

  @Inject
  public Rebase(GitRepositoryManager repoManager,
      Provider<RebaseChange> rebaseChange,
      ChangeJson json,
      Provider<ReviewDb> dbProvider) {
    this.repoManager = repoManager;
    this.rebaseChange = rebaseChange;
    this.json = json
        .addOption(ListChangesOption.CURRENT_REVISION)
        .addOption(ListChangesOption.CURRENT_COMMIT);
    this.dbProvider = dbProvider;
  }

  @Override
  public ChangeInfo apply(RevisionResource rsrc, RebaseInput input)
      throws AuthException, ResourceNotFoundException,
      ResourceConflictException, EmailException, OrmException, IOException {
    ChangeControl control = rsrc.getControl();
    Change change = rsrc.getChange();
    try (Repository repo = repoManager.openRepository(change.getProject());
        RevWalk rw = new RevWalk(repo)) {
      if (!control.canRebase()) {
        throw new AuthException("rebase not permitted");
      } else if (!change.getStatus().isOpen()) {
        throw new ResourceConflictException("change is "
            + change.getStatus().name().toLowerCase());
      } else if (!hasOneParent(rw, rsrc.getPatchSet())) {
        throw new ResourceConflictException(
            "cannot rebase merge commits or commit with no ancestor");
      }
      rebaseChange.get().rebase(repo, rw, control, rsrc.getPatchSet().getId(),
          rsrc.getUser(), findBaseRev(rw, rsrc, input));
    } catch (InvalidChangeOperationException e) {
      throw new ResourceConflictException(e.getMessage());
    } catch (NoSuchChangeException e) {
      throw new ResourceNotFoundException(change.getId().toString());
    }

    return json.format(change.getId());
  }

  private String findBaseRev(RevWalk rw, RevisionResource rsrc,
      RebaseInput input) throws AuthException, ResourceConflictException,
      OrmException, IOException {
    if (input == null || input.base == null) {
      return null;
    }

    Change change = rsrc.getChange();
    String base = input.base.trim();
    if (base.equals("")) {
      // remove existing dependency to other patch set
      return change.getDest().get();
    }

    ReviewDb db = dbProvider.get();
    PatchSet basePatchSet = parseBase(base);
    if (basePatchSet == null) {
      throw new ResourceConflictException("base revision is missing: " + base);
    } else if (!rsrc.getControl().isPatchVisible(basePatchSet, db)) {
      throw new AuthException("base revision not accessible: " + base);
    } else if (change.getId().equals(basePatchSet.getId().getParentKey())) {
      throw new ResourceConflictException("cannot depend on self");
    }

    Change baseChange = db.changes().get(basePatchSet.getId().getParentKey());
    if (baseChange == null) {
      return null;
    }
    if (!baseChange.getProject().equals(change.getProject())) {
      throw new ResourceConflictException(
          "base change is in wrong project: " + baseChange.getProject());
    } else if (!baseChange.getDest().equals(change.getDest())) {
      throw new ResourceConflictException(
          "base change is targeting wrong branch: " + baseChange.getDest());
    } else if (baseChange.getStatus() == Status.ABANDONED) {
      throw new ResourceConflictException(
          "base change is abandoned: " + baseChange.getKey());
    } else if (isMergedInto(rw, rsrc.getPatchSet(), basePatchSet)) {
      throw new ResourceConflictException(
          "base change " + baseChange.getKey()
          + " is a descendant of the current  change - recursion not allowed");
    }
    return basePatchSet.getRevision().get();
  }

  private boolean isMergedInto(RevWalk rw, PatchSet base, PatchSet tip)
      throws IOException {
    ObjectId baseId = ObjectId.fromString(base.getRevision().get());
    ObjectId tipId = ObjectId.fromString(tip.getRevision().get());
    return rw.isMergedInto(rw.parseCommit(baseId), rw.parseCommit(tipId));
  }

  private PatchSet parseBase(String base) throws OrmException {
    ReviewDb db = dbProvider.get();

    PatchSet.Id basePatchSetId = PatchSet.Id.fromRef(base);
    if (basePatchSetId != null) {
      // Try parsing the base as a ref string.
      return db.patchSets().get(basePatchSetId);
    }

    // Try parsing base as a change number (assume current patch set).
    PatchSet basePatchSet = null;
    Integer baseChangeId = Ints.tryParse(base);
    if (baseChangeId != null) {
      for (PatchSet ps : db.patchSets().byChange(new Change.Id(baseChangeId))) {
        if (basePatchSet == null
            || basePatchSet.getId().get() < ps.getId().get()) {
          basePatchSet = ps;
        }
      }
      if (basePatchSet != null) {
        return basePatchSet;
      }
    }

    // Try parsing as SHA-1.
    for (PatchSet ps : db.patchSets().byRevision(new RevId(base))) {
      if (basePatchSet == null
          || basePatchSet.getId().get() < ps.getId().get()) {
        basePatchSet = ps;
      }
    }
    return basePatchSet;
  }

  private boolean hasOneParent(RevWalk rw, PatchSet ps) throws IOException {
    // Prevent rebase of exotic changes (merge commit, no ancestor).
    RevCommit c = rw.parseCommit(ObjectId.fromString(ps.getRevision().get()));
    return c.getParentCount() == 1;
  }

  @Override
  public UiAction.Description getDescription(RevisionResource resource) {
    Project.NameKey project = resource.getChange().getProject();
    boolean visible = resource.getChange().getStatus().isOpen()
          && resource.isCurrent()
          && resource.getControl().canRebase();
    if (visible) {
      try (Repository repo = repoManager.openRepository(project);
          RevWalk rw = new RevWalk(repo)) {
        visible = hasOneParent(rw, resource.getPatchSet());
      } catch (IOException e) {
        log.error("Failed to get ancestors of patch set "
            + resource.getPatchSet().getId(), e);
        visible = false;
      }
    }
    UiAction.Description descr = new UiAction.Description()
      .setLabel("Rebase")
      .setTitle("Rebase onto tip of branch or parent change")
      .setVisible(visible);
    if (descr.isVisible()) {
      // Disable the rebase button in the RebaseDialog if
      // the change cannot be rebased.
      descr.setEnabled(rebaseChange.get().canRebase(resource));
    }
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
        throws AuthException, ResourceNotFoundException,
        ResourceConflictException, EmailException, OrmException, IOException {
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
