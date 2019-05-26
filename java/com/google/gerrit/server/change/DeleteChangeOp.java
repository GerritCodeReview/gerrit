// Copyright (C) 2015 The Android Open Source Project
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

import static com.google.common.flogger.LazyArgs.lazy;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.extensions.events.ChangeDeleted;
import com.google.gerrit.server.plugincontext.PluginItemContext;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.RepoContext;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevWalk;

public class DeleteChangeOp implements BatchUpdateOp {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    DeleteChangeOp create(Change.Id id);
  }

  private final PatchSetUtil psUtil;
  private final StarredChangesUtil starredChangesUtil;
  private final PluginItemContext<AccountPatchReviewStore> accountPatchReviewStore;
  private final ChangeDeleted changeDeleted;
  private final Change.Id id;

  @Inject
  DeleteChangeOp(
      PatchSetUtil psUtil,
      StarredChangesUtil starredChangesUtil,
      PluginItemContext<AccountPatchReviewStore> accountPatchReviewStore,
      ChangeDeleted changeDeleted,
      @Assisted Change.Id id) {
    this.psUtil = psUtil;
    this.starredChangesUtil = starredChangesUtil;
    this.accountPatchReviewStore = accountPatchReviewStore;
    this.changeDeleted = changeDeleted;
    this.id = id;
  }

  // The relative order of updateChange and updateRepo doesn't matter as long as all operations are
  // executed in a single atomic BatchRefUpdate. Actually deleting the change refs first would not
  // fail gracefully if the second delete fails, but fortunately that's not what happens.
  @Override
  public boolean updateChange(ChangeContext ctx) throws RestApiException, IOException {
    Collection<PatchSet> patchSets = psUtil.byChange(ctx.getNotes());

    ensureDeletable(ctx, id, patchSets);
    // Cleaning up is only possible as long as the change and its elements are
    // still part of the database.
    cleanUpReferences(id);

    logger.atFine().log(
        "Deleting change %s, current patch set %d is commit %s",
        id,
        ctx.getChange().currentPatchSetId().get(),
        lazy(
            () ->
                patchSets.stream()
                    .filter(p -> p.number() == ctx.getChange().currentPatchSetId().get())
                    .findAny()
                    .map(p -> p.commitId().name())
                    .orElse("n/a")));
    ctx.deleteChange();
    changeDeleted.fire(ctx.getChange(), ctx.getAccount(), ctx.getWhen());
    return true;
  }

  private void ensureDeletable(ChangeContext ctx, Change.Id id, Collection<PatchSet> patchSets)
      throws ResourceConflictException, MethodNotAllowedException, IOException {
    if (ctx.getChange().isMerged()) {
      throw new MethodNotAllowedException("Deleting merged change " + id + " is not allowed");
    }
    for (PatchSet patchSet : patchSets) {
      if (isPatchSetMerged(ctx, patchSet)) {
        throw new ResourceConflictException(
            String.format(
                "Cannot delete change %s: patch set %s is already merged", id, patchSet.number()));
      }
    }
  }

  private boolean isPatchSetMerged(ChangeContext ctx, PatchSet patchSet) throws IOException {
    Optional<ObjectId> destId = ctx.getRepoView().getRef(ctx.getChange().getDest().branch());
    if (!destId.isPresent()) {
      return false;
    }

    RevWalk revWalk = ctx.getRevWalk();
    return revWalk.isMergedInto(
        revWalk.parseCommit(patchSet.commitId()), revWalk.parseCommit(destId.get()));
  }

  private void cleanUpReferences(Change.Id id) throws IOException {
    accountPatchReviewStore.run(s -> s.clearReviewed(id));

    // Non-atomic operation on All-Users refs; not much we can do to make it atomic.
    starredChangesUtil.unstarAllForChangeDeletion(id);
  }

  @Override
  public void updateRepo(RepoContext ctx) throws IOException {
    String changeRefPrefix = RefNames.changeRefPrefix(id);
    for (Map.Entry<String, ObjectId> e : ctx.getRepoView().getRefs(changeRefPrefix).entrySet()) {
      removeRef(ctx, e, changeRefPrefix);
    }
    removeUserEdits(ctx);
  }

  private void removeUserEdits(RepoContext ctx) throws IOException {
    String prefix = RefNames.REFS_USERS;
    String editRef = String.format("/edit-%s/", id);
    for (Map.Entry<String, ObjectId> e : ctx.getRepoView().getRefs(prefix).entrySet()) {
      if (e.getKey().contains(editRef)) {
        removeRef(ctx, e, prefix);
      }
    }
  }

  private void removeRef(RepoContext ctx, Map.Entry<String, ObjectId> entry, String prefix)
      throws IOException {
    ctx.addRefUpdate(entry.getValue(), ObjectId.zeroId(), prefix + entry.getKey());
  }
}
