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

import com.google.common.flogger.FluentLogger;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;

/**
 * This class can be used to clean zombie draft comments refs. More context in
 * https://gerrit-review.googlesource.com/c/gerrit/+/246233
 *
 * <p>An earlier bug in the deletion of draft comments
 * (refs/draft-comments/${change_id_short}/${change_id}/${user_id}) caused some draft refs to remain
 * in Git and not get deleted. These refs point to an empty tree.
 */
public class DeleteDeadDraftCommentsRefs {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final String EMPTY_TREE_ID = "4b825dc642cb6eb9a060e54bf8d69288fbee4904";
  private final String DRAFT_COMMENTS_SUBSTR = "draft-comments";
  private final Repository repo;

  public DeleteDeadDraftCommentsRefs(Repository repo) {
    this.repo = repo;
  }

  public void execute() throws IOException {
    Collection<Ref> allRefs = repo.getRefDatabase().getRefs();

    List<Ref> draftCommentsRefs =
        allRefs.stream()
            .filter(ref -> ref.getName().contains(DRAFT_COMMENTS_SUBSTR))
            .collect(Collectors.toList());

    draftCommentsRefs.forEach(ref -> deleteRefIfDead(repo, ref));
  }

  private void deleteRefIfDead(Repository repo, Ref ref) {
    try {
      String treeId = repo.parseCommit(ref.getObjectId()).getTree().getName();
      if (treeId.equals(EMPTY_TREE_ID)) {
        RefUpdate refUpdate = repo.updateRef(ref.getName());
        refUpdate.setForceUpdate(true);
        refUpdate.delete();
      }
    } catch (IOException e) {
      logger.atWarning().log("Failed to delete ref with name " + ref.getName());
    }
  }
}
