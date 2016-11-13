// Copyright 2008 Google Inc. All rights reserved.
// http://code.google.com/p/protobuf/
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//
// * Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
// * Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the following disclaimer
// in the documentation and/or other materials provided with the
// distribution.
// * Neither the name of Google Inc. nor the names of its
// contributors may be used to endorse or promote products derived from
// this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
// A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
// OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
// LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
// DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
// THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package com.google.gerrit.server.ioutil;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.reviewdb.client.CodedEnum;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.eclipse.jgit.util.IO;

public class BasicSerialization {
  private static final byte[] NO_BYTES = {};

  private static int safeRead(final InputStream input) throws IOException {
    final int b = input.read();
    if (b == -1) {
      throw new EOFException();
    }
    return b;
  }

  /** Read a fixed-width 64 bit integer in network byte order (big-endian). */
  public static long readFixInt64(final InputStream input) throws IOException {
    final long h = readFixInt32(input);
    final long l = readFixInt32(input) & 0xFFFFFFFFL;
    return (h << 32) | l;
  }

  /** Write a fixed-width 64 bit integer in network byte order (big-endian). */
  public static void writeFixInt64(final OutputStream output, final long val) throws IOException {
    writeFixInt32(output, (int) (val >>> 32));
    writeFixInt32(output, (int) (val & 0xFFFFFFFFL));
  }

  /** Read a fixed-width 32 bit integer in network byte order (big-endian). */
  public static int readFixInt32(final InputStream input) throws IOException {
    final int b1 = safeRead(input);
    final int b2 = safeRead(input);
    final int b3 = safeRead(input);
    final int b4 = safeRead(input);
    return (b1 << 24) | (b2 << 16) | (b3 << 8) | b4;
  }

  /** Write a fixed-width 32 bit integer in network byte order (big-endian). */
  public static void writeFixInt32(final OutputStream output, final int val) throws IOException {
    output.write((val >>> 24) & 0xFF);
    output.write((val >>> 16) & 0xFF);
    output.write((val >>> 8) & 0xFF);
    output.write(val & 0xFF);
  }

  /** Read a varint from the input, one byte at a time. */
  public static int readVarInt32(final InputStream input) throws IOException {
    int result = 0;
    int offset = 0;
    for (; offset < 32; offset += 7) {
      final int b = safeRead(input);
      result |= (b & 0x7f) << offset;
      if ((b & 0x80) == 0) {
        return result;
      }
    }
    throw new EOFException();
  }

  /** Write a varint; value is treated as an unsigned value. */
  public static void writeVarInt32(final OutputStream output, int value) throws IOException {
    while (true) {
      if ((value & ~0x7F) == 0) {
        output.write(value);
        return;
      }
      output.write((value & 0x7F) | 0x80);
      value >>>= 7;
    }
  }

  /** Read a fixed length byte array whose length is specified as a varint. */
  public static byte[] readBytes(final InputStream input) throws IOException {
    final int len = readVarInt32(input);
    if (len == 0) {
      return NO_BYTES;
    }
    final byte[] buf = new byte[len];
    IO.readFully(input, buf, 0, len);
    return buf;
  }

  /** Write a byte array prefixed by its length in a varint. */
  public static void writeBytes(final OutputStream output, final byte[] data) throws IOException {
    writeBytes(output, data, 0, data.length);
  }

  /** Write a byte array prefixed by its length in a varint. */
  public static void writeBytes(
      final OutputStream output, final byte[] data, final int offset, final int len)
      throws IOException {
    writeVarInt32(output, len);
    output.write(data, offset, len);
  }

  /** Read a UTF-8 string, prefixed by its byte length in a varint. */
  public static String readString(final InputStream input) throws IOException {
    final byte[] bin = readBytes(input);
    if (bin.length == 0) {
      return null;
    }
    return new String(bin, 0, bin.length, UTF_8);
  }

  /** Write a UTF-8 string, prefixed by its byte length in a varint. */
  public static void writeString(final OutputStream output, final String s) throws IOException {
    if (s == null) {
      writeVarInt32(output, 0);
    } else {
      writeBytes(output, s.getBytes(UTF_8));
    }
  }

  /** Read an enum whose code is stored as a varint. */
  public static <T extends CodedEnum> T readEnum(final InputStream input, final T[] all)
      throws IOException {
    final int val = readVarInt32(input);
    for (T t : all) {
      if (t.getCode() == val) {
        return t;
      }
    }
    throw new IOException("Invalid enum " + val + " for " + all[0].getClass());
  }

  /** Write an enum whose code is stored as a varint. */
  public static <T extends CodedEnum> void writeEnum(final OutputStream output, final T e)
      throws IOException {
    writeVarInt32(output, e.getCode());
  }

  private BasicSerialization() {}
}
