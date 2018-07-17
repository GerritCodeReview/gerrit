// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.ioutil;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.Lists;
import com.google.common.primitives.Chars;
import dk.brics.automaton.Automaton;
import dk.brics.automaton.RegExp;
import dk.brics.automaton.RunAutomaton;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

/** Helper to search sorted lists for elements matching a {@link RegExp}. */
public final class RegexListSearcher<T> {
  public static RegexListSearcher<String> ofStrings(String re) {
    return new RegexListSearcher<>(re, Function.identity());
  }

  private final RunAutomaton pattern;
  private final Function<T, String> toStringFunc;

  private final String prefixBegin;
  private final String prefixEnd;
  private final int prefixLen;
  private final boolean prefixOnly;

  public RegexListSearcher(String re, Function<T, String> toStringFunc) {
    this.toStringFunc = checkNotNull(toStringFunc);

    if (re.startsWith("^")) {
      re = re.substring(1);
    }

    if (re.endsWith("$") && !re.endsWith("\\$")) {
      re = re.substring(0, re.length() - 1);
    }

    Automaton automaton = new RegExp(re).toAutomaton();
    prefixBegin = automaton.getCommonPrefix();
    prefixLen = prefixBegin.length();

    if (0 < prefixLen) {
      char max = Chars.checkedCast(prefixBegin.charAt(prefixLen - 1) + 1);
      prefixEnd = prefixBegin.substring(0, prefixLen - 1) + max;
      prefixOnly = re.equals(prefixBegin + ".*");
    } else {
      prefixEnd = "";
      prefixOnly = false;
    }

    pattern = prefixOnly ? null : new RunAutomaton(automaton);
  }

  public Stream<T> search(List<T> list) {
    checkNotNull(list);
    int begin;
    int end;

    if (0 < prefixLen) {
      // Assumes many consecutive elements may have the same prefix, so the cost of two binary
      // searches is less than iterating linearly and running the regexp find the endpoints.
      List<String> strings = Lists.transform(list, toStringFunc::apply);
      begin = find(strings, prefixBegin);
      end = find(strings, prefixEnd);
    } else {
      begin = 0;
      end = list.size();
    }
    if (begin >= end) {
      return Stream.empty();
    }

    Stream<T> result = list.subList(begin, end).stream();
    if (!prefixOnly) {
      result = result.filter(x -> pattern.run(toStringFunc.apply(x)));
    }
    return result;
  }

  private static int find(List<String> list, String p) {
    int r = Collections.binarySearch(list, p);
    return r < 0 ? -(r + 1) : r;
  }
}
