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

package com.google.gerrit.common.data;

import com.google.gerrit.common.errors.InvalidRegExpException;

import java.util.Set;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.RegExp;

public class RefUtil {

  /**
   * Checks if any of the given refs is a super set of ref2.
   *
   * @param refs a set of refs, ref patterns and ref regular expressions
   * @param ref2 a ref, a ref pattern or a ref regular expression
   * @return <code>true</code> if any of the given refs is a super set of ref2,
   *         otherwise <code>false</code>
   */
  public static boolean isAnySuperSet(final Set<String> refs, final String ref2) {
    if (refs.contains(ref2)) {
      return true;
    }

    for (final String r : refs) {
      if (isSuperSet(r, ref2)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Checks whether ref1 is a superset of ref2.
   *
   * @param ref1 a ref, a ref pattern or a ref regular expression
   * @param ref2 a ref, a ref pattern or a ref regular expression
   * @return <code>true</code> if ref1 is a superset of ref2, otherwise
   *         <code>false</code>
   * @throws InvalidRegExpException is thrown if a syntactically incorrect
   *         regular expression was given as ref1 or ref2
   */
  public static boolean isSuperSet(final String ref1, final String ref2)
      throws InvalidRegExpException {
    if (isRegExp(ref2)) {
      return isSuperSet(toAutomaton(ref1), toAutomaton(ref2));
    }

    if (isRegExp(ref1)) {
      if (isRefPattern(ref2)) {
        return isSuperSet(toAutomaton(ref1),
            toAutomaton(refPatternToRegExp(ref2)));
      }
      return toAutomaton(ref1).run(ref2);
    }

    if (isRefPattern(ref1)) {
      final String prefix = ref1.substring(0, ref1.length() - 1);
      return ref2.startsWith(prefix);
    }

    return ref1.equals(ref2);
  }

  /**
   * Checks whether the given ref is a regular expression.
   *
   * @param ref a ref, a ref pattern or a ref regular expression
   * @return <code>true</code> if the given ref is a regular expression,
   *         otherwise <code>false</code>
   */
  public static boolean isRegExp(final String ref) {
    return ref.startsWith(AccessSection.REGEX_PREFIX);
  }

  /**
   * Checks whether the given ref is a ref pattern.
   *
   * @param ref a ref, a ref pattern or a ref regular expression
   * @return <code>true</code> if the given ref is a ref pattern, otherwise
   *         <code>false</code>
   */
  public static boolean isRefPattern(final String ref) {
    return !isRegExp(ref) && ref.endsWith("/*");
  }

  /**
   * Converts the given ref pattern into a regular expression.
   *
   * @param refPattern a ref pattern
   * @return a regular expression matching the given ref pattern
   */
  private static String refPatternToRegExp(String refPattern) {
    return "^" + escape(refPattern.substring(0, refPattern.length() - 1))
        + ".*";
  }

  /**
   * Escapes s so it can be used to match exactly this string in a regex.
   *
   * @param s string to be escaped
   * @return the escaped string
   */
  private static String escape(final String s) {
    final StringBuilder b = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      final char c = s.charAt(i);
      if (!Character.isLetterOrDigit(c)) {
        b.append("\\");
      }
      b.append(c);
    }
    return b.toString();
  }

  /**
   * Creates an automaton out of the given regular expression.
   *
   * @param regExp a regular expression starting with the "^" character.
   * @return automaton for the given regular expression
   */
  private static Automaton toAutomaton(final String regExp)
      throws InvalidRegExpException {
    final String re = regExp.substring(1);
    try {
      return new RegExp(re, RegExp.NONE).toAutomaton();
    } catch (IllegalArgumentException e) {
      throw new InvalidRegExpException(re, e);
    }
  }

  /**
   * Checks whether the language of a1 is superset of the language of a2
   *
   * @param a1 automaton 1
   * @param a2 automaton 2
   * @return <code>true</code> if the language of a1 is superset of the language
   *         of a2, <code>false</code> otherwise
   */
  private static boolean isSuperSet(final Automaton a1, final Automaton a2) {
    return a1.union(a2).equals(a1);
  }
}
