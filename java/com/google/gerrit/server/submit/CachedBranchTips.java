package com.google.gerrit.server.submit;

import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.submit.MergeOpRepoManager.OpenRepo;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.Ref;

/**
 * Current branch tips, taking into account commits created during the submit process as well as
 * submodule updates produced by this class.
 */
public class CachedBranchTips {

  private final Map<BranchNameKey, CodeReviewCommit> branchTips = new HashMap<>();

  /**
   * Returns current tip of the branch, taking into account commits created during the submit
   * process or submodule updates.
   *
   * @param branch branch
   * @param repo repository to look for the branch if not cached
   * @return the current tip. Empty if the branch doesn't exist in the repository
   * @throws IOException Cannot access the underlying storage
   */
  public Optional<CodeReviewCommit> getTip(BranchNameKey branch, OpenRepo repo) throws IOException {
    CodeReviewCommit currentCommit;
    if (branchTips.containsKey(branch)) {
      currentCommit = branchTips.get(branch);
    } else {
      Ref r = repo.repo.exactRef(branch.branch());
      if (r == null) {
        return Optional.empty();
      }
      currentCommit = repo.rw.parseCommit(r.getObjectId());
      branchTips.put(branch, currentCommit);
    }

    return Optional.of(currentCommit);
  }

  void put(BranchNameKey branch, CodeReviewCommit c) {
    branchTips.put(branch, c);
  }
}
