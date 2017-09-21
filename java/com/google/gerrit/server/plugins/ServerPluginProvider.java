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
import java.nio.file.Path;
import org.eclipse.jgit.internal.storage.file.FileSnapshot;

/**
 * Provider of one Server plugin from one external file
 *
 * <p>Allows to load one plugin from one external file or one directory by declaring the ability to
 * handle it.
 *
 * <p>In order to load multiple files into a single plugin, group them into a directory tree and
 * then load the directory root as a single plugin.
 */
@ExtensionPoint
public interface ServerPluginProvider {

  /** Descriptor of the Plugin that ServerPluginProvider has to load. */
  class PluginDescription {
    public final PluginUser user;
    public final String canonicalUrl;
    public final Path dataDir;

    /**
     * Creates a new PluginDescription for ServerPluginProvider.
     *
     * @param user Gerrit user for interacting with plugins
     * @param canonicalUrl plugin root Web URL
     * @param dataDir directory for plugin data
     */
    public PluginDescription(PluginUser user, String canonicalUrl, Path dataDir) {
      this.user = user;
      this.canonicalUrl = canonicalUrl;
      this.dataDir = dataDir;
    }
  }

  /**
   * Declares the availability to manage an external file or directory
   *
   * @param srcPath the external file or directory
   * @return true if file or directory can be loaded into a Server Plugin
   */
  boolean handles(Path srcPath);

  /**
   * Returns the plugin name of an external file or directory
   *
   * <p>Should be called only if {@link #handles(Path) handles(srcFile)} returns true and thus
   * srcFile is a supported plugin format. An IllegalArgumentException is thrown otherwise as
   * srcFile is not a valid file format for extracting its plugin name.
   *
   * @param srcPath external file or directory
   * @return plugin name
   */
  String getPluginName(Path srcPath);

  /**
   * Loads an external file or directory into a Server plugin.
   *
   * <p>Should be called only if {@link #handles(Path) handles(srcFile)} returns true and thus
   * srcFile is a supported plugin format. An IllegalArgumentException is thrown otherwise as
   * srcFile is not a valid file format for extracting its plugin name.
   *
   * @param srcPath external file or directory
   * @param snapshot snapshot of the external file
   * @param pluginDescriptor descriptor of the ServerPlugin to load
   * @throws InvalidPluginException if plugin is supposed to be handled but cannot be loaded for any
   *     other reason
   */
  ServerPlugin get(Path srcPath, FileSnapshot snapshot, PluginDescription pluginDescriptor)
      throws InvalidPluginException;

  /**
   * Returns the plugin name of this provider.
   *
   * <p>Allows to identify which plugin provided the current ServerPluginProvider by returning the
   * plugin name. Helpful for troubleshooting plugin loading problems.
   *
   * @return plugin name of this provider
   */
  String getProviderPluginName();
}
