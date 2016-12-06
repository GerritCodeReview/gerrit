// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.schema;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

public class HANATest {

  private HANA hana;
  private Config config;

  @Before
  public void setup() {
    config = new Config();
    config.setString("database", null, "hostname", "my.host");
    hana = new HANA(config);
  }

  @Test
  public void getUrl() throws Exception {
    config.setString("database", null, "instance", "3");
    assertThat(hana.getUrl()).isEqualTo("jdbc:sap://my.host:30315");

    config.setString("database", null, "instance", "77");
    assertThat(hana.getUrl()).isEqualTo("jdbc:sap://my.host:37715");
  }

  @Test
  public void getIndexScript() throws Exception {
    assertThat(hana.getIndexScript()).isSameAs(ScriptRunner.NOOP);
  }
}
