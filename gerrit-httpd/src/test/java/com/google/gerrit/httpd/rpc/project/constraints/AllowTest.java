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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AllowTest {

  @Test
  public void refVsRef() {
    assertTrue(new Allow("refs/heads/master", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("refs/heads/master", (short) -1, (short) 2));
    assertFalse(new Allow("refs/heads/master", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("refs/heads/tags", (short) -1, (short) 2));
  }

  @Test
  public void refPatternVsRef() {
    assertTrue(new Allow("refs/heads/*", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("refs/heads/master", (short) -1, (short) 2));
    assertFalse(new Allow("refs/heads/*", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("refs/tags/mytag", (short) -1, (short) 2));
    assertFalse(new Allow("refs/heads/*", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("refs/heads", (short) -1, (short) 2));
  }

  @Test
  public void regExVsRef() {
    assertTrue(new Allow("^refs/heads/.*", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("refs/heads/master", (short) -1, (short) 2));
    assertFalse(new Allow("^refs/heads/.*", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("refs/tags/mytag", (short) -1, (short) 2));
    assertFalse(new Allow("^refs/heads/.*", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("refs/heads", (short) -1, (short) 2));
    assertFalse(new Allow("^refs/heads/*", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("refs/heads/master", (short) -1, (short) 2));
  }

  @Test
  public void refVsRefPattern() {
    assertFalse(new Allow("refs/heads/master", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("refs/heads/*", (short) -1, (short) 2));
    assertFalse(new Allow("refs/heads/master", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("refs/heads/master/*", (short) -1, (short) 2));
  }

  @Test
  public void refPatternVsRefPattern() {
    assertTrue(new Allow("refs/heads/*", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("refs/heads/*", (short) -1, (short) 2));
    assertTrue(new Allow("refs/heads/*", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("refs/heads/master/*", (short) -1, (short) 2));
    assertFalse(new Allow("refs/heads/*", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("refs/*", (short) -1, (short) 2));
  }

  @Test
  public void regExVsRefPattern() {
    assertTrue(new Allow("^refs/heads/.*", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("refs/heads/*", (short) -1, (short) 2));
    assertTrue(new Allow("^refs/heads/.*", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("refs/heads/master/*", (short) -1, (short) 2));
    assertTrue(new Allow("^refs/[hHeEaAdDsS]*/.*", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("refs/heads/master/*", (short) -1, (short) 2));
    assertFalse(new Allow("^refs/heads/[mM].*", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("refs/heads/*", (short) -1, (short) 2));
    assertFalse(new Allow("^refs/heads/.*", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("refs/*", (short) -1, (short) 2));
    assertFalse(new Allow("^refs/heads/\\*", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("refs/heads/*", (short) -1, (short) 2));
  }

  @Test
  public void refVsRegEx() {
    assertFalse(new Allow("refs/heads/master", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("^refs/heads/.*", (short) -1, (short) 2));
    assertTrue(new Allow("refs/heads/master", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("^refs/heads/master", (short) -1, (short) 2));
    assertFalse(new Allow("refs/heads/master", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("^refs/heads/master/.*", (short) -1, (short) 2));
    assertTrue(new Allow("refs/heads/master", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("^refs/heads/[m]aster", (short) -1, (short) 2));
    assertFalse(new Allow("refs/heads/.*", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("^refs/heads/.*", (short) -1, (short) 2));
  }

  @Test
  public void refPatternVsRegEx() {
    assertTrue(new Allow("refs/heads/*", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("^refs/heads/.*", (short) -1, (short) 2));
    assertFalse(new Allow("refs/.heads/*", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("^refs/xheads/.*", (short) -1, (short) 2));
    assertTrue(new Allow("refs/heads/*", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("^refs/heads/master/x.*", (short) -1, (short) 2));
    assertFalse(new Allow("refs/heads/master/*", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("^refs/heads/.*", (short) -1, (short) 2));
  }

  @Test
  public void regExVsRegEx() {
    assertFalse(new Allow("^refs/heads/master", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("^refs/heads/.*", (short) -1, (short) 2));
    assertTrue(new Allow("^refs/heads/master", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("^refs/heads/master", (short) -1, (short) 2));
    assertFalse(new Allow("^refs/heads/master", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("^refs/heads/master/.*", (short) -1, (short) 2));
    assertTrue(new Allow("^refs/heads/master", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("^refs/heads/[m]aster", (short) -1, (short) 2));
    assertFalse(new Allow("^refs/heads/\\.\\*", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("^refs/heads/.*", (short) -1, (short) 2));
    assertTrue(new Allow("^refs/.*/master", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("^refs/heads.*/master", (short) -1, (short) 2));
  }

  @Test
  public void subrange() {
    assertTrue(new Allow("x", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("x", (short) -1, (short) 2));
    assertTrue(new Allow("x", (short) -2, (short) 3)
        .isAllowed("x", (short) -1, (short) 2));
    assertTrue(new Allow("x", (short) -1, (short) 2)
        .isAllowed("x", (short) -1, (short) 2));
  }

  @Test
  public void superrange() {
    assertFalse(new Allow("x", (short) -1, (short) 2)
        .isAllowed("x", Short.MIN_VALUE, Short.MAX_VALUE));
    assertFalse(new Allow("x", (short) -1, (short) 2)
        .isAllowed("x", (short) -2, (short) 3));
  }

  @Test
  public void intersection() {
    // left
    assertFalse(new Allow("x", (short) -1, (short) 2)
        .isAllowed("x", Short.MIN_VALUE, (short) 0));
    assertFalse(new Allow("x", (short) -1, (short) 2)
        .isAllowed("x", (short) -2, (short) 0));
    assertTrue(new Allow("x", (short) -1, (short) 2)
        .isAllowed("x", (short) -1, (short) 0));

    // right
    assertFalse(new Allow("x", (short) -1, (short) 2)
        .isAllowed("x", (short) 0, Short.MAX_VALUE));
    assertFalse(new Allow("x", (short) -1, (short) 2)
        .isAllowed("x", (short) 0, (short) 3));
    assertTrue(new Allow("x", (short) -1, (short) 2)
        .isAllowed("x", (short) 0, (short) 2));
  }

  @Test
  public void disjunction() {
    // left
    assertFalse(new Allow("x", (short) -1, (short) 2)
        .isAllowed("x", Short.MIN_VALUE, (short) -2));
    assertFalse(new Allow("x", (short) -1, (short) 2)
        .isAllowed("x", (short) -5, (short) -2));
    assertFalse(new Allow("x", (short) -1, (short) 2)
        .isAllowed("x", (short) -5, (short) -1));

    //right
    assertFalse(new Allow("x", (short) -1, (short) 2)
        .isAllowed("x", (short) 3, Short.MAX_VALUE));
    assertFalse(new Allow("x", (short) -1, (short) 2)
        .isAllowed("x", (short) 3, (short) 5));
    assertFalse(new Allow("x", (short) -1, (short) 2)
        .isAllowed("x", (short) 2, (short) 5));
  }

  @Test
  public void collapsed() {
    assertTrue(new Allow("x", (short) 1, (short) 1)
        .isAllowed("x", (short) 1, (short) 1));
    assertFalse(new Allow("x", (short) 1, (short) 1)
        .isAllowed("x", (short) 0, (short) 0));
    assertFalse(new Allow("x", (short) 1, (short) 1)
        .isAllowed("x", (short) 2, (short) 2));
  }

  @Test(expected = IllegalArgumentException.class)
  public void invalidRange() {
    new Allow("x", (short) 1, (short) 0);
  }
}
