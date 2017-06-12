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
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

/**
 * Plugin static resource entry
 *
 * <p>Bean representing a static resource inside a plugin. All static resources are available at
 * {@code <plugin web url>/static} and served by the HttpPluginServlet.
 */
public class PluginEntry {
  public static final String ATTR_CHARACTER_ENCODING = "Character-Encoding";
  public static final String ATTR_CONTENT_TYPE = "Content-Type";
  public static final Comparator<PluginEntry> COMPARATOR_BY_NAME =
      new Comparator<PluginEntry>() {
        @Override
        public int compare(PluginEntry a, PluginEntry b) {
          return a.getName().compareTo(b.getName());
        }
      };

  private static final Map<Object, String> EMPTY_ATTRS = Collections.emptyMap();
  private static final Optional<Long> NO_SIZE = Optional.empty();

  private final String name;
  private final long time;
  private final Optional<Long> size;
  private final Map<Object, String> attrs;

  public PluginEntry(String name, long time, Optional<Long> size, Map<Object, String> attrs) {
    this.name = name;
    this.time = time;
    this.size = size;
    this.attrs = attrs;
  }

  public PluginEntry(String name, long time, Optional<Long> size) {
    this(name, time, size, EMPTY_ATTRS);
  }

  public PluginEntry(String name, long time) {
    this(name, time, NO_SIZE, EMPTY_ATTRS);
  }

  public String getName() {
    return name;
  }

  public long getTime() {
    return time;
  }

  public Optional<Long> getSize() {
    return size;
  }

  public Map<Object, String> getAttrs() {
    return attrs;
  }
}
