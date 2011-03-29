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

import com.google.gerrit.common.errors.InvalidRegExpException;

import org.junit.Test;

public class AllowTest {

  @Test
  public void refVsRef() throws InvalidRegExpException, InvalidRangeException {
    assertTrue(new Allow("refs/heads/master", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("refs/heads/master", (short) -1, (short) 2));
    assertFalse(new Allow("refs/heads/master", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("refs/heads/tags", (short) -1, (short) 2));
  }

  @Test
  public void refPatternVsRef() throws InvalidRegExpException, InvalidRangeException {
    assertTrue(new Allow("refs/heads/*", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("refs/heads/master", (short) -1, (short) 2));
    assertFalse(new Allow("refs/heads/*", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("refs/tags/mytag", (short) -1, (short) 2));
    assertFalse(new Allow("refs/heads/*", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("refs/heads", (short) -1, (short) 2));
  }

  @Test
  public void regExVsRef() throws InvalidRegExpException, InvalidRangeException {
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
  public void refVsRefPattern() throws InvalidRegExpException, InvalidRangeException {
    assertFalse(new Allow("refs/heads/master", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("refs/heads/*", (short) -1, (short) 2));
    assertFalse(new Allow("refs/heads/master", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("refs/heads/master/*", (short) -1, (short) 2));
  }

  @Test
  public void refPatternVsRefPattern() throws InvalidRegExpException, InvalidRangeException {
    assertTrue(new Allow("refs/heads/*", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("refs/heads/*", (short) -1, (short) 2));
    assertTrue(new Allow("refs/heads/*", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("refs/heads/master/*", (short) -1, (short) 2));
    assertFalse(new Allow("refs/heads/*", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("refs/*", (short) -1, (short) 2));
  }

  @Test
  public void regExVsRefPattern() throws InvalidRegExpException, InvalidRangeException {
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
  public void refVsRegEx() throws InvalidRegExpException, InvalidRangeException {
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
  public void refPatternVsRegEx() throws InvalidRegExpException, InvalidRangeException {
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
  public void regExVsRegEx() throws InvalidRegExpException, InvalidRangeException {
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
  public void subrange() throws InvalidRegExpException, InvalidRangeException {
    assertTrue(new Allow("x", Short.MIN_VALUE, Short.MAX_VALUE)
        .isAllowed("x", (short) -1, (short) 2));
    assertTrue(new Allow("x", (short) -2, (short) 3)
        .isAllowed("x", (short) -1, (short) 2));
    assertTrue(new Allow("x", (short) -1, (short) 2)
        .isAllowed("x", (short) -1, (short) 2));
  }

  @Test
  public void superrange() throws InvalidRegExpException, InvalidRangeException {
    assertFalse(new Allow("x", (short) -1, (short) 2)
        .isAllowed("x", Short.MIN_VALUE, Short.MAX_VALUE));
    assertFalse(new Allow("x", (short) -1, (short) 2)
        .isAllowed("x", (short) -2, (short) 3));
  }

  @Test
  public void intersection() throws InvalidRegExpException, InvalidRangeException {
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
  public void disjunction() throws InvalidRegExpException, InvalidRangeException {
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
  public void collapsed() throws InvalidRegExpException, InvalidRangeException {
    assertTrue(new Allow("x", (short) 1, (short) 1)
        .isAllowed("x", (short) 1, (short) 1));
    assertFalse(new Allow("x", (short) 1, (short) 1)
        .isAllowed("x", (short) 0, (short) 0));
    assertFalse(new Allow("x", (short) 1, (short) 1)
        .isAllowed("x", (short) 2, (short) 2));
  }

  @Test(expected = InvalidRangeException.class)
  public void invalidRange() throws InvalidRegExpException, InvalidRangeException {
    new Allow("x", (short) 1, (short) 0);
  }

  @Test(expected = InvalidRegExpException.class)
  public void anyVsInvalidRegEx() throws InvalidRegExpException, InvalidRangeException {
    assertTrue(new Allow("x", (short) 1, (short) 1)
        .isAllowed("^refs/heads/[\\:-2,3", (short) 1, (short) 1));
  }
}
