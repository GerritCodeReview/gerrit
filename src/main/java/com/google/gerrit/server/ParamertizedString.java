// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Performs replacements on strings such as <code>Hello ${user}</code>. */
public class ParamertizedString {
  private final String pattern;
  private final String rawPattern;
  private final List<Format> patternOps;
  private final List<String> patternArgs;

  public ParamertizedString(final String pattern) {
    final StringBuilder raw = new StringBuilder();
    final List<String> args = new ArrayList<String>(4);
    final List<Format> ops = new ArrayList<Format>(4);

    int i = 0;
    while (i < pattern.length()) {
      final int b = pattern.indexOf("${", i);
      if (b < 0) {
        break;
      }
      final int e = pattern.indexOf("}", b + 2);
      if (e < 0) {
        break;
      }

      raw.append(pattern.substring(i, b));
      ops.add(new Constant(pattern.substring(i, b)));

      raw.append("{" + args.size() + "}");
      args.add(pattern.substring(b + 2, e));
      ops.add(new Parameter(pattern.substring(b + 2, e)));

      i = e + 1;
    }
    if (i < pattern.length()) {
      raw.append(pattern.substring(i));
      ops.add(new Constant(pattern.substring(i)));
    }

    this.pattern = pattern;
    this.rawPattern = raw.toString();
    this.patternOps = Collections.unmodifiableList(ops);
    this.patternArgs = Collections.unmodifiableList(args);
  }

  /** Get the original pattern given to the constructor. */
  public String getPattern() {
    return pattern;
  }

  /** Get the pattern with variables replaced with {0}, {1}, ... */
  public String getRawPattern() {
    return rawPattern;
  }

  /** Get the list of parameter names, ordered by appearance in the pattern. */
  public List<String> getParameterNames() {
    return patternArgs;
  }

  /** Convert a map of parameters into a value array for binding. */
  public String[] bind(final Map<String, String> params) {
    final String[] r = new String[patternArgs.size()];
    for (int i = 0; i < r.length; i++) {
      r[i] = params.get(patternArgs.get(i));
      if (r[i] == null) {
        r[i] = "";
      }
    }
    return r;
  }

  /** Format this string by performing the variable replacements. */
  public String replace(final Map<String, String> params) {
    final StringBuilder r = new StringBuilder();
    for (final Format f : patternOps) {
      f.format(r, params);
    }
    return r.toString();
  }

  public Builder replace(final String name, final String value) {
    return new Builder().replace(name, value);
  }

  public Builder replace() {
    return new Builder();
  }

  @Override
  public String toString() {
    return getPattern();
  }

  public final class Builder {
    private final Map<String, String> params = new HashMap<String, String>();

    public Builder replace(final String name, final String value) {
      params.put(name, value);
      return this;
    }

    @Override
    public String toString() {
      return ParamertizedString.this.replace(params);
    }
  }

  private static abstract class Format {
    abstract void format(StringBuilder b, Map<String, String> p);
  }

  private static class Constant extends Format {
    private final String text;

    Constant(final String text) {
      this.text = text;
    }

    @Override
    void format(StringBuilder b, Map<String, String> p) {
      b.append(text);
    }
  }

  private static class Parameter extends Format {
    private final String name;

    Parameter(final String name) {
      this.name = name;
    }

    @Override
    void format(StringBuilder b, Map<String, String> p) {
      String v = p.get(name);
      if (v != null) {
        b.append(v);
      }
    }
  }
}
