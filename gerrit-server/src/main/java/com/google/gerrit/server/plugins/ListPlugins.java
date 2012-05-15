// Copyright (C) 2009 The Android Open Source Project
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
import com.google.common.collect.Maps;
import com.google.gerrit.server.OutputFormat;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;

import org.kohsuke.args4j.Option;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** List projects visible to the calling user. */
public class ListPlugins {
  private final PluginLoader pluginLoader;

  @Option(name = "--format", metaVar = "FMT", usage = "Output display format")
  private OutputFormat format = OutputFormat.TEXT;

  @Inject
  protected ListPlugins(PluginLoader pluginLoader) {
    this.pluginLoader = pluginLoader;
  }

  public OutputFormat getFormat() {
    return format;
  }

  public ListPlugins setFormat(OutputFormat fmt) {
    this.format = fmt;
    return this;
  }

  public void display(OutputStream out) {
    final PrintWriter stdout;
    try {
      stdout =
          new PrintWriter(new BufferedWriter(new OutputStreamWriter(out,
              "UTF-8")));
    } catch (UnsupportedEncodingException e) {
      // Our encoding is required by the specifications for the runtime.
      throw new RuntimeException("JVM lacks UTF-8 encoding", e);
    }

    Map<String, PluginInfo> output = Maps.newTreeMap();

    List<Plugin> plugins = pluginLoader.getPlugins();
    Collections.sort(plugins, new Comparator<Plugin>() {
      @Override
      public int compare(Plugin a, Plugin b) {
        return a.getName().compareTo(b.getName());
      }
    });

    if (!format.isJson()) {
      stdout.format("%-30s %-10s\n", "Name", "Version");
      stdout
          .print("----------------------------------------------------------------------\n");
    }

    for (Plugin p : plugins) {
      PluginInfo info = new PluginInfo();
      info.name = p.getName();
      info.version = p.getVersion();

      if (format.isJson()) {
        output.put(info.name, info);
      } else {
        stdout.format("%-30s %-10s\n", info.name,
            Strings.nullToEmpty(info.version));
      }
    }

    if (format.isJson()) {
      format.newGson().toJson(output,
          new TypeToken<Map<String, PluginInfo>>() {}.getType(), stdout);
      stdout.print('\n');
    }
    stdout.flush();
  }

  private static class PluginInfo {
    transient String name;
    String version;
  }
}
