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

package com.google.gerrit.server.query.change;

import com.google.common.collect.Sets;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.query.Matchable;
import com.google.gerrit.index.query.OperatorPredicate;
import com.google.gerrit.server.git.GitRepositoryManager;
import java.io.IOException;
import java.util.Set;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public class ParentOfPredicate extends OperatorPredicate<ChangeData>
    implements Matchable<ChangeData> {
  protected final Set<RevCommit> parents;

  public ParentOfPredicate(String value, ChangeData change, GitRepositoryManager repoManager) {
    super(ChangeQueryBuilder.FIELD_PARENTOF, value);
    this.parents = getParentChangeIds(change, repoManager);
  }

  @Override
  public boolean match(ChangeData changeData) {
    return changeData.patchSets().stream().anyMatch(ps -> parents.contains(ps.commitId()));
  }

  @Override
  public int getCost() {
    return 1;
  }

  protected Set<RevCommit> getParentChangeIds(ChangeData change, GitRepositoryManager repoManager) {
    PatchSet ps = change.currentPatchSet();
    try (Repository repo = repoManager.openRepository(change.project());
        RevWalk walk = new RevWalk(repo)) {
      RevCommit c = walk.parseCommit(ps.commitId());
      return Sets.newHashSet(c.getParents());
    } catch (IOException e) {
      throw new StorageException(
          String.format(
              "Loading commit %s for ps %d of change %d failed.",
              ps.commitId(), ps.id().get(), ps.id().changeId().get()),
          e);
    }
  }
}
