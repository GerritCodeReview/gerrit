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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.annotations.Export;
import com.google.gerrit.server.plugins.Plugin.ApiType;
import com.google.inject.Module;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Set;
import java.util.jar.Manifest;

/**
 * Base plugin scanner for a set of pre-loaded classes.
 *
 * Utility base class for simplifying the development of Server plugin scanner
 * based on a set of externally pre-loaded classes.
 *
 * Extending this class you can implement very easily a PluginContentScanner
 * from a set of pre-loaded Java Classes and an API Type.
 * The convention used by this class is:
 * - there is at most one Guice module per Gerrit module type (SysModule, HttpModule, SshModule)
 * - plugin is set to be restartable in Gerrit Plugin MANIFEST
 * - only Export and Listen annotated classes can be self-discovered
 */
public abstract class AbstractPreloadedPluginScanner implements PluginContentScanner {
  protected final String pluginName;
  protected final String pluginVersion;
  protected final Set<Class<?>> preloadedClasses;
  protected final ApiType apiType;

  private String sshModuleClassName;
  private String httpModuleClassName;
  private String sysModuleClassName;

  public AbstractPreloadedPluginScanner(String pluginName, String pluginVersion,
      Set<Class<?>> preloadedClasses, Plugin.ApiType apiType) {
    this.pluginName = pluginName;
    this.pluginVersion = pluginVersion;
    this.preloadedClasses = preloadedClasses;
    this.apiType = apiType;
  }

  @Override
  public Manifest getManifest() throws IOException {
    scanGuiceModules(preloadedClasses);
    StringBuilder manifestString =
        new StringBuilder("PluginName: " + pluginName + "\n"
            + "Implementation-Version: " + pluginVersion + "\n"
            + "Gerrit-ReloadMode: restart\n"
            + "Gerrit-ApiType: " + apiType + "\n");
    appendIfNotNull(manifestString, "Gerrit-Module: ", sshModuleClassName);
    appendIfNotNull(manifestString, "Gerrit-HttpModule: ", httpModuleClassName);
    appendIfNotNull(manifestString, "Gerrit-SshModule: ", sysModuleClassName);
    return new Manifest(new ByteArrayInputStream(manifestString.toString()
        .getBytes()));
  }

  @Override
  public Map<Class<? extends Annotation>, Iterable<ExtensionMetaData>> scan(
      String pluginName, Iterable<Class<? extends Annotation>> annotations)
      throws InvalidPluginException {
    ImmutableMap.Builder<Class<? extends Annotation>, Iterable<ExtensionMetaData>> result =
        ImmutableMap.builder();

    for (Class<? extends Annotation> annotation : annotations) {
      Set<ExtensionMetaData> classMetaDataSet = Sets.newHashSet();
      result.put(annotation, classMetaDataSet);

      for (Class<?> clazz : preloadedClasses) {
        if (!Modifier.isAbstract(clazz.getModifiers())
            && clazz.getAnnotation(annotation) != null) {
          classMetaDataSet.add(new ExtensionMetaData(clazz.getName(),
              getExportAnnotationValue(clazz, annotation)));
        }
      }
    }
    return result.build();
  }

  private void appendIfNotNull(StringBuilder string, String header, String body) {
    if (body != null) {
      string.append(header);
      string.append(body);
      string.append("\n");
    }
  }

  private void scanGuiceModules(Set<Class<?>> scriptClasses) throws IOException {
    try {
      Class<?> sysModuleBaseClass = Module.class;
      Class<?> httpModuleBaseClass =
          Class.forName("com.google.inject.servlet.ServletModule");
      Class<?> sshModuleBaseClass =
          Class.forName("com.google.gerrit.sshd.CommandModule");

      for (Class<?> scriptClass : scriptClasses) {
        String scriptClassName = scriptClass.getName();

        if (sshModuleBaseClass.isAssignableFrom(scriptClass)) {
          sshModuleClassName = scriptClassName;
        } else if (httpModuleBaseClass.isAssignableFrom(scriptClass)) {
          httpModuleClassName = scriptClassName;;
        } else if (sysModuleBaseClass.isAssignableFrom(scriptClass)) {
          sysModuleClassName = scriptClassName;;
        }
      }
    } catch (ClassNotFoundException e) {
      throw new IOException(
          "Cannot find base Gerrit classes for Guice Plugin Modules", e);
    }
  }

  private String getExportAnnotationValue(Class<?> scriptClass,
      Class<? extends Annotation> annotation) {
    return annotation == Export.class ? scriptClass.getAnnotation(Export.class)
        .value() : "";
  }
}
