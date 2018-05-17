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

import org.junit.Test;

public class ReindexTest extends AbstractProgramTest {
  @Test
  public void testListRunsProperly() throws Exception {
    configBuiler().setString("database", null, "type", "h2").save();

    Reindex reindex = new Reindex();
    int exitCode = reindex.main(new String[] {"-d", getSitePath(), "--list"});

    assertThat(exitCode).named("Reindex exit code").isEqualTo(0);
  }

  @Test
  public void testReindexWorksWithRealSite() throws Exception {
    configBuiler().setString("database", null, "type", "h2").save();

    initSite();

    Reindex reindex = new Reindex();
    int exitCode = reindex.main(new String[] {"-d", getSitePath()});

    assertThat(exitCode).named("Reindex exit code").isEqualTo(0);
  }
}
