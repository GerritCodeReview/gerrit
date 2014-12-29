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

package com.google.gerrit.client.diff;

import static org.junit.Assert.assertEquals;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArrayString;

import com.googlecode.gwt.test.GwtModule;
import com.googlecode.gwt.test.GwtTest;

import net.codemirror.lib.Pos;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/** Unit tests for EditIterator */
@GwtModule("com.google.gerrit.GerritGwtUI")
@Ignore
// TODO(davido): Enable this again when gwt-test-utils lib is fixed.
public class EditIteratorTest extends GwtTest {
  private JsArrayString lines;

  private void assertLineChsEqual(Pos a, Pos b) {
    assertEquals(a.line() + "," + a.ch(), b.line() + "," + b.ch());
  }

  @Before
  public void initialize() {
    lines = (JsArrayString) JavaScriptObject.createArray();
    lines.push("1st");
    lines.push("2nd");
    lines.push("3rd");
  }

  @Test
  public void testNegativeAdvance() {
    EditIterator i = new EditIterator(lines, 0);
    assertLineChsEqual(Pos.create(1, 1), i.advance(5));
    assertLineChsEqual(Pos.create(0, 3), i.advance(-2));
  }

  @Test
  public void testNoAdvance() {
    EditIterator iter = new EditIterator(lines, 0);
    assertLineChsEqual(Pos.create(0), iter.advance(0));
  }

  @Test
  public void testSimpleAdvance() {
    EditIterator iter = new EditIterator(lines, 0);
    assertLineChsEqual(Pos.create(0, 1), iter.advance(1));
  }

  @Test
  public void testEndsBeforeNewline() {
    EditIterator iter = new EditIterator(lines, 0);
    assertLineChsEqual(Pos.create(0, 3), iter.advance(3));
  }

  @Test
  public void testEndsOnNewline() {
    EditIterator iter = new EditIterator(lines, 0);
    assertLineChsEqual(Pos.create(1), iter.advance(4));
  }

  @Test
  public void testAcrossNewline() {
    EditIterator iter = new EditIterator(lines, 0);
    assertLineChsEqual(Pos.create(1, 1), iter.advance(5));
  }

  @Test
  public void testContinueFromBeforeNewline() {
    EditIterator iter = new EditIterator(lines, 0);
    iter.advance(3);
    assertLineChsEqual(Pos.create(2, 2), iter.advance(7));
  }

  @Test
  public void testContinueFromAfterNewline() {
    EditIterator iter = new EditIterator(lines, 0);
    iter.advance(4);
    assertLineChsEqual(Pos.create(2, 2), iter.advance(6));
  }

  @Test
  public void testAcrossMultipleLines() {
    EditIterator iter = new EditIterator(lines, 0);
    assertLineChsEqual(Pos.create(2, 2), iter.advance(10));
  }
}
