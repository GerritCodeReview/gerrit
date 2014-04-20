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
 * Provider of Server plugin from an external file
 *
 * Allows to load a plugin from an external file or
 * directory by declaring the ability to handle it
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
   * @param srcFile external file or directory
   * @return plugin name
   */
  String getPluginName(File srcFile);

  /**
   * Loads an external file or directory into a Server plugin.
   *
   * @param name plugin name
   * @param srcFile external file or directory
   * @param pluginUser Gerrit user for interacting with plugins
   * @param snapshot snapshot of the external file
   * @return the Server Plugin
   * @throws InvalidPluginException if plugin cannot be loaded
   */
  ServerPlugin get(String name, File srcFile, PluginUser pluginUser,
      FileSnapshot snapshot) throws InvalidPluginException;
}
