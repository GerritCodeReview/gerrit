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

package com.google.gerrit.acceptance.api.plugin;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.extensions.api.plugins.PluginApi;
import com.google.gerrit.extensions.api.plugins.Plugins.ListRequest;
import com.google.gerrit.extensions.common.InstallPluginInput;
import com.google.gerrit.extensions.common.PluginInfo;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import java.util.List;
import org.junit.Test;

@NoHttpd
public class PluginIT extends AbstractDaemonTest {
  private static final byte[] JS_PLUGIN_CONTENT =
      "Gerrit.install(function(self){});\n".getBytes(UTF_8);
  private static final List<String> PLUGINS =
      ImmutableList.of("plugin-a", "plugin-b", "plugin-c", "plugin-d");

  @Test
  @GerritConfig(name = "plugins.allowRemoteAdmin", value = "true")
  public void pluginManagement() throws Exception {
    // No plugins are loaded
    assertThat(list().get()).isEmpty();
    assertThat(list().all().get()).isEmpty();

    PluginApi test;
    PluginInfo info;

    // Install all the plugins
    InstallPluginInput input = new InstallPluginInput();
    input.raw = RawInputUtil.create(JS_PLUGIN_CONTENT);
    for (String plugin : PLUGINS) {
      test = gApi.plugins().install(plugin + ".js", input);
      assertThat(test).isNotNull();
      info = test.get();
      assertThat(info.id).isEqualTo(plugin);
      assertThat(info.disabled).isNull();
    }
    assertThat(list().get()).hasSize(PLUGINS.size());

    // Disable
    test = gApi.plugins().name("plugin-a");
    test.disable();
    test = gApi.plugins().name("plugin-a");
    info = test.get();
    assertThat(info.disabled).isTrue();
    assertThat(list().get()).hasSize(PLUGINS.size() - 1);
    assertThat(list().all().get()).hasSize(PLUGINS.size());

    // Enable
    test.enable();
    test = gApi.plugins().name("plugin-a");
    info = test.get();
    assertThat(info.disabled).isNull();
    assertThat(list().get()).hasSize(PLUGINS.size());
  }

  @Test
  public void installNotAllowed() throws Exception {
    exception.expect(MethodNotAllowedException.class);
    exception.expectMessage("remote installation is disabled");
    gApi.plugins().install("test.js", new InstallPluginInput());
  }

  @Test
  public void getNonExistingThrowsNotFound() throws Exception {
    exception.expect(ResourceNotFoundException.class);
    gApi.plugins().name("does-not-exist");
  }

  private ListRequest list() throws RestApiException {
    return gApi.plugins().list();
  }
}
