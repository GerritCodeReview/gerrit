// Copyright (C) 2013 The Android Open Source Project
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
import com.google.gerrit.common.data.ParameterizedString;
import dk.brics.automaton.Automaton;
import java.util.Collections;
import java.util.regex.Pattern;

abstract class RefPatternMatcher {
  public static RefPatternMatcher getMatcher(String pattern) {
    if (pattern.contains("${")) {
      return new ExpandParameters(pattern);
    } else if (isRE(pattern)) {
      return new Regexp(pattern);
    } else if (pattern.endsWith("/*")) {
      return new Prefix(pattern.substring(0, pattern.length() - 1));
    } else {
      return new Exact(pattern);
    }
  }

  abstract boolean match(String ref, String username);

  private static class Exact extends RefPatternMatcher {
    private final String expect;

    Exact(String name) {
      expect = name;
    }

    @Override
    boolean match(String ref, String username) {
      return expect.equals(ref);
    }
  }

  private static class Prefix extends RefPatternMatcher {
    private final String prefix;

    Prefix(String pfx) {
      prefix = pfx;
    }

    @Override
    boolean match(String ref, String username) {
      return ref.startsWith(prefix);
    }
  }

  private static class Regexp extends RefPatternMatcher {
    private final Pattern pattern;

    Regexp(String re) {
      pattern = Pattern.compile(re);
    }

    @Override
    boolean match(String ref, String username) {
      return pattern.matcher(ref).matches();
    }
  }

  static class ExpandParameters extends RefPatternMatcher {
    private final ParameterizedString template;
    private final String prefix;

    ExpandParameters(String pattern) {
      template = new ParameterizedString(pattern);

      if (isRE(pattern)) {
        // Replace ${username} with ":USERNAME:" as : is not legal
        // in a reference and the string :USERNAME: is not likely to
        // be a valid part of the regex. This later allows the pattern
        // prefix to be clipped, saving time on evaluation.
        Automaton am =
            RefControl.toRegExp(
                template.replace(Collections.singletonMap("username",
                    ":USERNAME:"))).toAutomaton();
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

      RefPatternMatcher next =
          getMatcher(template.replace(Collections.singletonMap("username", u)));
      return next != null ? next.match(ref, username) : false;
    }

    boolean matchPrefix(String ref) {
      return ref.startsWith(prefix);
    }
  }
}
