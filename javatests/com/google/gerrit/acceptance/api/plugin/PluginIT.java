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
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.extensions.api.plugins.InstallPluginInput;
import com.google.gerrit.extensions.api.plugins.PluginApi;
import com.google.gerrit.extensions.api.plugins.Plugins.ListRequest;
import com.google.gerrit.extensions.common.PluginInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.RawInput;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.plugins.MandatoryPluginsCollection;
import com.google.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.Test;

@NoHttpd
public class PluginIT extends AbstractDaemonTest {
  private static final String JS_PLUGIN = "Gerrit.install(function(self){});\n";
  private static final RawInput JS_PLUGIN_CONTENT = RawInputUtil.create(JS_PLUGIN.getBytes(UTF_8));

  private static final ImmutableList<String> PLUGINS =
      ImmutableList.of(
          "plugin-a.js",
          "plugin-b.js",
          "plugin-c.js",
          "plugin-d.js",
          "plugin-normal.jar",
          "plugin-empty.jar",
          "plugin-unset.jar",
          "plugin_e.js");

  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private MandatoryPluginsCollection mandatoryPluginsCollection;

  @Test
  @GerritConfig(name = "plugins.allowRemoteAdmin", value = "true")
  public void pluginManagement() throws Exception {
    // No plugins are loaded
    assertThat(list().get()).isEmpty();
    assertThat(list().all().get()).isEmpty();

    PluginApi api;
    // Install all the plugins
    InstallPluginInput input = new InstallPluginInput();
    for (String plugin : PLUGINS) {
      input.raw = pluginContent(plugin);
      api = gApi.plugins().install(plugin, input);
      assertThat(api).isNotNull();
      PluginInfo info = api.get();
      String name = pluginName(plugin);
      assertThat(info.id).isEqualTo(name);
      assertThat(info.version).isEqualTo(pluginVersion(plugin));
      assertThat(info.apiVersion).isEqualTo(pluginApiVersion(plugin));
      assertThat(info.indexUrl).isEqualTo(String.format("plugins/%s/", name));
      assertThat(info.filename).isEqualTo(plugin);
      assertThat(info.disabled).isNull();
    }
    assertPlugins(list().get(), PLUGINS);

    // With pagination
    assertPlugins(list().start(1).limit(2).get(), PLUGINS.subList(1, 3));

    // With prefix
    assertPlugins(list().prefix("plugin-b").get(), ImmutableList.of("plugin-b.js"));
    assertPlugins(list().prefix("PLUGIN-").get(), ImmutableList.of());

    // With substring
    assertPlugins(list().substring("lugin-").get(), PLUGINS.subList(0, PLUGINS.size() - 1));
    assertPlugins(list().substring("lugin-").start(1).limit(2).get(), PLUGINS.subList(1, 3));

    // With regex
    assertPlugins(list().regex(".*in-b").get(), ImmutableList.of("plugin-b.js"));
    assertPlugins(list().regex("plugin-.*").get(), PLUGINS.subList(0, PLUGINS.size() - 1));
    assertPlugins(list().regex("plugin-.*").start(1).limit(2).get(), PLUGINS.subList(1, 3));

    // Invalid match combinations
    assertBadRequest(list().regex(".*in-b").substring("a"));
    assertBadRequest(list().regex(".*in-b").prefix("a"));
    assertBadRequest(list().substring(".*in-b").prefix("a"));

    // Disable mandatory
    mandatoryPluginsCollection.add("plugin_e");
    assertThrows(MethodNotAllowedException.class, () -> gApi.plugins().name("plugin_e").disable());
    api = gApi.plugins().name("plugin_e");
    assertThat(api.get().disabled).isNull();

    // Disable non-mandatory
    api = gApi.plugins().name("plugin-a");
    api.disable();
    api = gApi.plugins().name("plugin-a");
    assertThat(api.get().disabled).isTrue();
    assertPlugins(list().get(), PLUGINS.subList(1, PLUGINS.size()));
    assertPlugins(list().all().get(), PLUGINS);

    // Enable
    api.enable();
    api = gApi.plugins().name("plugin-a");
    assertThat(api.get().disabled).isNull();
    assertPlugins(list().get(), PLUGINS);

    // Using deprecated input
    deprecatedInput();

    // Non-admin cannot disable
    requestScopeOperations.setApiUser(user.id());
    assertThrows(AuthException.class, () -> gApi.plugins().name("plugin-a").disable());
  }

  @SuppressWarnings("deprecation")
  private void deprecatedInput() throws Exception {
    com.google.gerrit.extensions.common.InstallPluginInput input =
        new com.google.gerrit.extensions.common.InstallPluginInput();
    input.raw = JS_PLUGIN_CONTENT;
    gApi.plugins().install("legacy.js", input);
    gApi.plugins().name("legacy").get();
  }

  @Test
  public void installNotAllowed() throws Exception {
    MethodNotAllowedException thrown =
        assertThrows(
            MethodNotAllowedException.class,
            () -> gApi.plugins().install("test.js", new InstallPluginInput()));
    assertThat(thrown).hasMessageThat().contains("remote plugin administration is disabled");
  }

  @Test
  public void getNonExistingThrowsNotFound() throws Exception {
    assertThrows(ResourceNotFoundException.class, () -> gApi.plugins().name("does-not-exist"));
  }

  private ListRequest list() throws RestApiException {
    return gApi.plugins().list();
  }

  private void assertPlugins(List<PluginInfo> actual, List<String> expected) {
    List<String> _actual = actual.stream().map(p -> p.id).collect(toList());
    List<String> _expected = expected.stream().map(this::pluginName).collect(toList());
    assertThat(_actual).containsExactlyElementsIn(_expected);
  }

  private String pluginName(String plugin) {
    int dot = plugin.indexOf(".");
    assertThat(dot).isGreaterThan(0);
    return plugin.substring(0, dot);
  }

  private RawInput pluginJarContent(String plugin) throws IOException {
    ByteArrayOutputStream arrayStream = new ByteArrayOutputStream();
    Manifest manifest = new Manifest();
    Attributes attributes = manifest.getMainAttributes();
    attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
    if (!plugin.endsWith("-unset.jar")) {
      attributes.put(Attributes.Name.IMPLEMENTATION_VERSION, pluginVersion(plugin));
      attributes.put(new Attributes.Name("Gerrit-ApiVersion"), pluginApiVersion(plugin));
    }
    try (JarOutputStream jarStream = new JarOutputStream(arrayStream, manifest)) {}
    return RawInputUtil.create(arrayStream.toByteArray());
  }

  private RawInput pluginContent(String plugin) throws IOException {
    if (plugin.endsWith(".js")) {
      return JS_PLUGIN_CONTENT;
    }
    assertThat(plugin).endsWith(".jar");
    return pluginJarContent(plugin);
  }

  @Nullable
  private String pluginVersion(String plugin) {
    String name = pluginName(plugin);
    if (name.endsWith("empty")) {
      return "";
    }
    if (name.endsWith("unset")) {
      return null;
    }
    int dash = name.lastIndexOf("-");
    return dash > 0 ? name.substring(dash + 1) : "";
  }

  @Nullable
  private String pluginApiVersion(String plugin) {
    if (plugin.endsWith("normal.jar")) {
      return "2.16.19-SNAPSHOT";
    }
    if (plugin.endsWith("empty.jar")) {
      return "";
    }
    return null;
  }

  private void assertBadRequest(ListRequest req) throws Exception {
    assertThrows(BadRequestException.class, () -> req.get());
  }
}
