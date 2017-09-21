// Copyright (C) 2014 The Android Open Source Project
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

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MostSpecificComparatorTest {

  private MostSpecificComparator cmp;

  @Test
  public void shorterDistanceWins() {
    cmp = new MostSpecificComparator("refs/heads/master");
    moreSpecificFirst("refs/heads/master", "refs/heads/master2");
    moreSpecificFirst("refs/heads/master", "refs/heads/maste");
    moreSpecificFirst("refs/heads/master", "refs/heads/*");
    moreSpecificFirst("refs/heads/master", "^refs/heads/.*");
    moreSpecificFirst("refs/heads/master", "^refs/heads/master.*");
  }

  /**
   * Assuming two patterns have the same Levenshtein distance, the pattern which represents a finite
   * language wins over a pattern which represents an infinite language.
   */
  @Test
  public void finiteWinsOverInfinite() {
    cmp = new MostSpecificComparator("refs/heads/master");
    moreSpecificFirst("^refs/heads/......", "refs/heads/*");
    moreSpecificFirst("^refs/heads/maste.", "^refs/heads/maste.*");
  }

  /**
   * Assuming two patterns have the same Levenshtein distance and are both either finite or infinite
   * the one with the higher number of state transitions (in an equivalent automaton) wins
   */
  @Test
  public void higherNumberOfTransitionsWins() {
    cmp = new MostSpecificComparator("refs/heads/x");
    moreSpecificFirst("^refs/heads/[a-z].*", "refs/heads/*");
    // Previously there was a bug where having a '1' in a refname would cause a
    // glob pattern's Levenshtein distance to decrease by 1.  These two
    // patterns should be a Levenshtein distance of 12 from the both of the
    // refnames, where previously the 'branch1' refname would be a distance of
    // 11 from 'refs/heads/abc/*'
    cmp = new MostSpecificComparator("refs/heads/abc/spam/branch2");
    moreSpecificFirst("^refs/heads/.*spam.*", "refs/heads/abc/*");
    cmp = new MostSpecificComparator("refs/heads/abc/spam/branch1");
    moreSpecificFirst("^refs/heads/.*spam.*", "refs/heads/abc/*");
  }

  /**
   * Assuming the same Levenshtein distance, (in)finity and the number of transitions, the longer
   * pattern wins
   */
  @Test
  public void longerPatternWins() {
    cmp = new MostSpecificComparator("refs/heads/x");
    moreSpecificFirst("^refs/heads/[a-z].*", "^refs/heads/..*");
  }

  private void moreSpecificFirst(String first, String second) {
    assertTrue(cmp.compare(first, second) < 0);
  }
}
