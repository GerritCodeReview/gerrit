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

package com.google.gerrit.server.config;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.server.mail.XsrfException;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

public class AuthConfigTest {

  private Config cfg;
  private AuthConfig authCfg;

  @Before
  public void setUp() throws Exception {
    cfg = new Config();
    authCfg = new AuthConfig(cfg);
  }

  @Test
  public void defaultHttpPasswordLengthWhenNotConfigured() {
    assertThat(authCfg.getHttpPasswordLength()).isEqualTo(AuthConfig.DEFAULT_HTTP_PASSWORD_LENGTH);
  }

  @Test
  public void exceptionWhenHttpPasswordLengthTooLow() throws XsrfException {
    cfg = new Config();
    configureHttpPasswordLength(15);
    Exception e = assertThrows(IllegalStateException.class, () -> new AuthConfig(cfg));
    assertThat(e).hasMessageThat().contains("must be greater");
  }

  @Test
  public void exceptionWhenHttpPasswordLengthTooHigh() throws XsrfException {
    cfg = new Config();
    configureHttpPasswordLength(73);
    Exception e = assertThrows(IllegalStateException.class, () -> new AuthConfig(cfg));
    assertThat(e).hasMessageThat().contains("must be greater");
  }

  private void configureHttpPasswordLength(int httpPasswordLength) {
    cfg.setInt(AuthConfig.SECTION_NAME, null, "httpPasswordLength", httpPasswordLength);
  }
}
