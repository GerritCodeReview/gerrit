// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.util;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.primitives.Chars;
import dk.brics.automaton.Automaton;
import dk.brics.automaton.RegExp;
import dk.brics.automaton.RunAutomaton;
import java.util.Collections;
import java.util.List;

/** Helper to search sorted lists for elements matching a regex. */
public abstract class RegexListSearcher<T> implements Function<T, String> {
  public static RegexListSearcher<String> ofStrings(String re) {
    return new RegexListSearcher<String>(re) {
      @Override
      public String apply(String in) {
        return in;
      }
    };
  }

  private final RunAutomaton pattern;

  private final String prefixBegin;
  private final String prefixEnd;
  private final int prefixLen;
  private final boolean prefixOnly;

  public RegexListSearcher(String re) {
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

  public Iterable<T> search(List<T> list) {
    checkNotNull(list);
    int begin;
    int end;

    if (0 < prefixLen) {
      // Assumes many consecutive elements may have the same prefix, so the cost
      // of two binary searches is less than iterating to find the endpoints.
      begin = find(list, prefixBegin);
      end = find(list, prefixEnd);
    } else {
      begin = 0;
      end = list.size();
    }

    if (prefixOnly) {
      return begin < end ? list.subList(begin, end) : ImmutableList.<T>of();
    }

    return Iterables.filter(list.subList(begin, end), x -> pattern.run(apply(x)));
  }

  public boolean hasMatch(List<T> list) {
    return !Iterables.isEmpty(search(list));
  }

  private int find(List<T> list, String p) {
    int r = Collections.binarySearch(Lists.transform(list, this), p);
    return r < 0 ? -(r + 1) : r;
  }
}
