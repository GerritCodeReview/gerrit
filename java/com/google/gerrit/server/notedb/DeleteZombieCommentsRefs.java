// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import com.google.common.collect.Iterables;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.update.RefUpdateUtil;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;

/**
 * This class can be used to clean zombie draft comments refs. More context in <a>
 * https://gerrit-review.googlesource.com/c/gerrit/+/246233 </a>
 *
 * <p>An earlier bug in the deletion of draft comments
 * (refs/draft-comments/${change_id_short}/${change_id}/${user_id}) caused some draft refs to remain
 * in Git and not get deleted. These refs point to an empty tree.
 */
public class DeleteZombieCommentsRefs {
  private final String EMPTY_TREE_ID = "4b825dc642cb6eb9a060e54bf8d69288fbee4904";
  private final String DRAFT_REFS_PREFIX = "refs/draft-comments";
  private final int CHUNK_SZ = 100; // log progress after deleting every CHUNK_SZ refs
  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsers;
  private Repository allUsersRepo;

  @Inject
  public DeleteZombieCommentsRefs(AllUsersName allUsers, GitRepositoryManager repoManager) {
    this.allUsers = allUsers;
    this.repoManager = repoManager;
  }

  public void execute() throws IOException {
    allUsersRepo = repoManager.openRepository(allUsers);

    List<Ref> draftRefs = allUsersRepo.getRefDatabase().getRefsByPrefix(DRAFT_REFS_PREFIX);
    List<Ref> zombieRefs = getZombieRefs(draftRefs);

    long zombieRefsCnt = zombieRefs.size();
    long deletedRefsCnt = 0;
    long startTime = System.currentTimeMillis();

    System.out.println(
        String.format("Found %d zombie draft refs in %s repo.", zombieRefs.size(), allUsers.get()));

    for (List<Ref> refsBatch : Iterables.partition(zombieRefs, CHUNK_SZ)) {
      List<ReceiveCommand> deleteCommands =
          refsBatch.stream()
              .map(
                  zombieRef ->
                      new ReceiveCommand(
                          zombieRef.getObjectId(), ObjectId.zeroId(), zombieRef.getName()))
              .collect(Collectors.toList());
      BatchRefUpdate bru = allUsersRepo.getRefDatabase().newBatchUpdate();
      bru.setAtomic(true);
      bru.addCommand(deleteCommands);
      RefUpdateUtil.executeChecked(bru, allUsersRepo);
      long elapsed = (System.currentTimeMillis() - startTime) / 1000;
      deletedRefsCnt += refsBatch.size();
      logProgress(deletedRefsCnt, zombieRefsCnt, elapsed);
    }
  }

  private List<Ref> getZombieRefs(List<Ref> allRefs) throws IOException {
    List<Ref> zombieRefs = new ArrayList<>();
    for (Ref ref : allRefs) {
      if (isZombieRef(allUsersRepo, ref)) {
        zombieRefs.add(ref);
      }
    }
    return zombieRefs;
  }

  private boolean isZombieRef(Repository repo, Ref ref) throws IOException {
    return repo.parseCommit(ref.getObjectId()).getTree().getName().equals(EMPTY_TREE_ID);
  }

  private void logProgress(long deletedRefsCount, long allRefsCount, long elapsed) {
    System.out.format(
        "Deleted %d/%d zombie draft refs (%d seconds)\n", deletedRefsCount, allRefsCount, elapsed);
  }
}
