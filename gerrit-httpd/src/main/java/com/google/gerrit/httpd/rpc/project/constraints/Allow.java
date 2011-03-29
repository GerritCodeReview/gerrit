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

import com.google.gerrit.reviewdb.RefRight;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.RegExp;

import java.util.regex.Pattern;


class Allow {

  private final String allowedRef;
  private final short allowedMin;
  private final short allowedMax;

  Allow(String allowedRef, short allowedMin, short allowedMax) {
    this.allowedRef = allowedRef;
    if (allowedMax < allowedMin) {
      throw new IllegalArgumentException("allowedMax: " + allowedMax
          + " cannot be smaller than allowedMin: " + allowedMin);
    }
    this.allowedMin = allowedMin;
    this.allowedMax = allowedMax;
  }

  boolean isAllowed(String ref, short min, short max) {
    // TODO: factor out the common pieces from here and RefControl.matches
    return rangeAllowed(min, max) && refAllowed(ref);
  }

  private boolean rangeAllowed(short min, short max) {
    return allowedMin <= min && max <= allowedMax;
  }

  private boolean refAllowed(String ref) {
    if (isRE(ref)) {
      return isSuperSet(toRegEx(allowedRef), ref);
    }

    if (isRE(allowedRef)) {
      if (isRefPattern(ref)) {
        return isSuperSet(allowedRef, refPatternToRegEx(ref));
      }
      return Pattern.matches(allowedRef, ref);
    }

    if (isRefPattern(allowedRef)) {
      String prefix = allowedRef.substring(0, allowedRef.length() - 1);
      return ref.startsWith(prefix);
    }

    return allowedRef.equals(ref);
  }
  /**
   * Check if ref is a ref pattern.
   * @param ref
   * @return
   */
  private static boolean isRefPattern(String ref) {
    return !isRE(ref) && ref.endsWith("/*");
  }

  // TODO: this was copied from RefControl class
  private static boolean isRE(String refPattern) {
    return refPattern.startsWith(RefRight.REGEX_PREFIX);
  }

  /**
   * Escapes s so it can be used to match exactly this string in a regex.
   * @param s
   * @return
   */
  private static String escape(String s) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (! Character.isLetterOrDigit(c)) {
        b.append("\\");
      }
      b.append(c);
    }
    return b.toString();
  }

  private static String toRegEx(String ref) {
    if (isRE(ref)) {
      return ref;
    }
    if (isRefPattern(ref)) {
      return refPatternToRegEx(ref);
    }
    return refToRegEx(ref);
  }

  private static String refToRegEx(String ref) {
    return "^" + escape(ref);
  }

  private static String refPatternToRegEx(String refPattern) {
    return "^" + escape(refPattern.substring(0, refPattern.length() - 1)) + ".*";
  }

  /**
   * Checks if the language of re1 is superset of the language of re2
   * @param re1
   * @param re2
   * @return
   */
  private static boolean isSuperSet(String re1, String re2) {
    Automaton a1 = toRegExp(re1).toAutomaton();
    Automaton a2 = toRegExp(re2).toAutomaton();
    return a1.union(a2).equals(a1);
  }

  // TODO: copied from RefControl
  private static RegExp toRegExp(String refPattern) {
    if (isRE(refPattern)) {
      refPattern = refPattern.substring(1);
    }
    return new RegExp(refPattern, RegExp.NONE);
  }

  @Override
  public String toString() {
    StringBuilder b = new StringBuilder();
    b.append(allowedRef);
    if (Short.MIN_VALUE < allowedMin || allowedMax < Short.MAX_VALUE) {
      b.append(":");
      if (Short.MIN_VALUE < allowedMin) {
        b.append(allowedMin);
        if (allowedMax < Short.MAX_VALUE) {
          b.append(",");
        }
      }
      if (allowedMax < Short.MAX_VALUE) {
        b.append(allowedMax);
      }
    }
    return b.toString();
  }
}
