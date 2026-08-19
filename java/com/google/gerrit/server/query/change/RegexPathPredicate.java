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

import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.ioutil.RegexAutomatonCompiler;
import com.google.gerrit.server.ioutil.RegexListSearcher;

public class RegexPathPredicate extends ChangeRegexPredicate {
  private final RegexAutomatonCompiler regexAutomatonCompiler;

  public RegexPathPredicate(RegexAutomatonCompiler regexAutomatonCompiler, String re) {
    super(ChangeField.PATH_SPEC, re);
    this.regexAutomatonCompiler = regexAutomatonCompiler;
  }

  @Override
  public boolean match(ChangeData object) {
    return RegexListSearcher.ofStrings(regexAutomatonCompiler, getValue())
        .search(object.currentFilePaths())
        .findAny()
        .isPresent();
  }

  @Override
  public int getCost() {
    return 1;
  }
}
