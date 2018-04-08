// Copyright (C) 2017 The Android Open Source Project
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

import static java.util.stream.Collectors.toList;

import com.google.common.primitives.Ints;
import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.StarredChangesUtil.IllegalLabelException;
import com.google.gerrit.server.StarredChangesUtil.StarRef;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

public class Schema_161 extends SchemaVersion {
  private static final String MUTE_LABEL = "mute";

  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;

  @Inject
  Schema_161(
      Provider<Schema_160> prior, GitRepositoryManager repoManager, AllUsersName allUsersName) {
    super(prior);
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException {
    try (Repository git = repoManager.openRepository(allUsersName);
        RevWalk rw = new RevWalk(git)) {
      BatchRefUpdate bru = git.getRefDatabase().newBatchUpdate();
      bru.setAllowNonFastForwards(true);

      for (Ref ref : git.getRefDatabase().getRefs(RefNames.REFS_STARRED_CHANGES).values()) {
        StarRef starRef = StarredChangesUtil.readLabels(git, ref.getName());

        Set<Integer> mutedPatchSets =
            StarredChangesUtil.getStarredPatchSets(starRef.labels(), MUTE_LABEL);
        if (mutedPatchSets.isEmpty()) {
          continue;
        }

        Set<Integer> reviewedPatchSets =
            StarredChangesUtil.getStarredPatchSets(
                starRef.labels(), StarredChangesUtil.REVIEWED_LABEL);
        Set<Integer> unreviewedPatchSets =
            StarredChangesUtil.getStarredPatchSets(
                starRef.labels(), StarredChangesUtil.UNREVIEWED_LABEL);

        List<String> newLabels =
            starRef
                .labels()
                .stream()
                .map(
                    l -> {
                      if (l.startsWith(MUTE_LABEL)) {
                        Integer mutedPatchSet = Ints.tryParse(l.substring(MUTE_LABEL.length() + 1));
                        if (mutedPatchSet == null) {
                          // unexpected format of mute label, must be a label that was manually
                          // set, just leave it alone
                          return l;
                        }
                        if (!reviewedPatchSets.contains(mutedPatchSet)
                            && !unreviewedPatchSets.contains(mutedPatchSet)) {
                          // convert mute label to reviewed label
                          return StarredChangesUtil.REVIEWED_LABEL + "/" + mutedPatchSet;
                        }
                        // else patch set is muted but has either reviewed or unreviewed label
                        // -> just drop the mute label
                        return null;
                      }
                      return l;
                    })
                .filter(Objects::nonNull)
                .collect(toList());

        ObjectId id = StarredChangesUtil.writeLabels(git, newLabels);
        bru.addCommand(new ReceiveCommand(ref.getTarget().getObjectId(), id, ref.getName()));
      }
      bru.execute(rw, new TextProgressMonitor());
    } catch (IOException | IllegalLabelException ex) {
      throw new OrmException(ex);
    }
  }
}
