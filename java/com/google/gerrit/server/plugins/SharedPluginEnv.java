// Copyright (C) 2021 The Android Open Source Project
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

import com.google.inject.Inject;
import com.google.inject.Injector;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

public class SharedPluginEnv {
  public static class NoSuchPluginException extends Exception {
    private static final long serialVersionUID = 1L;

    public NoSuchPluginException(String msg) {
      super(msg);
    }
  }

  protected static class Properties {
    protected List<String> dependentPluginNames;
    protected Set<String> excludedClassNamePatterns;
    protected String injectorFromPlugin;
    protected boolean isTargetPluginADependency;
    protected boolean isInstantiatingClassExcluded;
    protected String targetPluginName;
    protected boolean useTargetPluginInjector;

    protected Properties() {
      dependentPluginNames = new ArrayList<>();
      excludedClassNamePatterns = new HashSet<>();
      isTargetPluginADependency = true;
      isInstantiatingClassExcluded = true;
      useTargetPluginInjector = true;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      try {
        for (Field field : getClass().getDeclaredFields()) {
          field.setAccessible(true);
          if (!Objects.deepEquals(field.get(this), field.get(o))) {
            return false;
          }
        }
      } catch (IllegalArgumentException | IllegalAccessException e) {
        throw new RuntimeException(e);
      }
      return true;
    }

    @Override
    public int hashCode() {
      List<Object> values = new ArrayList<>();
      try {
        for (Field field : getClass().getDeclaredFields()) {
          field.setAccessible(true);
          values.add(field.get(this));
        }
      } catch (IllegalArgumentException | IllegalAccessException e) {
        throw new RuntimeException(e);
      }
      return Objects.hash(values);
    }
  }

  protected static Map<Properties, Map<ClassLoader, WeakReference<ClassLoader>>>
      mergedClByProperties =
          Collections.synchronizedMap(
              new WeakHashMap<Properties, Map<ClassLoader, WeakReference<ClassLoader>>>());

  protected PluginLoader pluginLoader;
  protected Properties properties;

  @Inject
  public SharedPluginEnv(PluginLoader pluginLoader) {
    this.pluginLoader = pluginLoader;
    properties = new Properties();
  }

  public Object instantiate(String classNameToInstantiate)
      throws ClassNotFoundException, NoSuchPluginException {
    if (properties.isInstantiatingClassExcluded) {
      properties.excludedClassNamePatterns.add(classNameToInstantiate + ".*");
    }
    if (properties.targetPluginName == null) {
      throw new IllegalArgumentException("Target plugin name is not set");
    }

    return getInjector()
        .getInstance(
            getMergedClassLoader(
                    getPluginClassLoader(properties.targetPluginName),
                    getDependentPluginClassLoaders())
                .loadClass(classNameToInstantiate));
  }

  protected Injector getInjector() {
    if (properties.useTargetPluginInjector) {
      properties.injectorFromPlugin = properties.targetPluginName;
    }
    return pluginLoader.get(properties.injectorFromPlugin).getSysInjector();
  }

  public SharedPluginEnv setTargetPluginAsDependency(boolean isTargetPluginADependency) {
    properties.isTargetPluginADependency = isTargetPluginADependency;
    return this;
  }

  public SharedPluginEnv setIsInstantiatingClassExcluded(boolean isInstantiatingClassExcluded) {
    properties.isInstantiatingClassExcluded = isInstantiatingClassExcluded;
    return this;
  }

  public SharedPluginEnv setUseTargetPluginInjector(boolean useTargetPluginInjector) {
    properties.useTargetPluginInjector = useTargetPluginInjector;
    return this;
  }

  public SharedPluginEnv addExcludedClassNamePattern(String excludedClassNamePattern) {
    properties.excludedClassNamePatterns.add(excludedClassNamePattern);
    return this;
  }

  public SharedPluginEnv setTargetPluginName(String pluginName) {
    properties.targetPluginName = pluginName;
    return this;
  }

  public SharedPluginEnv addDependentPlugin(String pluginName) {
    properties.dependentPluginNames.add(pluginName);
    return this;
  }

  public SharedPluginEnv useInjectorFromPlugin(String injectorFromPlugin) {
    properties.injectorFromPlugin = injectorFromPlugin;
    return this;
  }

  protected ClassLoader getPluginClassLoader(String pluginName) throws NoSuchPluginException {
    Plugin plugin = pluginLoader.get(pluginName);
    if (plugin instanceof ServerPlugin) {
      return ((ServerPlugin) plugin).getClassLoader();
    }
    throw new NoSuchPluginException(String.format("Jar Plugin '%s' is not installed", pluginName));
  }

  protected List<ClassLoader> getDependentPluginClassLoaders() throws NoSuchPluginException {
    List<ClassLoader> cls = new ArrayList<>();
    if (properties.isTargetPluginADependency) {
      cls.add(getPluginClassLoader(properties.targetPluginName));
    }
    for (String pluginName : properties.dependentPluginNames) {
      cls.add(getPluginClassLoader(pluginName));
    }
    return cls;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  protected ClassLoader getMergedClassLoader(ClassLoader targetCl, List<ClassLoader> dependentCls)
      throws NoSuchPluginException {
    Map<ClassLoader, WeakReference<ClassLoader>> mergedClByCl =
        mergedClByProperties.get(properties);
    if (mergedClByCl == null) {
      mergedClByCl =
          Collections.synchronizedMap(new WeakHashMap<ClassLoader, WeakReference<ClassLoader>>());
      mergedClByProperties.put(properties, mergedClByCl);
    }
    WeakReference<ClassLoader> mergedClRef = mergedClByCl.get(targetCl);
    ClassLoader mergedCl = null;
    if (mergedClRef != null) {
      mergedCl = mergedClRef.get();
    }
    if (mergedCl == null) {
      MultiParentClassLoader parents =
          new MultiParentClassLoader(properties.excludedClassNamePatterns, dependentCls);
      mergedCl = new DelegatingClassLoader(parents, targetCl);
      mergedClByCl.put(targetCl, new WeakReference(mergedCl));
    }
    return mergedCl;
  }
}
