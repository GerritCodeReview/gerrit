// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Table;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.ChangeKindCache;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LabelNormalizer;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeMap;

/**
 * Copies approvals between patch sets.
 * <p>
 * The result of a copy may either be stored, as when stamping approvals in the
 * database at submit time, or refreshed on demand, as when reading approvals
 * from the NoteDb.
 */
@Singleton
public class ApprovalCopier {
  private final GitRepositoryManager repoManager;
  private final ProjectCache projectCache;
  private final ChangeKindCache changeKindCache;
  private final LabelNormalizer labelNormalizer;
  private final ChangeData.Factory changeDataFactory;
  private final PatchSetUtil psUtil;

  @Inject
  ApprovalCopier(GitRepositoryManager repoManager,
      ProjectCache projectCache,
      ChangeKindCache changeKindCache,
      LabelNormalizer labelNormalizer,
      ChangeData.Factory changeDataFactory,
      PatchSetUtil psUtil) {
    this.repoManager = repoManager;
    this.projectCache = projectCache;
    this.changeKindCache = changeKindCache;
    this.labelNormalizer = labelNormalizer;
    this.changeDataFactory = changeDataFactory;
    this.psUtil = psUtil;
  }

  public void copy(ReviewDb db, ChangeControl ctl, PatchSet ps)
      throws OrmException {
    db.patchSetApprovals().insert(getForPatchSet(db, ctl, ps));
  }

  Iterable<PatchSetApproval> getForPatchSet(ReviewDb db,
      ChangeControl ctl, PatchSet.Id psId) throws OrmException {
    PatchSet ps = psUtil.get(db, ctl.getNotes(), psId);
    if (ps == null) {
      return Collections.emptyList();
    }
    return getForPatchSet(db, ctl, ps);
  }

  private Iterable<PatchSetApproval> getForPatchSet(ReviewDb db,
      ChangeControl ctl, PatchSet ps) throws OrmException {
    checkNotNull(ps, "ps should not be null");
    ChangeData cd = changeDataFactory.create(db, ctl);
    try {
      ProjectState project =
          projectCache.checkedGet(cd.change().getDest().getParentKey());
      ListMultimap<PatchSet.Id, PatchSetApproval> all = cd.approvals();
      checkNotNull(all, "all should not be null");

      Table<String, Account.Id, PatchSetApproval> byUser =
          HashBasedTable.create();
      for (PatchSetApproval psa : all.get(ps.getId())) {
        byUser.put(psa.getLabel(), psa.getAccountId(), psa);
      }

      TreeMap<Integer, PatchSet> patchSets = getPatchSets(cd);
      NavigableSet<Integer> allPsIds = patchSets.navigableKeySet();

      try (Repository repo =
          repoManager.openRepository(project.getProject().getNameKey())) {
        // Walk patch sets strictly less than current in descending order.
        Collection<PatchSet> allPrior = patchSets.descendingMap()
            .tailMap(ps.getId().get(), false)
            .values();
        for (PatchSet priorPs : allPrior) {
          List<PatchSetApproval> priorApprovals = all.get(priorPs.getId());
          if (priorApprovals.isEmpty()) {
            continue;
          }

          ChangeKind kind = changeKindCache.getChangeKind(project, repo,
              ObjectId.fromString(priorPs.getRevision().get()),
              ObjectId.fromString(ps.getRevision().get()));

          for (PatchSetApproval psa : priorApprovals) {
            if (!byUser.contains(psa.getLabel(), psa.getAccountId())
                && canCopy(project, psa, ps.getId(), allPsIds, kind)) {
              byUser.put(psa.getLabel(), psa.getAccountId(),
                  copy(psa, ps.getId()));
            }
          }
        }
        return labelNormalizer.normalize(ctl, byUser.values()).getNormalized();
      }
    } catch (IOException e) {
      throw new OrmException(e);
    }
  }

  private static TreeMap<Integer, PatchSet> getPatchSets(ChangeData cd)
      throws OrmException {
    Collection<PatchSet> patchSets = cd.patchSets();
    TreeMap<Integer, PatchSet> result = new TreeMap<>();
    for (PatchSet ps : patchSets) {
      result.put(ps.getId().get(), ps);
    }
    return result;
  }

  private static boolean canCopy(ProjectState project, PatchSetApproval psa,
      PatchSet.Id psId, NavigableSet<Integer> allPsIds, ChangeKind kind) {
    int n = psa.getKey().getParentKey().get();
    checkArgument(n != psId.get());
    LabelType type = project.getLabelTypes().byLabel(psa.getLabelId());
    if (type == null) {
      return false;
    } else if (Objects.equals(n, previous(allPsIds, psId.get())) && (
        type.isCopyMinScore() && type.isMaxNegative(psa)
        || type.isCopyMaxScore() && type.isMaxPositive(psa))) {
      // Copy min/max score only from the immediately preceding patch set (which
      // may not be psId.get() - 1).
      return true;
    }
    switch (kind) {
      case MERGE_FIRST_PARENT_UPDATE:
        return type.isCopyAllScoresOnMergeFirstParentUpdate();
      case NO_CODE_CHANGE:
        return type.isCopyAllScoresIfNoCodeChange();
      case TRIVIAL_REBASE:
        return type.isCopyAllScoresOnTrivialRebase();
      case NO_CHANGE:
        return type.isCopyAllScoresIfNoChange()
            || type.isCopyAllScoresOnTrivialRebase()
            || type.isCopyAllScoresOnMergeFirstParentUpdate()
            || type.isCopyAllScoresIfNoCodeChange();
      case REWORK:
      default:
        return false;
    }
  }

  private static PatchSetApproval copy(PatchSetApproval src, PatchSet.Id psId) {
    if (src.getKey().getParentKey().equals(psId)) {
      return src;
    }
    return new PatchSetApproval(psId, src);
  }

  private static <T> T previous(NavigableSet<T> s, T v) {
    SortedSet<T> head = s.headSet(v);
    return !head.isEmpty() ? head.last() : null;
  }
}
