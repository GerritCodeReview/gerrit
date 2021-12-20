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

import static com.google.common.collect.ImmutableListMultimap.toImmutableListMultimap;

import com.google.common.collect.ImmutableListMultimap;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.server.approval.ApprovalsUtil;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.util.time.TimeUtil;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Schema 185 for Gerrit metadata.
 *
 * <p>Upgrading to this schema version will rename persist copied votes on open changes, as the next
 * version will no longer compute copied votes on demand (but just look in storage).
 */
public class Schema_185 implements NoteDbSchemaVersion {
  @Override
  public void upgrade(Arguments args, UpdateUI ui) throws Exception {
    List<ChangeData> openChanges = args.queryProvider.get().byOpen();
    ImmutableListMultimap<NameKey, ChangeData> openChangesMap =
        openChanges.stream().collect(toImmutableListMultimap(c -> c.project(), c -> c));

    for (Project.NameKey project : openChangesMap.keySet()) {
      try (BatchUpdate bu =
              args.updateFactory.create(project, args.userProvider.get(), TimeUtil.nowTs());
          Repository repository = args.repoManager.openRepository(project);
          RevWalk unused = new RevWalk(repository)) {
        for (ChangeData changeData : openChangesMap.get(project)) {
          bu.addOp(changeData.getId(), new PersistCopiedVotesOp(args.approvalsUtil));
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
      return true;
    }
  }
}
