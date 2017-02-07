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

package com.google.gerrit.server.util;

import com.google.gerrit.common.data.RefConfigSection;
import com.google.gerrit.server.project.RefPattern;
import java.util.Comparator;
import org.apache.commons.lang.StringUtils;

/**
 * Order the Ref Pattern by the most specific. This sort is done by:
 *
 * <ul>
 *   <li>1 - The minor value of Levenshtein string distance between the branch name and the regex
 *       string shortest example. A shorter distance is a more specific match.
 *   <li>2 - Finites first, infinities after.
 *   <li>3 - Number of transitions. More transitions is more specific.
 *   <li>4 - Length of the expression text.
 * </ul>
 *
 * Levenshtein distance is a measure of the similarity between two strings. The distance is the
 * number of deletions, insertions, or substitutions required to transform one string into another.
 *
 * <p>For example, if given refs/heads/m* and refs/heads/*, the distances are 5 and 6. It means that
 * refs/heads/m* is more specific because it's closer to refs/heads/master than refs/heads/*.
 *
 * <p>Another example could be refs/heads/* and refs/heads/[a-zA-Z]*, the distances are both 6. Both
 * are infinite, but refs/heads/[a-zA-Z]* has more transitions, which after all turns it more
 * specific.
 */
public final class MostSpecificComparator implements Comparator<RefConfigSection> {
  private final String refName;

  public MostSpecificComparator(String refName) {
    this.refName = refName;
  }

  @Override
  public int compare(RefConfigSection a, RefConfigSection b) {
    return compare(a.getName(), b.getName());
  }

  public int compare(final String pattern1, final String pattern2) {
    int cmp = distance(pattern1) - distance(pattern2);
    if (cmp == 0) {
      boolean p1_finite = finite(pattern1);
      boolean p2_finite = finite(pattern2);

      if (p1_finite && !p2_finite) {
        cmp = -1;
      } else if (!p1_finite && p2_finite) {
        cmp = 1;
      } else /* if (f1 == f2) */ {
        cmp = 0;
      }
    }
    if (cmp == 0) {
      cmp = transitions(pattern2) - transitions(pattern1);
    }
    if (cmp == 0) {
      cmp = pattern2.length() - pattern1.length();
    }
    return cmp;
  }

  private int distance(String pattern) {
    String example;
    if (RefPattern.isRE(pattern)) {
      example = RefPattern.shortestExample(pattern);

    } else if (pattern.endsWith("/*")) {
      example = pattern;

    } else if (pattern.equals(refName)) {
      return 0;

    } else {
      return Math.max(pattern.length(), refName.length());
    }
    return StringUtils.getLevenshteinDistance(example, refName);
  }

  private boolean finite(String pattern) {
    if (RefPattern.isRE(pattern)) {
      return RefPattern.toRegExp(pattern).toAutomaton().isFinite();

    } else if (pattern.endsWith("/*")) {
      return false;

    } else {
      return true;
    }
  }

  private int transitions(String pattern) {
    if (RefPattern.isRE(pattern)) {
      return RefPattern.toRegExp(pattern).toAutomaton().getNumberOfTransitions();

    } else if (pattern.endsWith("/*")) {
      return pattern.length();

    } else {
      return pattern.length();
    }
  }
}
