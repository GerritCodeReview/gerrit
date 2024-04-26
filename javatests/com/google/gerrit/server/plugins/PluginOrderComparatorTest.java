// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.server.plugins;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.jar.Manifest;
import org.junit.Test;

public class PluginOrderComparatorTest {
  private static final String API_MODULE = "Gerrit-ApiModule: com.google.gerrit.UnitTest";
  private static final String LOAD_PRIORITY = "Gerrit-LoadPriority: 1";

  private static final Path FIRST_PLUGIN_PATH = Paths.get("01-plugin.jar");
  private static final Path LAST_PLUGIN_PATH = Paths.get("99-plugin.jar");

  private static final Map.Entry<String, Path> FIRST_ENTRY = Map.entry("first", FIRST_PLUGIN_PATH);
  private static final Map.Entry<String, Path> SECOND_ENTRY = Map.entry("second", LAST_PLUGIN_PATH);

  private static final Manifest EMPTY_MANIFEST = newManifest("");
  private static final Manifest API_MODULE_MANIFEST = newManifest(API_MODULE);
  private static final Manifest LOAD_PRIORITY_MANIFEST = newManifest(LOAD_PRIORITY);
  private static final Manifest API_MODULE_AND_PRIOIRITY_MANIFEST =
      newManifest(API_MODULE + "\n" + LOAD_PRIORITY);

  @Test
  public void shouldOrderPluginsBasedOnFileName() {
    PluginOrderComparator comparator = new PluginOrderComparator(pluginPath -> EMPTY_MANIFEST);

    assertThat(comparator.compare(FIRST_ENTRY, SECOND_ENTRY)).isEqualTo(-1);
    assertThat(comparator.compare(SECOND_ENTRY, FIRST_ENTRY)).isEqualTo(1);
  }

  @Test
  public void shouldReturnPluginWithApiModuleFirst() {
    // return empty manifest for the first plugin and manifest with ApiModule for the last
    PluginOrderComparator.ManifestLoader loader = customLoader(EMPTY_MANIFEST, API_MODULE_MANIFEST);

    PluginOrderComparator comparator = new PluginOrderComparator(loader);

    assertThat(comparator.compare(FIRST_ENTRY, SECOND_ENTRY)).isEqualTo(1);
    assertThat(comparator.compare(SECOND_ENTRY, FIRST_ENTRY)).isEqualTo(-1);
  }

  @Test
  public void shouldReturnPriorityPluginFirst() {
    PluginOrderComparator.ManifestLoader loader =
        customLoader(EMPTY_MANIFEST, LOAD_PRIORITY_MANIFEST);

    PluginOrderComparator comparator = new PluginOrderComparator(loader);

    assertThat(comparator.compare(FIRST_ENTRY, SECOND_ENTRY)).isEqualTo(1);
    assertThat(comparator.compare(SECOND_ENTRY, FIRST_ENTRY)).isEqualTo(-1);
  }

  @Test
  public void shouldReturnApiModuleBeforePriority() {
    PluginOrderComparator.ManifestLoader loader =
        customLoader(LOAD_PRIORITY_MANIFEST, API_MODULE_MANIFEST);

    PluginOrderComparator comparator = new PluginOrderComparator(loader);

    assertThat(comparator.compare(FIRST_ENTRY, SECOND_ENTRY)).isEqualTo(1);
    assertThat(comparator.compare(SECOND_ENTRY, FIRST_ENTRY)).isEqualTo(-1);
  }

  @Test
  public void shouldReturnApiModuleWithPriorityFirst() {
    PluginOrderComparator.ManifestLoader loader =
        customLoader(API_MODULE_MANIFEST, API_MODULE_AND_PRIOIRITY_MANIFEST);

    PluginOrderComparator comparator = new PluginOrderComparator(loader);

    assertThat(comparator.compare(FIRST_ENTRY, SECOND_ENTRY)).isEqualTo(1);
    assertThat(comparator.compare(SECOND_ENTRY, FIRST_ENTRY)).isEqualTo(-1);
  }

  private static Manifest newManifest(String content) {
    String withEmptyLine = content + "\n";
    try {
      Manifest manifest = new Manifest();
      manifest.read(new ByteArrayInputStream(withEmptyLine.getBytes(UTF_8)));
      return manifest;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private PluginOrderComparator.ManifestLoader customLoader(
      Manifest firstManifest, Manifest secondManifest) {
    return pluginPath -> {
      if (pluginPath.equals(FIRST_PLUGIN_PATH)) {
        return firstManifest;
      }
      if (pluginPath.equals(LAST_PLUGIN_PATH)) {
        return secondManifest;
      }
      throw new IllegalArgumentException("unsupported path: " + pluginPath);
    };
  }
}
