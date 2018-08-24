// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.extensions.registration;

import com.google.common.base.Splitter;

public class PluginName {
  /** Name that is used as plugin name if Gerrit core implements a plugin extension point. */
  public static final String GERRIT = "gerrit";

  /**
   * Returns a plugin name that can be included into logs.
   *
   * <p>If the provided plugin name is non-null it is returned unmodified.
   *
   * <p>If the provided plugin name is {@code null} this method tries to guess the plugin name from
   * the package name of the provided extension point implementation and returns it as
   * "<plugin-name> (guessed)".
   *
   * <p>If a plugin name could not be guessed "n/a" is returned.
   *
   * @param pluginEntry the plugin entry
   * @return plugin name that can be included into logs, guaranteed to be non-null, but may be "n/a"
   *     if a plugin name cannot be found
   */
  static <T> String forLogging(PluginEntry<T> pluginEntry) {
    if (pluginEntry.getPluginName() != null) {
      return pluginEntry.getPluginName();
    }

    T extensionPoint = pluginEntry.getProvider().get();
    if (extensionPoint == null) {
      return "n/a";
    }

    // Try to guess plugin name from package name.
    // For most plugins the package name contains the plugin name, e.g.:
    //   com.googlesource.gerrit.plugins.<pluginName>.foo.bar
    // Use the part of the package that follows 'plugins' as plugin name.
    boolean foundPluginsPackage = false;
    for (String part : Splitter.on('.').split(extensionPoint.getClass().getName())) {
      if (foundPluginsPackage) {
        return String.format("%s (guessed)", part);
      }
      if (part.equals("plugins")) {
        foundPluginsPackage = true;
      }
    }

    return "n/a";
  }

  private PluginName() {}
}
