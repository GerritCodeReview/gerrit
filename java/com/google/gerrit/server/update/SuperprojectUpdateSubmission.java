package com.google.gerrit.server.update;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.submit.MergeOpRepoManager;
import com.google.gerrit.server.submit.SubmoduleOp;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Provider;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.Constants;

/** Submission that updates superprojects on completion */
public class SuperprojectUpdateSubmission implements SubmissionListener {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Provider<MergeOpRepoManager> ormProvider;
  private final IdentifiedUser user;
  private SubmoduleOp.Factory subOpFactory;
  private ImmutableList<BatchUpdate> batchUpdates;
  private Set<BranchNameKey> updatedBranches = new HashSet<>();
  private boolean dryrun;

  public SuperprojectUpdateSubmission(
      Provider<MergeOpRepoManager> ormProvider,
      IdentifiedUser user,
      SubmoduleOp.Factory subOpFactory) {
    this.ormProvider = ormProvider;
    this.user = user;
    this.subOpFactory = subOpFactory;
  }

  @Override
  public void setBatchUpdates(boolean dryrun, Collection<BatchUpdate> updates) {
    if (batchUpdates != null) {
      // This is a retry. Save previous updates, as they are not in the new BatchUpdate.
      collectSuccessfullUpdates();
    }
    this.batchUpdates = ImmutableList.copyOf(updates);
    this.dryrun = dryrun;
  }

  @Override
  public void completed() {
    collectSuccessfullUpdates();
    // Update superproject gitlinks if required.
    if (!dryrun && !updatedBranches.isEmpty()) {
      try (MergeOpRepoManager orm = ormProvider.get()) {
        orm.setContext(TimeUtil.nowTs(), user, NotifyResolver.Result.none());
        SubmoduleOp op = subOpFactory.create(updatedBranches, orm);
        op.updateSuperProjects();
      } catch (RestApiException e) {
        logger.atWarning().withCause(e).log("Can't update the superprojects");
      }
    }
  }

  @Override
  public BatchUpdateListener getBatchListener() {
    return BatchUpdateListener.NONE;
  }

  private void collectSuccessfullUpdates() {
    if (this.batchUpdates != null) {
      Set<BranchNameKey> branches =
          this.batchUpdates.stream()
              .flatMap(bu -> bu.getSuccessfullyUpdatedBranches(dryrun))
              .filter(branch -> isHead(branch) || isConfig(branch))
              .collect(Collectors.toSet());
      updatedBranches.addAll(branches);
    }
  }

  private static boolean isHead(BranchNameKey branchNameKey) {
    return branchNameKey.branch().startsWith(Constants.R_HEADS);
  }

  private static boolean isConfig(BranchNameKey branchNameKey) {
    return branchNameKey.branch().equals(RefNames.REFS_CONFIG);
  }
}
