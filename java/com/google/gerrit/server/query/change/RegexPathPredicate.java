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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.ioutil.RegexCompiler;
import com.google.gerrit.server.ioutil.RegexListSearcher;

public class RegexPathPredicate extends ChangeRegexPredicate {
  private static final String REGEX_META_CHARACTERS = ".*+?()[]{}|\\^$";
  private final RegexListSearcher<String> searcher;

  public static Predicate<ChangeData> create(ChangeQueryBuilder.Arguments args, String re)
      throws QueryParseException {
    RegexPathPredicate regexPredicate = new RegexPathPredicate(re, args.regexCompiler);
    String dirPrefix = extractDirectoryPrefix(re);
    if (dirPrefix != null && !dirPrefix.isEmpty()) {
      if (isPureDirectoryPrefix(re, dirPrefix)) {
        return ChangePredicates.directory(dirPrefix);
      }
      return Predicate.and(ChangePredicates.directory(dirPrefix), regexPredicate);
    }
    return regexPredicate;
  }

  @Nullable
  public static String extractDirectoryPrefix(String re) {
    if (!re.startsWith("^")) {
      return null;
    }
    String stripped = re.substring(1);
    StringBuilder literalPrefix = new StringBuilder();
    for (int i = 0; i < stripped.length(); i++) {
      char c = stripped.charAt(i);
      if (REGEX_META_CHARACTERS.indexOf(c) != -1) {
        break;
      }
      literalPrefix.append(c);
    }
    int lastSlash = literalPrefix.lastIndexOf("/");
    if (lastSlash <= 0) {
      return null;
    }
    String dir = literalPrefix.substring(0, lastSlash);
    while (dir.startsWith("/")) {
      dir = dir.substring(1);
    }
    return dir.isEmpty() ? null : dir;
  }

  public static boolean isPureDirectoryPrefix(String re, String dirPrefix) {
    if (!re.startsWith("^")) {
      return false;
    }
    String stripped = re.substring(1);
    if (stripped.endsWith("$") && !stripped.endsWith("\\$")) {
      stripped = stripped.substring(0, stripped.length() - 1);
    }
    String expectedPrefix = dirPrefix + "/";
    if (!stripped.startsWith(expectedPrefix)) {
      return false;
    }
    String remainder = stripped.substring(expectedPrefix.length());
    return remainder.equals(".*")
        || remainder.equals(".+")
        || remainder.equals("(.*)")
        || remainder.equals("(.+)");
  }

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
