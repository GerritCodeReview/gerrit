// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.entities;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Arrays;

public class KeyUtil {
  private static final char[] hexc = {
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
  };
  private static final char safe[];
  private static final byte hexb[];

  static {
    safe = new char[256];
    safe['-'] = '-';
    safe['_'] = '_';
    safe['.'] = '.';
    safe['!'] = '!';
    safe['~'] = '~';
    safe['*'] = '*';
    safe['\''] = '\'';
    safe['('] = '(';
    safe[')'] = ')';
    safe['/'] = '/';
    safe[' '] = '+';
    for (char c = '0'; c <= '9'; c++) safe[c] = c;
    for (char c = 'A'; c <= 'Z'; c++) safe[c] = c;
    for (char c = 'a'; c <= 'z'; c++) safe[c] = c;

    hexb = new byte['f' + 1];
    Arrays.fill(hexb, (byte) -1);
    for (char i = '0'; i <= '9'; i++) hexb[i] = (byte) (i - '0');
    for (char i = 'A'; i <= 'F'; i++) hexb[i] = (byte) ((i - 'A') + 10);
    for (char i = 'a'; i <= 'f'; i++) hexb[i] = (byte) ((i - 'a') + 10);
  }

  public static String encode(final String key) {
    final byte[] b = key.getBytes(UTF_8);
    final StringBuilder r = new StringBuilder(b.length);
    for (int i = 0; i < b.length; i++) {
      final int c = b[i] & 0xff;
      final char s = safe[c];
      if (s == 0) {
        r.append('%');
        r.append(hexc[c >> 4]);
        r.append(hexc[c & 15]);
      } else {
        r.append(s);
      }
    }
    return r.toString();
  }

  public static String decode(String key) {
    // URLs use percentage encoding which replaces unsafe ASCII characters with a '%' followed by
    // two hexadecimal digits. If there is '%' that is not followed by two hexadecimal digits
    // the code below fails with an IllegalArgumentException. To prevent this replace any '%'
    // that is not followed by two hexadecimal digits by "%25", which is the URL encoding for '%'.
    key = key.replaceAll("%(?![0-9a-fA-F]{2})", "%25");

    if (key.indexOf('%') < 0) {
      return key.replace('+', ' ');
    }

    final byte[] b = new byte[key.length()];
    int bPtr = 0;
    try {
      for (int i = 0; i < key.length(); ) {
        final char c = key.charAt(i);
        if (c == '%' && i + 2 < key.length()) {
          final int v = (hexb[key.charAt(i + 1)] << 4) | hexb[key.charAt(i + 2)];
          if (v < 0) {
            throw new IllegalArgumentException(key.substring(i, i + 3));
          }
          b[bPtr++] = (byte) v;
          i += 3;
        } else if (c == '+') {
          b[bPtr++] = ' ';
          i++;
        } else {
          b[bPtr++] = (byte) c;
          i++;
        }
      }
    } catch (ArrayIndexOutOfBoundsException err) {
      throw new IllegalArgumentException("Bad encoding" + key, err);
    }
    return new String(b, 0, bPtr, UTF_8);
  }
}
