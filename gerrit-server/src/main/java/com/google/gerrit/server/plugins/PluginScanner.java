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

import com.google.gerrit.server.plugins.JarScanner.ExtensionMetaData;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.util.Enumeration;
import java.util.Map;
import java.util.jar.Manifest;

/**
 * Scans the plugin returning classes and resources.
 *
 * Gerrit uses the scanner to automatically discover the classes
 * and resources exported by the plugin for auto discovery
 * of exported SSH commands, Servlets and listeners.
 */
public interface PluginScanner {
  /**
   * Return the plugin meta-data manifest
   *
   * @return Manifest of the plugin or null if plugin as no meta-data
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
   * Return the  plugin typed resource associated to a path
   *
   * @param resourcePath full path of the resource inside the plugin package
   * @param resourceClass expected Java class of the resource
   * @return the resource object or null if the resource was not found
   */
  <T> T getResource(String resourcePath, Class<? extends T> resourceClass);

  /**
   * Return the InputStream of the resource associated to a path
   * @param resourcePath full path of the resource inside the plugin package
   * @return the resource input stream or null if the resource was not found
   * @throws IOException if there was an I/O problem accessing the resource
   */
  InputStream getResourceInputStream(String resourcePath) throws IOException;

  /**
   * Return all the resources that are matching an expected Java class
   * @param resourceClass expected Java class
   * @return the enumeration of all resources found
   */
  <T> Enumeration<T>  resources(Class<? extends T> resourceClass);
}
