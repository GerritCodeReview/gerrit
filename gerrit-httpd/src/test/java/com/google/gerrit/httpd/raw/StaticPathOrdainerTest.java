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

package com.google.gerrit.httpd.raw;

import static com.google.common.truth.Truth.assertThat;

import com.google.template.soy.data.SanitizedContent;
import org.eclipse.jgit.lib.Config;

import org.junit.Test;

public class StaticPathOrdainerTest {

  private static String testStaticPathString = "http://my-cdn.com/foo/bar/";

  private Config configWithCdnPath(String cdnPath) {
    Config cfg = new Config();
    cfg.setString("gerrit", null, "cdnPath", cdnPath);
    return cfg;
  }

  @Test
  public void noPathAndNoCDN() {
    SanitizedContent staticPath = StaticPathOrdainer.ordainStaticPath(null, new Config());
    assertThat(staticPath.stringValue()).isEqualTo("");
  }

  @Test
  public void pathAndNoCDN() {
    SanitizedContent staticPath = StaticPathOrdainer.ordainStaticPath("/gerrit", new Config());
    assertThat(staticPath.stringValue()).isEqualTo("/gerrit");
  }

  @Test
  public void noPathAndCDN() {
    SanitizedContent staticPath = StaticPathOrdainer.ordainStaticPath(null,
        configWithCdnPath(testStaticPathString));
    assertThat(staticPath.stringValue()).isEqualTo("http://my-cdn.com/foo/bar/");
  }

  @Test
  public void pathAndCDN() {
    SanitizedContent staticPath = StaticPathOrdainer.ordainStaticPath("/gerrit",
        configWithCdnPath(testStaticPathString));
    assertThat(staticPath.stringValue()).isEqualTo("http://my-cdn.com/foo/bar/");
  }
}
