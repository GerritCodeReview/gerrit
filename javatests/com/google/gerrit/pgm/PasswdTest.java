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

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Files;
import org.junit.Test;

public class PasswdTest extends AbstractProgramTest {
  @Test
  public void testPasswdIsRunProperly() throws Exception {
    Passwd cat = new Passwd();
    int exitCode = cat.main(new String[] {"-d", getSitePath(), "test.p4ss", "s3cur3-P4ssw0rd"});

    assertThat(exitCode).named("Passwd exit code").isEqualTo(0);
    assertThat(site.secure_config.toFile().exists()).named("secure.config exists").isTrue();

    assertThat(Files.readAllLines(site.secure_config))
        .containsExactly("[test]", "\tp4ss = s3cur3-P4ssw0rd");
  }
}
