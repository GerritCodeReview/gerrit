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
    for (Project.NameKey project : repositoryManager.list()) {
      try (BatchUpdate bu =
              batchUpdateFactory.create(project, internalUserFactory.create(), TimeUtil.now());
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
          update,
          /* legacyIncludePreviousPatchsets= */ true);
      return update.hasCopiedApprovals();
    }
  }
}
