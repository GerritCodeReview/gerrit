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
package com.google.gerrit.server.plugins;

import java.util.Collections;
import java.util.Map;

/**
 * Plugin static resource entry
 *
 * Bean representing a static resource inside a plugin.
 * All static resources are available at <plugin web url>/static
 * and served by the HttpPluginServlet.
 */
public class PluginEntry {
  public static final String ATTR_CHARACTER_ENCODING = "Character-Encoding";
  public static final String ATTR_CONTENT_TYPE = "Content-Type";

  private static final Map<Object,String> EMPTY_ATTRS = Collections.emptyMap();

  private final String name;
  private final long time;
  private final long size;
  private final Map<Object, String> attrs;

  public PluginEntry(String name, long time, long size,
      Map<Object, String> attrs) {
    this.name = name;
    this.time = time;
    this.size = size;
    this.attrs = attrs;
  }

  public PluginEntry(String name, long time, long size) {
    this(name, time, size, EMPTY_ATTRS);
  }

  public String getName() {
    return name;
  }

  public long getTime() {
    return time;
  }

  public long getSize() {
    return size;
  }

  public Map<Object, String> getAttrs() {
    return attrs;
  }
}