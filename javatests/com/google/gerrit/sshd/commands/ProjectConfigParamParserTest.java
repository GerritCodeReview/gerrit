// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.sshd.commands;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.extensions.api.projects.ConfigValue;
import java.util.Collections;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class ProjectConfigParamParserTest {

  private CreateProjectCommand cmd;

  @Before
  public void setUp() {
    cmd = new CreateProjectCommand();
  }

  @Test
  public void parseSingleValue() throws Exception {
    String in = "a.b=c";
    Map<String, Map<String, ConfigValue>> r =
        cmd.parsePluginConfigValues(Collections.singletonList(in));
    ConfigValue configValue = r.get("a").get("b");
    assertThat(configValue.value).isEqualTo("c");
    assertThat(configValue.values).isNull();
  }

  @Test
  public void parseMultipleValue() throws Exception {
    String in = "a.b=c,d,e";
    Map<String, Map<String, ConfigValue>> r =
        cmd.parsePluginConfigValues(Collections.singletonList(in));
    ConfigValue configValue = r.get("a").get("b");
    assertThat(configValue.values).containsExactly("c", "d", "e").inOrder();
    assertThat(configValue.value).isNull();
  }
}
