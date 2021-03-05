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

import static com.google.gerrit.server.index.change.ChangeField.HASHTAG;

import dk.brics.automaton.RegExp;
import dk.brics.automaton.RunAutomaton;

public class RegexHashtagPredicate extends ChangeRegexPredicate {
  protected final RunAutomaton pattern;

  public RegexHashtagPredicate(String re) {
    super(HASHTAG, re);

    if (re.startsWith("^")) {
      re = re.substring(1);
    }

    if (re.endsWith("$") && !re.endsWith("\\$")) {
      re = re.substring(0, re.length() - 1);
    }

    this.pattern = new RunAutomaton(new RegExp(re).toAutomaton());
  }

  @Override
  public boolean match(ChangeData cd) {
    if (cd.hashtags().isEmpty()) {
      return false;
    }
    return cd.hashtags().stream().anyMatch(ht -> pattern.run(ht));
  }

  @Override
  public int getCost() {
    return 1;
  }
}
