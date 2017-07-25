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

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.common.PluginInfo;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.server.OutputFormat;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.kohsuke.args4j.Option;

/** List the installed plugins. */
@RequiresCapability(GlobalCapability.VIEW_PLUGINS)
public class ListPlugins implements RestReadView<TopLevelResource> {
  private final PluginLoader pluginLoader;

  private boolean all;

  @Deprecated
  @Option(name = "--format", usage = "(deprecated) output format")
  private OutputFormat format = OutputFormat.TEXT;

  @Option(
    name = "--all",
    aliases = {"-a"},
    usage = "List all plugins, including disabled plugins"
  )
  public void setAll(boolean all) {
    this.all = all;
  }

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

  @Override
  public Object apply(TopLevelResource resource) {
    format = OutputFormat.JSON;
    return display(null);
  }

  public SortedMap<String, PluginInfo> apply() {
    format = OutputFormat.JSON;
    return display(null);
  }

  public SortedMap<String, PluginInfo> display(@Nullable PrintWriter stdout) {
    SortedMap<String, PluginInfo> output = new TreeMap<>();
    List<Plugin> plugins = Lists.newArrayList(pluginLoader.getPlugins(all));
    Collections.sort(
        plugins,
        new Comparator<Plugin>() {
          @Override
          public int compare(Plugin a, Plugin b) {
            return a.getName().compareTo(b.getName());
          }
        });

    if (!format.isJson()) {
      stdout.format("%-30s %-10s %-8s %s\n", "Name", "Version", "Status", "File");
      stdout.print(
          "-------------------------------------------------------------------------------\n");
    }

    for (Plugin p : plugins) {
      PluginInfo info = toPluginInfo(p);
      if (format.isJson()) {
        output.put(p.getName(), info);
      } else {
        stdout.format(
            "%-30s %-10s %-8s %s\n",
            p.getName(),
            Strings.nullToEmpty(info.version),
            p.isDisabled() ? "DISABLED" : "ENABLED",
            p.getSrcFile().getFileName());
      }
    }

    if (stdout == null) {
      return output;
    } else if (format.isJson()) {
      format
          .newGson()
          .toJson(output, new TypeToken<Map<String, PluginInfo>>() {}.getType(), stdout);
      stdout.print('\n');
    }
    stdout.flush();
    return null;
  }

  public static PluginInfo toPluginInfo(Plugin p) {
    String id;
    String version;
    String indexUrl;
    Boolean disabled;

    id = Url.encode(p.getName());
    version = p.getVersion();
    disabled = p.isDisabled() ? true : null;
    indexUrl = p.getSrcFile() != null ? String.format("plugins/%s/", p.getName()) : null;

    return new PluginInfo(id, version, indexUrl, disabled);
  }
}
