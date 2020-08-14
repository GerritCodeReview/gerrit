package com.google.gerrit.server.git.receive;

import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.server.IdentifiedUser;
import java.util.Set;

/** Updates superprojects that reference modified branches */
interface SuperprojectsUpdater {

  /**
   * Update gitlinks on superprojects with submodules pointing to any of the {@code branches}
   *
   * @param user the calling user
   * @param branches updated branches
   */
  void update(IdentifiedUser user, Set<BranchNameKey> branches);
}
