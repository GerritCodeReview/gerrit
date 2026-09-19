// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.common;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import org.junit.Test;

public class CharsetUtilTest {
  @Test
  public void nullNameFallsBackToUtf8() throws Exception {
    assertThat(CharsetUtil.forNameOrUtf8(null)).isEqualTo(UTF_8);
  }

  @Test
  public void validNameResolves() throws Exception {
    assertThat(CharsetUtil.forNameOrUtf8("ISO-8859-1")).isEqualTo(ISO_8859_1);
  }

  @Test
  public void unsupportedNameThrows() {
    IOException e =
        assertThrows(IOException.class, () -> CharsetUtil.forNameOrUtf8("no-such-charset"));
    assertThat(e).hasMessageThat().contains("no-such-charset");
  }

  @Test
  public void illegalNameThrows() {
    assertThrows(IOException.class, () -> CharsetUtil.forNameOrUtf8("not a charset"));
  }
}
