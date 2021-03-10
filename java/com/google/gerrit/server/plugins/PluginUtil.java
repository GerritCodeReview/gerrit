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

package com.google.gerrit.server.plugins;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.google.common.io.ByteStreams;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.webui.JavaScriptPlugin;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class PluginUtil {
  public static List<Path> listPlugins(Path pluginsDir, String suffix) throws IOException {
    if (pluginsDir == null || !Files.exists(pluginsDir)) {
      return ImmutableList.of();
    }
    DirectoryStream.Filter<Path> filter =
        entry -> {
          String n = entry.getFileName().toString();
          boolean accept =
              !n.startsWith(".last_") && !n.startsWith(".next_") && Files.isRegularFile(entry);
          if (!Strings.isNullOrEmpty(suffix)) {
            accept &= n.endsWith(suffix);
          }
          return accept;
        };
    try (DirectoryStream<Path> files = Files.newDirectoryStream(pluginsDir, filter)) {
      return Ordering.natural().sortedCopy(files);
    }
  }

  static List<Path> listPlugins(Path pluginsDir) throws IOException {
    return listPlugins(pluginsDir, null);
  }

  static Path asTemp(InputStream in, String prefix, String suffix, Path dir) throws IOException {
    if (!Files.exists(dir)) {
      Files.createDirectories(dir);
    }
    Path tmp = Files.createTempFile(dir, prefix, suffix);
    boolean keep = false;
    try (OutputStream out = Files.newOutputStream(tmp)) {
      ByteStreams.copy(in, out);
      keep = true;
      return tmp;
    } finally {
      if (!keep) {
        Files.delete(tmp);
      }
    }
  }

  public static String nameOf(Path plugin) {
    return nameOf(plugin.getFileName().toString());
  }

  static String nameOf(String name) {
    if (name.endsWith(".disabled")) {
      name = name.substring(0, name.lastIndexOf('.'));
    }
    int ext = name.lastIndexOf('.');
    return 0 < ext ? name.substring(0, ext) : name;
  }

  static ClassLoader parentFor(Plugin.ApiType type) {
    switch (type) {
      case EXTENSION:
        return PluginName.class.getClassLoader();
      case PLUGIN:
        return PluginLoader.class.getClassLoader();
      case JS:
        return JavaScriptPlugin.class.getClassLoader();
      default:
        throw new IllegalArgumentException("Unsupported ApiType " + type);
    }
  }
}
