// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.submitrequirement.predicate;

import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.SubmitRequirementPredicate;
import dk.brics.automaton.RegExp;
import dk.brics.automaton.RunAutomaton;
import java.util.Optional;

/**
 * A submit requirement predicate that matches with changes having the uploader's email address
 * matching a specific regular expression pattern.
 */
@AutoFactory
public class RegexUploaderEmailPredicate extends SubmitRequirementPredicate {
  protected final RunAutomaton uploaderEmailPattern;
  private final AccountCache accountCache;

  public RegexUploaderEmailPredicate(@Provided AccountCache accountCache, String pattern)
      throws QueryParseException {
    super("uploaderemail", pattern);
    this.accountCache = accountCache;

    if (pattern.startsWith("^")) {
      pattern = pattern.substring(1);
    }

    if (pattern.endsWith("$") && !pattern.endsWith("\\$")) {
      pattern = pattern.substring(0, pattern.length() - 1);
    }

    try {
      this.uploaderEmailPattern = new RunAutomaton(new RegExp(pattern).toAutomaton());
    } catch (IllegalArgumentException e) {
      throw new QueryParseException(String.format("invalid regular expression: %s", pattern), e);
    }
  }

  @Override
  public boolean match(ChangeData cd) {
    Optional<AccountState> accountState = accountCache.get(cd.currentPatchSet().uploader());
    if (!accountState.isPresent()) {
      return false;
    }
    String email = accountState.get().account().preferredEmail();
    return email == null ? false : uploaderEmailPattern.run(email);
  }

  @Override
  public int getCost() {
    return 1;
  }
}
