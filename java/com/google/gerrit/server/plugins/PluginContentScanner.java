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

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.nio.file.NoSuchFileException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Manifest;

/**
 * Scans the plugin returning classes and resources.
 *
 * <p>Gerrit uses the scanner to automatically discover the classes and resources exported by the
 * plugin for auto discovery of exported SSH commands, Servlets and listeners.
 */
public interface PluginContentScanner {

  /** Scanner without resources. */
  PluginContentScanner EMPTY =
      new PluginContentScanner() {
        @Override
        public Manifest getManifest() throws IOException {
          return new Manifest();
        }

        @Override
        public Map<Class<? extends Annotation>, Iterable<ExtensionMetaData>> scan(
            String pluginName, Iterable<Class<? extends Annotation>> annotations)
            throws InvalidPluginException {
          return Collections.emptyMap();
        }

        @Override
        public Optional<PluginEntry> getEntry(String resourcePath) {
          return Optional.empty();
        }

        @Override
        public InputStream getInputStream(PluginEntry entry) throws IOException {
          throw new NoSuchFileException("Empty plugin");
        }

        @Override
        public Enumeration<PluginEntry> entries() {
          return Collections.emptyEnumeration();
        }
      };

  /**
   * Plugin class extension meta-data
   *
   * <p>Class name and annotation value of the class provided by a plugin to extend an existing
   * extension point in Gerrit.
   */
  class ExtensionMetaData {
    public final String className;
    public final String annotationValue;

    public ExtensionMetaData(String className, String annotationValue) {
      this.className = className;
      this.annotationValue = annotationValue;
    }
  }

  /**
   * Return the plugin meta-data manifest
   *
   * @return Manifest of the plugin or null if plugin has no meta-data
   * @throws IOException if an I/O problem occurred whilst accessing the Manifest
   */
  Manifest getManifest() throws IOException;

  /**
   * Scans the plugin for declared public annotated classes
   *
   * @param pluginName the plugin name
   * @param annotations annotations declared by the plugin classes
   * @return map of annotations and associated plugin classes found
   * @throws InvalidPluginException if the plugin is not valid or corrupted
   */
  Map<Class<? extends Annotation>, Iterable<ExtensionMetaData>> scan(
      String pluginName, Iterable<Class<? extends Annotation>> annotations)
      throws InvalidPluginException;

  /**
   * Return the plugin resource associated to a path
   *
   * @param resourcePath full path of the resource inside the plugin package
   * @return the resource object or Optional.absent() if the resource was not found
   * @throws IOException if there was a problem retrieving the resource
   */
  Optional<PluginEntry> getEntry(String resourcePath) throws IOException;

  /**
   * Return the InputStream of the resource entry
   *
   * @param entry resource entry inside the plugin package
   * @return the resource input stream
   * @throws IOException if there was an I/O problem accessing the resource
   */
  InputStream getInputStream(PluginEntry entry) throws IOException;

  /**
   * Return all the resources inside a plugin
   *
   * @return the enumeration of all resources found
   */
  Enumeration<PluginEntry> entries();
}
