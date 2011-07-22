// Copyright (C) 2011 The Android Open Source Project
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

import static com.google.gerrit.server.project.RefControl.isRE;

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.ParameterizedString;

import dk.brics.automaton.Automaton;

import java.util.Collections;
import java.util.regex.Pattern;

/**
 * Matches an AccessSection against a reference name.
 * <p>
 * These matchers are "compiled" versions of the AccessSection name, supporting
 * faster selection of which sections are relevant to any given input reference.
 */
abstract class SectionMatcher {
  static SectionMatcher wrap(AccessSection section) {
    String ref = section.getName();
    if (AccessSection.isAccessSection(ref)) {
      return wrap(ref, section);
    } else {
      return null;
    }
  }

  static SectionMatcher wrap(String pattern, AccessSection section) {
    if (pattern.contains("${")) {
      return new ExpandParameters(pattern, section);

    } else if (isRE(pattern)) {
      return new Regexp(pattern, section);

    } else if (pattern.endsWith("/*")) {
      return new Prefix(pattern.substring(0, pattern.length() - 1), section);

    } else {
      return new Exact(pattern, section);
    }
  }

  final AccessSection section;

  SectionMatcher(AccessSection section) {
    this.section = section;
  }

  abstract boolean match(String ref, String username);

  private static class Exact extends SectionMatcher {
    private final String expect;

    Exact(String name, AccessSection section) {
      super(section);
      expect = name;
    }

    @Override
    boolean match(String ref, String username) {
      return expect.equals(ref);
    }
  }

  private static class Prefix extends SectionMatcher {
    private final String prefix;

    Prefix(String pfx, AccessSection section) {
      super(section);
      prefix = pfx;
    }

    @Override
    boolean match(String ref, String username) {
      return ref.startsWith(prefix);
    }
  }

  private static class Regexp extends SectionMatcher {
    private final Pattern pattern;

    Regexp(String re, AccessSection section) {
      super(section);
      pattern = Pattern.compile(re);
    }

    @Override
    boolean match(String ref, String username) {
      return pattern.matcher(ref).matches();
    }
  }

  static class ExpandParameters extends SectionMatcher {
    private final ParameterizedString template;
    private final String prefix;

    ExpandParameters(String pattern, AccessSection section) {
      super(section);
      template = new ParameterizedString(pattern);

      if (isRE(pattern)) {
        // Replace ${username} with ":USERNAME:" as : is not legal
        // in a reference and the string :USERNAME: is not likely to
        // be a valid part of the regex. This later allows the pattern
        // prefix to be clipped, saving time on evaluation.
        Automaton am = RefControl.toRegExp(
            template.replace(Collections.singletonMap("username", ":USERNAME:")))
            .toAutomaton();
        String rePrefix = am.getCommonPrefix();
        prefix = rePrefix.substring(0, rePrefix.indexOf(":USERNAME:"));
      } else {
        prefix = pattern.substring(0, pattern.indexOf("${"));
      }
    }

    @Override
    boolean match(String ref, String username) {
      if (!ref.startsWith(prefix) || username == null) {
        return false;
      }

      String u;
      if (isRE(template.getPattern())) {
        u = username.replace(".", "\\.");
      } else {
        u = username;
      }

      SectionMatcher next = wrap(
          template.replace(Collections.singletonMap("username", u)),
          section);
      return next != null ? next.match(ref, username) : false;
    }

   boolean matchPrefix(String ref) {
     return ref.startsWith(prefix);
    }
  }
}
