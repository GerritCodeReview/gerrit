// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.pgm;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.truth.ExitCodeSubject.exitCode;

import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;

public class PasswdTest {
  @Rule public SiteRule siteRule = new SiteRule();

  @Test
  public void passwdExitsWithoutErrors() throws Exception {
    Passwd passwd = new Passwd();
    int exitCode =
        passwd.main(
            new String[] {
              "--site-path",
              siteRule.getSitePaths().site_path.toString(),
              "test.p4ss",
              "s3cur3-P4ssw0rd"
            });

    assertAbout(exitCode()).that(exitCode).isSuccessful();
  }

  @Test
  public void passwdStoresThePassword() throws Exception {
    Passwd passwd = new Passwd();
    passwd.main(
        new String[] {
          "--site-path",
          siteRule.getSitePaths().site_path.toString(),
          "test.p4ss",
          "s3cur3-P4ssw0rd"
        });

    assertThat(Files.exists(siteRule.getSitePaths().secure_config))
        .named("secure.config exists")
        .isTrue();
    assertThat(Files.readAllLines(siteRule.getSitePaths().secure_config))
        .containsAllOf("[test]", "\tp4ss = s3cur3-P4ssw0rd")
        .inOrder();
  }
}
