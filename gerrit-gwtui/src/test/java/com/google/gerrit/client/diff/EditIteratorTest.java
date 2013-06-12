package com.google.gerrit.client.diff;

import static org.junit.Assert.*;

import com.google.gerrit.client.diff.CodeMirrorDemo.EditIterator;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArrayString;

import com.googlecode.gwt.test.GwtModule;
import com.googlecode.gwt.test.GwtTest;

import net.codemirror.lib.LineCharacter;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for EditIterator
 */
@GwtModule("com.google.gerrit.GerritGwtUI")
public class EditIteratorTest extends GwtTest {
  private JsArrayString lines;

  private void assertLineChsEqual(LineCharacter a, LineCharacter b) {
    assertEquals(a.getLine() + "," + a.getCh(), b.getLine() + "," + b.getCh());
  }

  @Before
  public void initialize() {
    lines = (JsArrayString) JavaScriptObject.createArray();
    lines.push("1st");
    lines.push("2nd");
    lines.push("3rd");
  }

  @Test
  public void testNoAdvance() {
    EditIterator iter = new EditIterator(lines, 0);
    assertLineChsEqual(LineCharacter.create(0, 0), iter.advance(0));
  }

  @Test
  public void testSimpleAdvance() {
    EditIterator iter = new EditIterator(lines, 0);
    assertLineChsEqual(LineCharacter.create(0, 1), iter.advance(1));
  }

  @Test
  public void testEndsBeforeNewline() {
    EditIterator iter = new EditIterator(lines, 0);
    assertLineChsEqual(LineCharacter.create(0, 3), iter.advance(3));
  }

  @Test
  public void testEndsOnNewline() {
    EditIterator iter = new EditIterator(lines, 0);
    assertLineChsEqual(LineCharacter.create(1, 0), iter.advance(4));
  }

  @Test
  public void testAcrossNewline() {
    EditIterator iter = new EditIterator(lines, 0);
    assertLineChsEqual(LineCharacter.create(1, 1), iter.advance(5));
  }

  @Test
  public void testContinueFromBeforeNewline() {
    EditIterator iter = new EditIterator(lines, 0);
    iter.advance(3);
    assertLineChsEqual(LineCharacter.create(2, 2), iter.advance(7));
  }

  @Test
  public void testContinueFromAfterNewline() {
    EditIterator iter = new EditIterator(lines, 0);
    iter.advance(4);
    assertLineChsEqual(LineCharacter.create(2, 2), iter.advance(6));
  }

  @Test
  public void testAcrossMultipleLines() {
    EditIterator iter = new EditIterator(lines, 0);
    assertLineChsEqual(LineCharacter.create(2, 2), iter.advance(10));
  }
}
