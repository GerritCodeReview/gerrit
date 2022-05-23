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

package com.google.gerrit.server.approval;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.InternalUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

public class RecursiveApprovalCopier {

  private final BatchUpdate.Factory batchUpdateFactory;
  private final GitRepositoryManager repositoryManager;
  private final InternalUser.Factory internalUserFactory;
  private final ApprovalsUtil approvalsUtil;

  @Inject
  public RecursiveApprovalCopier(
      BatchUpdate.Factory batchUpdateFactory,
      GitRepositoryManager repositoryManager,
      InternalUser.Factory internalUserFactory,
      ApprovalsUtil approvalsUtil) {
    this.batchUpdateFactory = batchUpdateFactory;
    this.repositoryManager = repositoryManager;
    this.internalUserFactory = internalUserFactory;
    this.approvalsUtil = approvalsUtil;
  }

  public void persist() throws Exception {
    persist(repositoryManager.list(), null);
  }

  public void persist(
      Set<Project.NameKey> projects, @Nullable Consumer<Change> labelsCopiedListener)
      throws Exception {
    for (Project.NameKey project : projects) {
      try (BatchUpdate bu =
              batchUpdateFactory.create(project, internalUserFactory.create(), TimeUtil.nowTs());
          Repository repository = repositoryManager.openRepository(project)) {
        for (Ref changeMetaRef :
            repository.getRefDatabase().getRefsByPrefix(RefNames.REFS_CHANGES).stream()
                .filter(r -> r.getName().endsWith(RefNames.META_SUFFIX))
                .collect(toImmutableList())) {
          Change.Id changeId = Change.Id.fromRef(changeMetaRef.getName());
          bu.addOp(changeId, new PersistCopiedVotesOp(approvalsUtil, labelsCopiedListener));
        }

        bu.execute();
      }
    }
  }

  private static class PersistCopiedVotesOp implements BatchUpdateOp {
    private final ApprovalsUtil approvalsUtil;
    private final Consumer<Change> listener;

    PersistCopiedVotesOp(
        ApprovalsUtil approvalsUtil, @Nullable Consumer<Change> labelsCopiedListener) {
      this.approvalsUtil = approvalsUtil;
      this.listener = labelsCopiedListener;
    }

    @Override
    public boolean updateChange(ChangeContext ctx) throws IOException {
      Change change = ctx.getChange();
      ChangeUpdate update = ctx.getUpdate(change.currentPatchSetId());
      approvalsUtil.persistCopiedApprovals(
          ctx.getNotes(),
          ctx.getNotes().getCurrentPatchSet(),
          ctx.getRevWalk(),
          ctx.getRepoView().getConfig(),
          update);

      boolean labelsCopied = update.hasCopiedApprovals();

      if (labelsCopied && listener != null) {
        listener.accept(change);
      }

      return labelsCopied;
    }
  }
}
