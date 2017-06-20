// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.mail.send;

import static com.google.common.truth.Truth.assertThat;

import java.util.Collections;
import org.junit.Test;

public class QuotedPrintableEncodingTest {
  @Test
  public void encodeSpecialCharacters() {
    String encoded = QuotedPrintableEncoding.encode("abc=123 def");
    assertThat(encoded).isEqualTo("abc=3D123 def");
  }

  @Test
  public void encodeNonAscii() {
    String encoded = QuotedPrintableEncoding.encode("suivre les voilà bientôt qui");
    assertThat(encoded).isEqualTo("suivre les voil=C3=A0 bient=C3=B4t qui");
  }

  @Test
  public void softBreaks() {
    // Too many unencoded characterts to fit one line.
    String length75 = repeat("x", 75);
    String encoded = QuotedPrintableEncoding.encode(length75 + length75);
    assertThat(encoded).isEqualTo(length75 + "=\n" + length75);

    // Too many characters to fit on one line after encoding.
    String length25 = repeat("=", 25);
    String encodedLength25 = repeat("=3D", 25);
    encoded = QuotedPrintableEncoding.encode(length25 + length25);
    assertThat(encoded).isEqualTo(encodedLength25 + "=\n" + encodedLength25);

    // Encoded character appears on line boundary.
    String length74 = repeat("x", 73);
    encoded = QuotedPrintableEncoding.encode(length74 + "=");
    assertThat(encoded).isEqualTo(length74 + "=\n=3D");

    encoded = QuotedPrintableEncoding.encode(length74 + "x=");
    assertThat(encoded).isEqualTo(length74 + "x=\n=3D");
  }

  @Test
  public void encodesEmoji() {
    // Smiling Face With Smiling Eyes U+1F60A "- 😊 -"
    String encoded = QuotedPrintableEncoding.encode("- 😊 -");
    assertThat(encoded).isEqualTo("- =F0=9F=98=8A -");
  }

  private String repeat(String phrase, int times) {
    return String.join("", Collections.nCopies(times, phrase));
  }
}
