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
 */
public abstract class AbstractPreloadedPluginScanner implements PluginContentScanner {
  protected final String pluginName;
  protected final String pluginVersion;
  protected final Set<Class<?>> preloadedClasses;
  protected final ApiType apiType;
  protected Class<? extends Module> sshModule;
  protected Class<? extends Module> httpModule;
  protected Class<? extends Module> sysModule;

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
    String manifestString =
        "PluginName: " + pluginName + "\n"
            + "Implementation-Version: " + pluginVersion + "\n"
            + "Gerrit-ReloadMode: restart\n"
            + (sysModule == null ? "" : ("Gerrit-Module: " + sysModule.getName() + "\n"))
            + (httpModule == null ? "" : ("Gerrit-HttpModule: " + httpModule.getName() + " \n"))
            + (sshModule == null ? "" : ("Gerrit-SshModule: " + sshModule.getName() + "\n")) + "Gerrit-ApiType: " + apiType
            + "\n";
    return new Manifest(new ByteArrayInputStream(manifestString.getBytes()));
  }

  @SuppressWarnings("unchecked")
  private void scanGuiceModules(Set<Class<?>> scriptClasses) throws IOException {
    try {
      Class<?> sysModuleBaseClass = Module.class;
      Class<?> httpModuleBaseClass =
          Class.forName("com.google.inject.servlet.ServletModule");
      Class<?> sshModuleBaseClass =
          Class.forName("com.google.gerrit.sshd.CommandModule");

      for (Class<?> scriptClass : scriptClasses) {
        if (sshModuleBaseClass.isAssignableFrom(scriptClass)) {
          sshModule = ((Class<? extends Module>) scriptClass);
        } else if (httpModuleBaseClass.isAssignableFrom(scriptClass)) {
          httpModule = ((Class<? extends Module>) scriptClass);
        } else if (sysModuleBaseClass.isAssignableFrom(scriptClass)) {
          sysModule = ((Class<? extends Module>) scriptClass);
        }
      }
    } catch (ClassNotFoundException e) {
      throw new IOException(
          "Cannot find base Gerrit classes for Guice Plugin Modules", e);
    }
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

      for (Class<?> scriptClass : preloadedClasses) {
        if (!Modifier.isAbstract(scriptClass.getModifiers())
            && scriptClass.getAnnotation(annotation) != null) {
          classMetaDataSet.add(new ExtensionMetaData(scriptClass.getName(),
              getExportAnnotationValue(scriptClass, annotation)));
        }
      }
    }
    return result.build();
  }

  private String getExportAnnotationValue(Class<?> scriptClass,
      Class<? extends Annotation> annotation) {
    return annotation == Export.class ? scriptClass.getAnnotation(Export.class)
        .value() : "";
  }
}
