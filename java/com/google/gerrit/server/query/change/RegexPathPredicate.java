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

import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.ioutil.RegexCompiler;
import com.google.gerrit.server.ioutil.RegexListSearcher;

public class RegexPathPredicate extends ChangeRegexPredicate {
  private final RegexListSearcher<String> searcher;

  public RegexPathPredicate(String re, RegexCompiler regexCompiler) throws QueryParseException {
    super(ChangeField.PATH_SPEC, re);

    if (re.startsWith("^")) {
      re = re.substring(1);
    }
    if (re.endsWith("$") && !re.endsWith("\\$")) {
      re = re.substring(0, re.length() - 1);
    }

    try {
      searcher = RegexListSearcher.ofStrings(re, regexCompiler);
    } catch (IllegalArgumentException e) {
      throw new QueryParseException(
          String.format("invalid regular expression '%s': %s", re, e.getMessage()), e);
    }
  }

  @Override
  public boolean match(ChangeData object) {
    return searcher.search(object.currentFilePaths()).findAny().isPresent();
  }

  @Override
  public int getCost() {
    return 1;
  }
}
