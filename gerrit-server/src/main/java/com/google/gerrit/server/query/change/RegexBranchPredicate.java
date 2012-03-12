// Copyright (C) 2010 The Android Open Source Project
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

import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.query.OperatorPredicate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;

import dk.brics.automaton.RegExp;
import dk.brics.automaton.RunAutomaton;

class RegexBranchPredicate extends OperatorPredicate<ChangeData, PatchSet> {
  private final Provider<ReviewDb> dbProvider;
  private final RunAutomaton pattern;

  RegexBranchPredicate(Provider<ReviewDb> dbProvider, String re) {
    super(ChangeQueryBuilder.FIELD_BRANCH, re);

    if (re.startsWith("^")) {
      re = re.substring(1);
    }

    if (re.endsWith("$") && !re.endsWith("\\$")) {
      re = re.substring(0, re.length() - 1);
    }

    this.dbProvider = dbProvider;
    this.pattern = new RunAutomaton(new RegExp(re).toAutomaton());
  }

  @Override
  public boolean match(final ChangeData object, final PatchSet subobject)
      throws OrmException {
    Change change = object.change(dbProvider);
    if (change == null) {
      return false;
    }
    return change.getDest().get().startsWith(Branch.R_HEADS)
        && pattern.run(change.getDest().getShortName());
  }

  @Override
  public int getCost() {
    return 1;
  }
}
