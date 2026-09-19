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

package com.google.gerrit.httpd;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.common.io.BaseEncoding;
import com.google.gerrit.httpd.BasicAuthHeader.Credentials;
import java.io.IOException;
import org.junit.Test;

public class BasicAuthHeaderTest {
  private static String basic(String usernamePassword) {
    return BasicAuthHeader.PREFIX + BaseEncoding.base64().encode(usernamePassword.getBytes(UTF_8));
  }

  @Test
  public void parsesUsernameAndPassword() throws Exception {
    assertThat(BasicAuthHeader.parse(basic("user:secret"), null))
        .hasValue(new Credentials("user", "secret"));
  }

  @Test
  public void passwordMayContainColon() throws Exception {
    assertThat(BasicAuthHeader.parse(basic("user:a:b"), null))
        .hasValue(new Credentials("user", "a:b"));
  }

  @Test
  public void nullHeaderIsEmpty() throws Exception {
    assertThat(BasicAuthHeader.parse(null, null)).isEmpty();
  }

  @Test
  public void nonBasicHeaderIsEmpty() throws Exception {
    assertThat(BasicAuthHeader.parse("Bearer token", null)).isEmpty();
  }

  @Test
  public void missingColonIsEmpty() throws Exception {
    assertThat(BasicAuthHeader.parse(basic("username"), null)).isEmpty();
  }

  @Test
  public void leadingColonIsEmpty() throws Exception {
    assertThat(BasicAuthHeader.parse(basic(":secret"), null)).isEmpty();
  }

  @Test
  public void trailingColonIsEmpty() throws Exception {
    assertThat(BasicAuthHeader.parse(basic("user:"), null)).isEmpty();
  }

  @Test
  public void invalidCharsetThrows() {
    assertThrows(
        IOException.class, () -> BasicAuthHeader.parse(basic("user:secret"), "no-such-charset"));
  }
}
