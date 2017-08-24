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

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gwtorm.server.OrmException;
import dk.brics.automaton.RegExp;
import dk.brics.automaton.RunAutomaton;

public class RegexProjectPredicate extends ChangeRegexPredicate {
  protected final RunAutomaton pattern;

  public RegexProjectPredicate(String re) {
    super(ChangeField.PROJECT, re);

    if (re.startsWith("^")) {
      re = re.substring(1);
    }

    if (re.endsWith("$") && !re.endsWith("\\$")) {
      re = re.substring(0, re.length() - 1);
    }

    this.pattern = new RunAutomaton(new RegExp(re).toAutomaton());
  }

  @Override
  public boolean match(ChangeData object) throws OrmException {
    Change change = object.change();
    if (change == null) {
      return false;
    }

    Project.NameKey p = change.getDest().getParentKey();
    return pattern.run(p.get());
  }

  @Override
  public int getCost() {
    return 1;
  }
}
