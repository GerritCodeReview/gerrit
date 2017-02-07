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

package com.google.gerrit.server.project;

import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.gerrit.extensions.api.projects.RefInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import dk.brics.automaton.RegExp;
import dk.brics.automaton.RunAutomaton;
import java.util.List;
import java.util.Locale;

public class RefFilter<T extends RefInfo> {
  private final String prefix;
  private String matchSubstring;
  private String matchRegex;
  private int start;
  private int limit;

  public RefFilter(String prefix) {
    this.prefix = prefix;
  }

  public RefFilter<T> subString(String subString) {
    this.matchSubstring = subString;
    return this;
  }

  public RefFilter<T> regex(String regex) {
    this.matchRegex = regex;
    return this;
  }

  public RefFilter<T> start(int start) {
    this.start = start;
    return this;
  }

  public RefFilter<T> limit(int limit) {
    this.limit = limit;
    return this;
  }

  public List<T> filter(List<T> refs) throws BadRequestException {
    FluentIterable<T> results = FluentIterable.from(refs);
    if (!Strings.isNullOrEmpty(matchSubstring)) {
      results = results.filter(new SubstringPredicate(matchSubstring));
    } else if (!Strings.isNullOrEmpty(matchRegex)) {
      results = results.filter(new RegexPredicate(matchRegex));
    }
    if (start > 0) {
      results = results.skip(start);
    }
    if (limit > 0) {
      results = results.limit(limit);
    }
    return results.toList();
  }

  private class SubstringPredicate implements Predicate<T> {
    private final String substring;

    private SubstringPredicate(String substring) {
      this.substring = substring.toLowerCase(Locale.US);
    }

    @Override
    public boolean apply(T in) {
      String ref = in.ref;
      if (ref.startsWith(prefix)) {
        ref = ref.substring(prefix.length());
      }
      ref = ref.toLowerCase(Locale.US);
      return ref.contains(substring);
    }
  }

  private class RegexPredicate implements Predicate<T> {
    private final RunAutomaton a;

    private RegexPredicate(String regex) throws BadRequestException {
      if (regex.startsWith("^")) {
        regex = regex.substring(1);
        if (regex.endsWith("$") && !regex.endsWith("\\$")) {
          regex = regex.substring(0, regex.length() - 1);
        }
      }
      try {
        a = new RunAutomaton(new RegExp(regex).toAutomaton());
      } catch (IllegalArgumentException e) {
        throw new BadRequestException(e.getMessage());
      }
    }

    @Override
    public boolean apply(T in) {
      String ref = in.ref;
      if (ref.startsWith(prefix)) {
        ref = ref.substring(prefix.length());
      }
      return a.run(ref);
    }
  }
}
