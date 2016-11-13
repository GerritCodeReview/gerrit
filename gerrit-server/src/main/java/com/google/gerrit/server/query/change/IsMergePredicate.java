// Copyright (C) 2015 The Android Open Source Project
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

import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.query.change.ChangeQueryBuilder.Arguments;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public class IsMergePredicate extends ChangeOperatorPredicate {
  private final Arguments args;

  public IsMergePredicate(Arguments args, String value) {
    super(ChangeQueryBuilder.FIELD_MERGE, value);
    this.args = args;
  }

  @Override
  public boolean match(ChangeData cd) throws OrmException {
    ObjectId id = ObjectId.fromString(cd.currentPatchSet().getRevision().get());
    try (Repository repo = args.repoManager.openRepository(cd.change().getProject());
        RevWalk rw = CodeReviewCommit.newRevWalk(repo)) {
      RevCommit commit = rw.parseCommit(id);
      return commit.getParentCount() > 1;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public int getCost() {
    return 2;
  }
}
