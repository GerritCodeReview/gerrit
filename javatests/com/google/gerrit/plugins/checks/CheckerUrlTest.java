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

package com.google.gerrit.plugins.checks;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;

import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.testing.GerritBaseTests;
import org.junit.Test;

public class CheckerUrlTest extends GerritBaseTests {
  @Test
  public void validUrls() throws Exception {
    assertThat(CheckerUrl.clean("https://foo.com/")).isEqualTo("https://foo.com/");
    assertThat(CheckerUrl.clean("http://foo.com/")).isEqualTo("http://foo.com/");
  }

  @Test
  public void emptyUrls() throws Exception {
    assertThat(CheckerUrl.clean("")).isEqualTo("");
    assertThat(CheckerUrl.clean(" ")).isEqualTo("");
    assertThat(CheckerUrl.clean(" \t ")).isEqualTo("");
  }

  @Test
  public void trimUrls() throws Exception {
    assertThat(CheckerUrl.clean(" https://foo.com/")).isEqualTo("https://foo.com/");
    assertThat(CheckerUrl.clean("https://foo.com/ ")).isEqualTo("https://foo.com/");
    assertThat(CheckerUrl.clean(" https://foo.com/ ")).isEqualTo("https://foo.com/");
  }

  @Test
  public void notUrls() throws Exception {
    assertInvalidUrl("foobar", "invalid URL: foobar");
    assertInvalidUrl("foobar:", "invalid URL: foobar:");
    assertInvalidUrl("foo http://bar.com/", "invalid URL: foo http://bar.com/");
  }

  @Test
  public void nonHttpUrls() throws Exception {
    assertInvalidUrl("ftp://foo.com/", "only http/https URLs supported: ftp://foo.com/");
    assertInvalidUrl(
        "mailto:user@example.com", "only http/https URLs supported: mailto:user@example.com");
    assertInvalidUrl(
        "javascript:alert('h4x0r3d')",
        "only http/https URLs supported: javascript:alert('h4x0r3d')");
  }

  private static void assertInvalidUrl(String url, String expectedMessage) {
    try {
      CheckerUrl.clean(url);
      assert_().fail("expected BadRequestException");
    } catch (BadRequestException e) {
      assertThat(e).hasMessageThat().isEqualTo(expectedMessage);
    }
  }
}
