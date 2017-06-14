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
//
// This code is based heavily on Robert Harder's <rob@iharder.net>
// public domain Base64 class, version 2.1.
//

package com.google.gerrit.httpd;

/** Base64 encoder which uses a language safe within HTTP cookies. */
class CookieBase64 {
  private static final char[] enc;

  static {
    enc = new char[64];
    int o = 0;
    o = fill(enc, o, 'a', 'z');
    o = fill(enc, o, 'A', 'Z');
    o = fill(enc, o, '0', '9');
    enc[o++] = '-';
    enc[o] = '.';
  }

  private static int fill(char[] out, int o, char f, int l) {
    for (char c = f; c <= l; c++) {
      out[o++] = c;
    }
    return o;
  }

  static String encode(byte[] in) {
    final StringBuilder out = new StringBuilder(in.length * 4 / 3);
    final int len2 = in.length - 2;
    int d = 0;
    for (; d < len2; d += 3) {
      encode3to4(out, in, d, 3);
    }
    if (d < in.length) {
      encode3to4(out, in, d, in.length - d);
    }
    return out.toString();
  }

  private static void encode3to4(StringBuilder out, byte[] in, int inOffset, int numSigBytes) {
    //           1         2         3
    // 01234567890123456789012345678901 Bit position
    // --------000000001111111122222222 Array position from threeBytes
    // --------|    ||    ||    ||    | Six bit groups to index ALPHABET
    //          >>18  >>12  >> 6  >> 0  Right shift necessary
    //                0x3f  0x3f  0x3f  Additional AND

    // Create buffer with zero-padding if there are only one or two
    // significant bytes passed in the array.
    // We have to shift left 24 in order to flush out the 1's that appear
    // when Java treats a value as negative that is cast from a byte to an int.
    //
    int inBuff =
        (numSigBytes > 0 ? ((in[inOffset] << 24) >>> 8) : 0)
            | (numSigBytes > 1 ? ((in[inOffset + 1] << 24) >>> 16) : 0)
            | (numSigBytes > 2 ? ((in[inOffset + 2] << 24) >>> 24) : 0);

    switch (numSigBytes) {
      case 3:
        out.append(enc[(inBuff >>> 18)]);
        out.append(enc[(inBuff >>> 12) & 0x3f]);
        out.append(enc[(inBuff >>> 6) & 0x3f]);
        out.append(enc[(inBuff) & 0x3f]);
        break;

      case 2:
        out.append(enc[(inBuff >>> 18)]);
        out.append(enc[(inBuff >>> 12) & 0x3f]);
        out.append(enc[(inBuff >>> 6) & 0x3f]);
        break;

      case 1:
        out.append(enc[(inBuff >>> 18)]);
        out.append(enc[(inBuff >>> 12) & 0x3f]);
        break;

      default:
        break;
    }
  }

  private CookieBase64() {}
}
