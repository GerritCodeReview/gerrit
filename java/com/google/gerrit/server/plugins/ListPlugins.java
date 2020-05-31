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

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toMap;

import com.google.common.collect.Streams;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.api.plugins.Plugins;
import com.google.gerrit.extensions.common.PluginInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.Url;
import com.google.inject.Inject;
import java.util.Locale;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.kohsuke.args4j.Option;

/** List the installed plugins. */
@RequiresCapability(GlobalCapability.VIEW_PLUGINS)
public class ListPlugins implements RestReadView<TopLevelResource> {
  private final PluginLoader pluginLoader;

  private boolean all;
  private int limit;
  private int start;
  private String matchPrefix;
  private String matchSubstring;
  private String matchRegex;

  @Option(
      name = "--all",
      aliases = {"-a"},
      usage = "List all plugins, including disabled plugins")
  public void setAll(boolean all) {
    this.all = all;
  }

  @Option(
      name = "--limit",
      aliases = {"-n"},
      metaVar = "CNT",
      usage = "maximum number of plugins to list")
  public void setLimit(int limit) {
    this.limit = limit;
  }

  @Option(
      name = "--start",
      aliases = {"-S"},
      metaVar = "CNT",
      usage = "number of plugins to skip")
  public void setStart(int start) {
    this.start = start;
  }

  @Option(
      name = "--prefix",
      aliases = {"-p"},
      metaVar = "PREFIX",
      usage = "match plugin prefix")
  public void setMatchPrefix(String matchPrefix) {
    this.matchPrefix = matchPrefix;
  }

  @Option(
      name = "--match",
      aliases = {"-m"},
      metaVar = "MATCH",
      usage = "match plugin substring")
  public void setMatchSubstring(String matchSubstring) {
    this.matchSubstring = matchSubstring;
  }

  @Option(name = "-r", metaVar = "REGEX", usage = "match plugin regex")
  public void setMatchRegex(String matchRegex) {
    this.matchRegex = matchRegex;
  }

  @Inject
  protected ListPlugins(PluginLoader pluginLoader) {
    this.pluginLoader = pluginLoader;
  }

  public ListPlugins request(Plugins.ListRequest request) {
    this.setAll(request.getAll());
    this.setStart(request.getStart());
    this.setLimit(request.getLimit());
    this.setMatchPrefix(request.getPrefix());
    this.setMatchSubstring(request.getSubstring());
    this.setMatchRegex(request.getRegex());
    return this;
  }

  @Override
  public SortedMap<String, PluginInfo> apply(TopLevelResource resource) throws BadRequestException {
    Stream<Plugin> s = Streams.stream(pluginLoader.getPlugins(all));
    if (matchPrefix != null) {
      checkMatchOptions(matchSubstring == null && matchRegex == null);
      s = s.filter(p -> p.getName().startsWith(matchPrefix));
    } else if (matchSubstring != null) {
      checkMatchOptions(matchPrefix == null && matchRegex == null);
      String substring = matchSubstring.toLowerCase(Locale.US);
      s = s.filter(p -> p.getName().toLowerCase(Locale.US).contains(substring));
    } else if (matchRegex != null) {
      checkMatchOptions(matchPrefix == null && matchSubstring == null);
      Pattern pattern = Pattern.compile(matchRegex);
      s = s.filter(p -> pattern.matcher(p.getName()).matches());
    }
    s = s.sorted(comparing(Plugin::getName));
    if (start > 0) {
      s = s.skip(start);
    }
    if (limit > 0) {
      s = s.limit(limit);
    }
    return new TreeMap<>(s.collect(toMap(Plugin::getName, ListPlugins::toPluginInfo)));
  }

  private void checkMatchOptions(boolean cond) throws BadRequestException {
    if (!cond) {
      throw new BadRequestException("specify exactly one of p/m/r");
    }
  }

  public static PluginInfo toPluginInfo(Plugin p) {
    String id;
    String version;
    String apiVersion;
    String indexUrl;
    String filename;
    Boolean disabled;

    id = Url.encode(p.getName());
    version = p.getVersion();
    apiVersion = p.getApiVersion();
    disabled = p.isDisabled() ? true : null;
    if (p.getSrcFile() != null) {
      indexUrl = String.format("plugins/%s/", p.getName());
      filename = p.getSrcFile().getFileName().toString();
    } else {
      indexUrl = null;
      filename = null;
    }

    return new PluginInfo(id, version, apiVersion, indexUrl, filename, disabled);
  }
}
