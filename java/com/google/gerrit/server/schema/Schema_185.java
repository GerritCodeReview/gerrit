// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.schema;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.git.RefUpdateUtil;
import java.util.List;
import java.util.SortedSet;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

/** Compute and persist auto-merge commits for all Gerrit patchset merge commits in Git. */
public class Schema_185 implements NoteDbSchemaVersion {

  /** Perform ref updates in chunks of size 1000. */
  private int REF_UPDATE_CHUNK = 1000;

  @Override
  public void upgrade(Arguments args, UpdateUI ui) throws Exception {
    SortedSet<NameKey> projects = args.repoManager.list();
    for (Project.NameKey project : projects) {
      upgradeForProject(project, args);
    }
  }

  @VisibleForTesting
  public void setRefUpdateChunk(int size) {
    this.REF_UPDATE_CHUNK = size;
  }

  private void upgradeForProject(Project.NameKey project, Arguments args) throws Exception {
    try (Repository repo = args.repoManager.openRepository(project);
        ObjectInserter inserter = repo.newObjectInserter();
        ObjectReader reader = inserter.newReader();
        RevWalk rw = new RevWalk(reader)) {
      int numUpdates = 0;
      BatchRefUpdate bru = createNewBatchRefUpdate(repo);
      List<Ref> changeRefs = repo.getRefDatabase().getRefsByPrefix(RefNames.REFS_CHANGES);
      for (Ref ref : changeRefs) {
        if (RefNames.isNoteDbMetaRef(ref.getName())) {
          continue;
        }
        RevCommit maybeMergeCommit = rw.parseCommit(ref.getObjectId());
        String autoMergeRef = RefNames.refsCacheAutomerge(maybeMergeCommit.name());
        if (maybeMergeCommit.getParentCount() == 2 && !autoMergeExists(repo, rw, autoMergeRef)) {
          ObjectId autoMerge =
              args.autoMerger.createAutoMergeCommit(
                  repo.getConfig(), rw, inserter, maybeMergeCommit, MergeStrategy.RESOLVE);
          bru.addCommand(new ReceiveCommand(ObjectId.zeroId(), autoMerge, autoMergeRef));
          numUpdates += 1;
          if (numUpdates == REF_UPDATE_CHUNK) {
            RefUpdateUtil.executeChecked(bru, rw);
            bru = createNewBatchRefUpdate(repo);
            numUpdates = 0;
          }
        }
      }
      if (numUpdates > 0) {
        RefUpdateUtil.executeChecked(bru, rw);
      }
    }
  }

  private static BatchRefUpdate createNewBatchRefUpdate(Repository repo) {
    BatchRefUpdate bru = repo.getRefDatabase().newBatchUpdate();
    bru.setAllowNonFastForwards(true);
    return bru;
  }

  private static boolean autoMergeExists(Repository repo, RevWalk rw, String refName)
      throws Exception {
    Ref ref = repo.getRefDatabase().exactRef(refName);
    if (ref != null && ref.getObjectId() != null) {
      RevObject obj = rw.parseAny(ref.getObjectId());
      if (obj instanceof RevCommit) {
        return true;
      }
    }
    return false;
  }
}
