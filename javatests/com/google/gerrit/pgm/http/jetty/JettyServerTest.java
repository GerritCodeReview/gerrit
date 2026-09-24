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

package com.google.gerrit.pgm.http.jetty;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import java.net.URI;
import org.junit.Test;

public class JettyServerTest {
  @Test
  public void isReverseProxied_returnsFalseForDirectSchemes() {
    assertThat(
            JettyServer.isReverseProxied(
                new URI[] {
                  URI.create("http://localhost:8080"), URI.create("https://localhost:8443")
                }))
        .isFalse();
  }

  @Test
  public void isReverseProxied_returnsTrueForProxySchemes() {
    assertThat(
            JettyServer.isReverseProxied(
                new URI[] {
                  URI.create("proxy-http://localhost:8080"),
                  URI.create("proxy-https://localhost:8081")
                }))
        .isTrue();
  }

  @Test
  public void isReverseProxied_returnsTrueForMixedSchemes() {
    assertThat(
            JettyServer.isReverseProxied(
                new URI[] {
                  URI.create("http://localhost:8080"), URI.create("proxy-http://localhost:8081")
                }))
        .isTrue();
  }

  @Test
  public void isReverseProxied_rejectsUnsupportedScheme() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> JettyServer.isReverseProxied(new URI[] {URI.create("ftp://localhost:8080")}));

    assertThat(exception)
        .hasMessageThat()
        .contains("Protocol 'ftp' not supported in httpd.listenurl ");
  }
}
