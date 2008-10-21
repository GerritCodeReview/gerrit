// Copyright 2008 Google Inc.
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

package com.google.codereview.manager.unpack;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

public class RecordInputStreamTest extends TestCase {
  public void testEmptyStream() throws IOException {
    final RecordInputStream in = b("");
    assertEquals(-1, in.read());
    assertEquals(-1, in.read());
    assertEquals(-1, in.read(new byte[8], 0, 8));
    assertNull(in.readRecord('\n'));
    in.close();
  }

  public void testReadOneByte() throws IOException {
    final String exp = "abc";
    final RecordInputStream in = b(exp);
    assertEquals(exp.charAt(0), in.read());
    assertEquals(exp.charAt(1), in.read());
    assertEquals(exp.charAt(2), in.read());
    assertEquals(-1, in.read());
    in.close();
  }

  public void testReadBlockOffset0() throws IOException {
    final String exp = "abc";
    final RecordInputStream in = b(exp);
    final byte[] act = new byte[exp.length()];
    assertEquals(act.length, in.read(act, 0, act.length));
    assertEquals(exp.charAt(0), act[0]);
    assertEquals(exp.charAt(1), act[1]);
    assertEquals(exp.charAt(2), act[2]);
    assertEquals(-1, in.read(act, 0, act.length));
    in.close();
  }

  public void testReadBlockOffset1() throws IOException {
    final String exp = "abc";
    final RecordInputStream in = b(exp);
    assertEquals(exp.charAt(0), in.read());
    final byte[] act = new byte[exp.length() - 1];
    assertEquals(act.length, in.read(act, 0, act.length));
    assertEquals(exp.charAt(1), act[0]);
    assertEquals(exp.charAt(2), act[1]);
    assertEquals(-1, in.read(act, 0, act.length));
    in.close();
  }

  public void testReadRecord_SeparatorOnly() throws IOException {
    final String rec1 = "foo";
    final String rec2 = "bar";
    final char sep = '\0';
    final RecordInputStream in = b(rec1 + sep + rec2);
    assertTrue(Arrays.equals(toBytes(rec1), in.readRecord(sep)));
    assertTrue(Arrays.equals(toBytes(rec2), in.readRecord(sep)));
    assertNull(in.readRecord(sep));
    in.close();
  }

  public void testReadRecord_Terminated() throws IOException {
    final String rec1 = "foo";
    final String rec2 = "bar";
    final char sep = '\0';
    final RecordInputStream in = b(rec1 + sep + rec2 + sep);
    assertTrue(Arrays.equals(toBytes(rec1), in.readRecord(sep)));
    assertTrue(Arrays.equals(toBytes(rec2), in.readRecord(sep)));
    assertNull(in.readRecord(sep));
    in.close();
  }

  public void testReadRecord_EmptyRecords() throws IOException {
    final char sep = '\0';
    final RecordInputStream in = b("" + sep + "" + sep);
    assertTrue(Arrays.equals(new byte[0], in.readRecord(sep)));
    assertTrue(Arrays.equals(new byte[0], in.readRecord(sep)));
    assertNull(in.readRecord(sep));
    in.close();
  }

  public void testReadRecord_PartialRecordInBuffer() throws IOException {
    final int huge = 16 * 1024;
    final StringBuilder temp = new StringBuilder(huge);
    for (int i = 0; i < huge; i++) {
      temp.append('x');
    }
    final char sep = '\n';
    final String ts = temp.toString();
    final RecordInputStream in = b(ts + "1" + sep + ts + "2");
    assertEquals(ts + "1", toString(in.readRecord(sep)));
    assertEquals(ts + "2", toString(in.readRecord(sep)));
    assertNull(in.readRecord(sep));
    in.close();
  }

  private static RecordInputStream b(final String s) {
    return new RecordInputStream(new ByteArrayInputStream(toBytes(s)));
  }

  private static byte[] toBytes(final String s) {
    final String enc = "UTF-8";
    try {
      return s.getBytes(enc);
    } catch (UnsupportedEncodingException uee) {
      throw new RuntimeException("No " + enc + " support?", uee);
    }
  }

  private static String toString(final byte[] b) {
    final String enc = "UTF-8";
    try {
      return new String(b, 0, b.length, enc);
    } catch (UnsupportedEncodingException uee) {
      throw new RuntimeException("No " + enc + " support?", uee);
    }
  }
}
