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
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.InternalUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import java.io.IOException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
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

  public void persist()
      throws UpdateException, RestApiException, RepositoryNotFoundException, IOException {
    for (Project.NameKey project : repositoryManager.list()) {
      persist(project);
    }
  }

  public void persist(Project.NameKey project)
      throws IOException, UpdateException, RestApiException, RepositoryNotFoundException {
    try (BatchUpdate bu =
            batchUpdateFactory.create(project, internalUserFactory.create(), TimeUtil.nowTs());
        Repository repository = repositoryManager.openRepository(project)) {
      for (Ref changeMetaRef :
          repository.getRefDatabase().getRefsByPrefix(RefNames.REFS_CHANGES).stream()
              .filter(r -> r.getName().endsWith(RefNames.META_SUFFIX))
              .collect(toImmutableList())) {
        Change.Id changeId = Change.Id.fromRef(changeMetaRef.getName());
        bu.addOp(changeId, new PersistCopiedVotesOp(approvalsUtil));
      }
      bu.execute();
    }
  }

  public void persist(Change change) throws UpdateException, RestApiException {
    Project.NameKey project = change.getProject();
    try (BatchUpdate bu =
        batchUpdateFactory.create(project, internalUserFactory.create(), TimeUtil.nowTs())) {
      Change.Id changeId = change.getId();
      bu.addOp(changeId, new PersistCopiedVotesOp(approvalsUtil));
      bu.execute();
    }
  }

  private static class PersistCopiedVotesOp implements BatchUpdateOp {
    private final ApprovalsUtil approvalsUtil;

    PersistCopiedVotesOp(ApprovalsUtil approvalsUtil) {
      this.approvalsUtil = approvalsUtil;
    }

    @Override
    public boolean updateChange(ChangeContext ctx) throws IOException {
      ChangeUpdate update = ctx.getUpdate(ctx.getChange().currentPatchSetId());
      approvalsUtil.persistCopiedApprovals(
          ctx.getNotes(),
          ctx.getNotes().getCurrentPatchSet(),
          ctx.getRevWalk(),
          ctx.getRepoView().getConfig(),
          update);
      return update.hasCopiedApprovals();
    }
  }
}
