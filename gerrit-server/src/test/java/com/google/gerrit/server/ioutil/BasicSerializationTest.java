// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.ioutil;

import static com.google.gerrit.server.ioutil.BasicSerialization.readFixInt64;
import static com.google.gerrit.server.ioutil.BasicSerialization.readString;
import static com.google.gerrit.server.ioutil.BasicSerialization.readVarInt32;
import static com.google.gerrit.server.ioutil.BasicSerialization.writeFixInt64;
import static com.google.gerrit.server.ioutil.BasicSerialization.writeString;
import static com.google.gerrit.server.ioutil.BasicSerialization.writeVarInt32;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;

public class BasicSerializationTest {
  @Test
  public void testReadVarInt32() throws IOException {
    assertEquals(0x00000000, readVarInt32(r(b(0))));
    assertEquals(0x00000003, readVarInt32(r(b(3))));
    assertEquals(0x000000ff, readVarInt32(r(b(0x80 | 0x7f, 0x01))));
  }

  @Test
  public void testWriteVarInt32() throws IOException {
    ByteArrayOutputStream out;

    out = new ByteArrayOutputStream();
    writeVarInt32(out, 0);
    assertOutput(b(0), out);

    out = new ByteArrayOutputStream();
    writeVarInt32(out, 3);
    assertOutput(b(3), out);

    out = new ByteArrayOutputStream();
    writeVarInt32(out, 0xff);
    assertOutput(b(0x80 | 0x7f, 0x01), out);
  }

  @Test
  public void testReadFixInt64() throws IOException {
    assertEquals(0L, readFixInt64(r(b(0, 0, 0, 0, 0, 0, 0, 0))));
    assertEquals(3L, readFixInt64(r(b(0, 0, 0, 0, 0, 0, 0, 3))));

    assertEquals(0xdeadbeefL, readFixInt64(r(b(0, 0, 0, 0, 0xde, 0xad, 0xbe, 0xef))));

    assertEquals(0x0310adefL, readFixInt64(r(b(0, 0, 0, 0, 0x03, 0x10, 0xad, 0xef))));

    assertEquals(
        0xc0ffee78deadbeefL, readFixInt64(r(b(0xc0, 0xff, 0xee, 0x78, 0xde, 0xad, 0xbe, 0xef))));

    assertEquals(0x00000000ffffffffL, readFixInt64(r(b(0, 0, 0, 0, 0xff, 0xff, 0xff, 0xff))));

    assertEquals(
        0xffffffffffffffffL, readFixInt64(r(b(0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff))));
  }

  @Test
  public void testWriteFixInt64() throws IOException {
    ByteArrayOutputStream out;

    out = new ByteArrayOutputStream(8);
    writeFixInt64(out, 0L);
    assertOutput(b(0, 0, 0, 0, 0, 0, 0, 0), out);

    out = new ByteArrayOutputStream(8);
    writeFixInt64(out, 3L);
    assertOutput(b(0, 0, 0, 0, 0, 0, 0, 3), out);

    out = new ByteArrayOutputStream(8);
    writeFixInt64(out, 0xdeacL);
    assertOutput(b(0, 0, 0, 0, 0, 0, 0xde, 0xac), out);

    out = new ByteArrayOutputStream(8);
    writeFixInt64(out, 0xdeac9853L);
    assertOutput(b(0, 0, 0, 0, 0xde, 0xac, 0x98, 0x53), out);

    out = new ByteArrayOutputStream(8);
    writeFixInt64(out, 0xac431242deac9853L);
    assertOutput(b(0xac, 0x43, 0x12, 0x42, 0xde, 0xac, 0x98, 0x53), out);

    out = new ByteArrayOutputStream(8);
    writeFixInt64(out, -1L);
    assertOutput(b(0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff), out);
  }

  @Test
  public void testReadString() throws IOException {
    assertNull(readString(r(b(0))));
    assertEquals("a", readString(r(b(1, 'a'))));
    assertEquals("coffee4", readString(r(b(7, 'c', 'o', 'f', 'f', 'e', 'e', '4'))));
  }

  @Test
  public void testWriteString() throws IOException {
    ByteArrayOutputStream out;

    out = new ByteArrayOutputStream();
    writeString(out, null);
    assertOutput(b(0), out);

    out = new ByteArrayOutputStream();
    writeString(out, "");
    assertOutput(b(0), out);

    out = new ByteArrayOutputStream();
    writeString(out, "a");
    assertOutput(b(1, 'a'), out);

    out = new ByteArrayOutputStream();
    writeString(out, "coffee4");
    assertOutput(b(7, 'c', 'o', 'f', 'f', 'e', 'e', '4'), out);
  }

  private static void assertOutput(final byte[] expect, final ByteArrayOutputStream out) {
    final byte[] buf = out.toByteArray();
    for (int i = 0; i < expect.length; i++) {
      assertEquals(expect[i], buf[i]);
    }
  }

  private static InputStream r(final byte[] buf) {
    return new ByteArrayInputStream(buf);
  }

  private static byte[] b(int a) {
    return new byte[] {(byte) a};
  }

  private static byte[] b(int a, int b) {
    return new byte[] {(byte) a, (byte) b};
  }

  private static byte[] b(int a, int b, int c, int d, int e, int f, int g, int h) {
    return new byte[] {
      (byte) a, (byte) b, (byte) c, (byte) d, //
      (byte) e, (byte) f, (byte) g, (byte) h,
    };
  }
}
