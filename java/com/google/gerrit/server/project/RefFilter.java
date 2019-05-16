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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.api.projects.RefInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import dk.brics.automaton.RegExp;
import dk.brics.automaton.RunAutomaton;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

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

  public ImmutableList<T> filter(List<T> refs) throws BadRequestException {
    if (!Strings.isNullOrEmpty(matchSubstring) && !Strings.isNullOrEmpty(matchRegex)) {
      throw new BadRequestException("specify exactly one of m/r");
    }
    Stream<T> results = refs.stream();
    if (!Strings.isNullOrEmpty(matchSubstring)) {
      String lowercaseSubstring = matchSubstring.toLowerCase(Locale.US);
      results = results.filter(refInfo -> matchesSubstring(prefix, lowercaseSubstring, refInfo));
    } else if (!Strings.isNullOrEmpty(matchRegex)) {
      RunAutomaton a = parseRegex(matchRegex);
      results = results.filter(refInfo -> matchesRegex(prefix, a, refInfo));
    }
    if (start > 0) {
      results = results.skip(start);
    }
    if (limit > 0) {
      results = results.limit(limit);
    }
    return results.collect(toImmutableList());
  }

  private static <T extends RefInfo> boolean matchesSubstring(
      String prefix, String lowercaseSubstring, T refInfo) {
    String ref = refInfo.ref;
    if (ref.startsWith(prefix)) {
      ref = ref.substring(prefix.length());
    }
    ref = ref.toLowerCase(Locale.US);
    return ref.contains(lowercaseSubstring);
  }

  private static RunAutomaton parseRegex(String regex) throws BadRequestException {
    if (regex.startsWith("^")) {
      regex = regex.substring(1);
      if (regex.endsWith("$") && !regex.endsWith("\\$")) {
        regex = regex.substring(0, regex.length() - 1);
      }
    }
    try {
      return new RunAutomaton(new RegExp(regex).toAutomaton());
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(e.getMessage());
    }
  }

  private static <T extends RefInfo> boolean matchesRegex(
      String prefix, RunAutomaton a, T refInfo) {
    String ref = refInfo.ref;
    if (ref.startsWith(prefix)) {
      ref = ref.substring(prefix.length());
    }
    return a.run(ref);
  }
}
