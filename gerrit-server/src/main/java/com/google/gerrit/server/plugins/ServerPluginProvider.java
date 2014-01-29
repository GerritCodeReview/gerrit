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

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.server.PluginUser;

import org.eclipse.jgit.internal.storage.file.FileSnapshot;

import java.io.File;

/**
 * Provider of one Server plugin from one external file
 *
 * Allows to load one plugin from one external file or
 * one directory by declaring the ability to handle it.
 *
 * In order to load multiple files into a single plugin,
 * group them into a directory tree and then load the directory
 * root as a single plugin.
 */
@ExtensionPoint
public interface ServerPluginProvider {
  /**
   * Declares the availability to manage an external file or directory
   *
   * @param srcFile the external file or directory
   * @return true if file or directory can be loaded into a Server Plugin
   */
  boolean handles(File srcFile);

  /**
   * Returns the plugin name of an external file or directory
   *
   * Should be called only if {@link #handles(File) handles(srcFile)}
   * returns true and thus srcFile not a supported plugin format.
   *
   * An IllegalArgumentException is thrown otherwise as srcFile
   * is not a valid file format for extracting its plugin name.
   *
   * @param srcFile external file or directory
   * @return plugin name
   */
  String getPluginName(File srcFile);

  /**
   * Loads an external file or directory into a Server plugin.
   *
   * @param srcFile external file or directory
   * @param pluginUser Gerrit user for interacting with plugins
   * @param snapshot snapshot of the external file
   * @param pluginCanonicalWebUrl plugin root Web URL
   * @param pluginDataDir directory for plugin data
   * @return the Server Plugin
   * @throws InvalidPluginException if plugin cannot be loaded
   */
  ServerPlugin get(File srcFile, PluginUser pluginUser,
      FileSnapshot snapshot, String pluginCanonicalWebUrl, File pluginDataDir)
          throws InvalidPluginException;
}
