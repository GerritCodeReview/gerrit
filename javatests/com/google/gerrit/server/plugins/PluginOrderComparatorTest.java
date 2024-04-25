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

import com.google.common.collect.Sets;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.jar.Manifest;
import org.junit.Test;

public class PluginOrderComparatorTest {
  private static final String API_MODULE = "Gerrit-ApiModule: com.google.gerrit.UnitTest";

  private static final Path FIRST_PLUGIN_PATH = Paths.get("01-first.jar");
  private static final Path LAST_PLUGIN_PATH = Paths.get("99-last.jar");

  private static final Map.Entry<String, Path> FIRST_ENTRY = Map.entry("first", FIRST_PLUGIN_PATH);
  private static final Map.Entry<String, Path> LAST_ENTRY = Map.entry("last", LAST_PLUGIN_PATH);

  private static final Manifest EMPTY_MANIFEST = newManifest("");
  private static final Manifest API_MODULE_MANIFEST = newManifest(API_MODULE);

  @Test
  public void shouldOrderPluginsBasedOnFileName() {
    PluginOrderComparator comparator = new PluginOrderComparator(pluginPath -> EMPTY_MANIFEST);

    assertOrder(comparator, List.of(FIRST_ENTRY, LAST_ENTRY), List.of(FIRST_ENTRY, LAST_ENTRY));
  }

  @Test
  public void shouldReturnPluginWithApiModuleFirst() {
    // return empty manifest for the first plugin and manifest with ApiModule for the last
    PluginOrderComparator.ManifestLoader loader = customLoader(EMPTY_MANIFEST, API_MODULE_MANIFEST);

    PluginOrderComparator comparator = new PluginOrderComparator(loader);

    assertOrder(comparator, List.of(FIRST_ENTRY, LAST_ENTRY), List.of(LAST_ENTRY, FIRST_ENTRY));
  }

  private void assertOrder(
      PluginOrderComparator comparator,
      List<Map.Entry<String, Path>> input,
      List<Map.Entry<String, Path>> expected) {
    TreeSet<Map.Entry<String, Path>> actual = Sets.newTreeSet(comparator);
    actual.addAll(input);

    assertThat(List.copyOf(actual)).isEqualTo(expected);
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
