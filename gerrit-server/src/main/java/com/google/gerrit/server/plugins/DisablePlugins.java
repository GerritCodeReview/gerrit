// Copyright (C) 2012 The Android Open Source Project
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

import com.google.common.collect.Sets;
import com.google.gerrit.server.OutputFormat;
import com.google.inject.Inject;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.List;

/** Disable the specified plugins */
public class DisablePlugins {
  private final PluginLoader pluginLoader;

  @Option(name = "--format", metaVar = "FMT", usage = "Output display format")
  private OutputFormat format = OutputFormat.TEXT;

  @Argument(index = 0, metaVar = "NAME", required = true, usage = "plugin to remove")
  List<String> names;

  @Inject
  protected DisablePlugins(PluginLoader pluginLoader) {
    this.pluginLoader = pluginLoader;
  }

  public OutputFormat getFormat() {
    return format;
  }

  public DisablePlugins setFormat(OutputFormat fmt) {
    this.format = fmt;
    return this;
  }

  public void display(OutputStream out) {
    final PrintWriter stdout;

    if (names != null && !names.isEmpty()) {
      pluginLoader.disablePlugins(Sets.newHashSet(names));
    }

    try {
      stdout =
          new PrintWriter(new BufferedWriter(new OutputStreamWriter(out,
              "UTF-8")));
    } catch (UnsupportedEncodingException e) {
      // Our encoding is required by the specifications for the runtime.
      throw new RuntimeException("JVM lacks UTF-8 encoding", e);
    }

    if (format.isJson()) {
      stdout.print("{\"disabled\":true}");
    }
    stdout.print('\n');
    stdout.flush();
  }
}
