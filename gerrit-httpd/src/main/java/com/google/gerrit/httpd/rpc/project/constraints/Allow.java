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

package com.google.gerrit.httpd.rpc.project.constraints;

import com.google.gerrit.common.errors.InvalidRegExpException;
import com.google.gerrit.reviewdb.RefRight;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.RegExp;

class Allow {

  private final String allowedRef;
  private final Automaton allowedRefAutomaton;
  private final short allowedMin;
  private final short allowedMax;

  Allow(String allowedRef, short allowedMin, short allowedMax)
      throws InvalidRegExpException, InvalidRangeException {
    this.allowedRef = allowedRef;
    allowedRefAutomaton = toAutomaton(toRegExp(allowedRef));
    if (allowedMax < allowedMin) {
      throw new InvalidRangeException(allowedMin, allowedMax);
    }
    this.allowedMin = allowedMin;
    this.allowedMax = allowedMax;
  }

  boolean isAllowed(String ref, short min, short max)
      throws InvalidRegExpException {
    return rangeAllowed(min, max) && refAllowed(ref);
  }

  private boolean rangeAllowed(short min, short max) {
    return allowedMin <= min && max <= allowedMax;
  }

  private boolean refAllowed(String ref) throws InvalidRegExpException {
    if (isRegExp(ref)) {
      return isSuperSet(allowedRefAutomaton, toAutomaton(ref));
    }

    if (isRegExp(allowedRef)) {
      if (isRefPattern(ref)) {
        return isSuperSet(allowedRefAutomaton,
            toAutomaton(refPatternToRegExp(ref)));
      }
      return allowedRefAutomaton.run(ref);
    }

    if (isRefPattern(allowedRef)) {
      String prefix = allowedRef.substring(0, allowedRef.length() - 1);
      return ref.startsWith(prefix);
    }

    return allowedRef.equals(ref);
  }

  /**
   * Check if ref is a ref pattern.
   *
   * @param ref
   * @return
   */
  private static boolean isRefPattern(String ref) {
    return !isRegExp(ref) && ref.endsWith("/*");
  }

  private static boolean isRegExp(String refPattern) {
    return refPattern.startsWith(RefRight.REGEX_PREFIX);
  }

  private static String toRegExp(String ref) {
    if (isRegExp(ref)) {
      return ref;
    }
    if (isRefPattern(ref)) {
      return refPatternToRegExp(ref);
    }
    return "^" + escape(ref);
  }

  private static String refPatternToRegExp(String refPattern) {
    return "^" + escape(refPattern.substring(0, refPattern.length() - 1))
        + ".*";
  }

  /**
   * Escapes s so it can be used to match exactly this string in a regex.
   *
   * @param s
   * @return
   */
  private static String escape(String s) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (!Character.isLetterOrDigit(c)) {
        b.append("\\");
      }
      b.append(c);
    }
    return b.toString();
  }

  /**
   * Creates an automaton out of the given regExp.
   *
   * @param regExp a regular expression starting with "^" character.
   * @return
   */
  private static Automaton toAutomaton(String regExp)
      throws InvalidRegExpException {
    String re = regExp.substring(1);
    try {
      return new RegExp(re, RegExp.NONE).toAutomaton();
    } catch (IllegalArgumentException e) {
      throw new InvalidRegExpException(re, e);
    }
  }

  /**
   * @return <code>true</code> if the language of a1 is superset of the language
   *         of a2, <code>false</code> otherwise
   */
  private static boolean isSuperSet(Automaton a1, Automaton a2) {
    return a1.union(a2).equals(a1);
  }

  @Override
  public String toString() {
    StringBuilder b = new StringBuilder();
    b.append(allowedRef);
    if (Short.MIN_VALUE < allowedMin || allowedMax < Short.MAX_VALUE) {
      b.append(":");
      if (Short.MIN_VALUE < allowedMin) {
        b.append(allowedMin > 0 ? "+" : "").append(allowedMin);
        if (allowedMax < Short.MAX_VALUE) {
          b.append(",");
        }
      }
      if (allowedMax < Short.MAX_VALUE) {
        b.append(allowedMax > 0 ? "+" : "").append(allowedMax);
      }
    }
    return b.toString();
  }
}
