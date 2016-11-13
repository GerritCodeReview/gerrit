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

package com.google.gerrit.common.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Performs replacements on strings such as <code>Hello ${user}</code>. */
public class ParameterizedString {
  /** Obtain a string which has no parameters and always produces the value. */
  public static ParameterizedString asis(final String constant) {
    return new ParameterizedString(new Constant(constant));
  }

  private final String pattern;
  private final String rawPattern;
  private final List<Format> patternOps;
  private final List<Parameter> parameters;

  protected ParameterizedString() {
    this(new Constant(""));
  }

  private ParameterizedString(final Constant c) {
    pattern = c.text;
    rawPattern = c.text;
    patternOps = Collections.<Format>singletonList(c);
    parameters = Collections.emptyList();
  }

  public ParameterizedString(final String pattern) {
    final StringBuilder raw = new StringBuilder();
    final List<Parameter> prs = new ArrayList<>(4);
    final List<Format> ops = new ArrayList<>(4);

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

      // "${parameter[.functions...]}" -> "parameter[.functions...]"
      final Parameter p = new Parameter(pattern.substring(b + 2, e));

      raw.append("{").append(prs.size()).append("}");
      prs.add(p);
      ops.add(p);

      i = e + 1;
    }
    if (i < pattern.length()) {
      raw.append(pattern.substring(i));
      ops.add(new Constant(pattern.substring(i)));
    }

    this.pattern = pattern;
    this.rawPattern = raw.toString();
    this.patternOps = Collections.unmodifiableList(ops);
    this.parameters = Collections.unmodifiableList(prs);
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
    final ArrayList<String> r = new ArrayList<>(parameters.size());
    for (Parameter p : parameters) {
      r.add(p.name);
    }
    return Collections.unmodifiableList(r);
  }

  /** Convert a map of parameters into a value array for binding. */
  public String[] bind(final Map<String, String> params) {
    final String[] r = new String[parameters.size()];
    for (int i = 0; i < r.length; i++) {
      final StringBuilder b = new StringBuilder();
      parameters.get(i).format(b, params);
      r[i] = b.toString();
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

  @Override
  public String toString() {
    return getPattern();
  }

  public final class Builder {
    private final Map<String, String> params = new HashMap<>();

    public Builder replace(final String name, final String value) {
      params.put(name, value);
      return this;
    }

    @Override
    public String toString() {
      return ParameterizedString.this.replace(params);
    }
  }

  private abstract static class Format {
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
    private final List<Function> functions;

    Parameter(final String parameter) {
      // "parameter[.functions...]" -> (parameter, functions...)
      final List<String> names = Arrays.asList(parameter.split("\\."));
      final List<Function> functs = new ArrayList<>(names.size());

      if (names.isEmpty()) {
        name = "";
      } else {
        name = names.get(0);

        for (String fname : names.subList(1, names.size())) {
          final Function function = FUNCTIONS.get(fname);
          if (function != null) {
            functs.add(function);
          }
        }
      }

      functions = Collections.unmodifiableList(functs);
    }

    @Override
    void format(StringBuilder b, Map<String, String> p) {
      String v = p.get(name);
      if (v == null) {
        v = "";
      }
      for (Function function : functions) {
        v = function.apply(v);
      }
      b.append(v);
    }
  }

  private abstract static class Function {
    abstract String apply(String a);
  }

  private static final Map<String, Function> FUNCTIONS = initFunctions();

  private static Map<String, Function> initFunctions() {
    HashMap<String, Function> m = new HashMap<>();
    m.put(
        "toLowerCase",
        new Function() {
          @Override
          String apply(String a) {
            return a.toLowerCase();
          }
        });
    m.put(
        "toUpperCase",
        new Function() {
          @Override
          String apply(String a) {
            return a.toUpperCase();
          }
        });
    m.put(
        "localPart",
        new Function() {
          @Override
          String apply(String a) {
            int at = a.indexOf('@');
            return at < 0 ? a : a.substring(0, at);
          }
        });
    return Collections.unmodifiableMap(m);
  }
}
