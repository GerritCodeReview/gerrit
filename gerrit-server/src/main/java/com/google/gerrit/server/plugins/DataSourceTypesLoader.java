// Copyright (C) 2013 The Android Open Source Project
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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.schema.DataSourceType;
import com.google.gwtorm.schema.sql.SqlDialect;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Names;

import java.io.File;
import java.io.FileFilter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

@Singleton
public class DataSourceTypesLoader {
  private final File pluginsDir;

  private boolean loaded;

  private final List<SqlDialect> dialects = Lists.newArrayList();
  private final Map<String, Class<? extends DataSourceType>> types =
      Maps.newHashMap();

  @Inject
  public DataSourceTypesLoader(final SitePaths sitePaths) {
    this.pluginsDir = sitePaths.plugins_dir;
  }

  public void registerSqlDialects() {
    load();
    for (SqlDialect d : dialects) {
      SqlDialect.register(d);
    }
  }

  public Module bindDataSourceTypes() {
    load();
    return new AbstractModule() {
      @Override
      protected void configure() {
        for (Map.Entry<String, Class<? extends DataSourceType>> e
            : types.entrySet()) {
          bind(DataSourceType.class).annotatedWith(Names.named(e.getKey()))
              .to(e.getValue());
        }
      }
    };
  }

  private void load() {
    if (loaded) {
      return;
    }

    for (File jar : scanJarsInPluginsDirectory()) {
      load(jar);
    }

    loaded = true;
  }

  private void load(File jar) {
    try {
      ClassLoader pluginLoader =
          new URLClassLoader(new URL[] {jar.toURI().toURL()},
              DataSourceTypesLoader.class.getClassLoader());
      JarFile jarFile = new JarFile(jar);
      Attributes jarFileAttributes = jarFile.getManifest().getMainAttributes();
      String dialectClassName = jarFileAttributes.getValue("Gerrit-SqlDialect");
      String dstName = jarFileAttributes.getValue("Gerrit-DataSourceTypeName");
      String dstClassName = jarFileAttributes.getValue("Gerrit-DataSourceType");

      if (dialectClassName != null) {
        @SuppressWarnings("unchecked")
        Class<? extends SqlDialect> dialectClass =
            (Class<? extends SqlDialect>) pluginLoader.loadClass(dialectClassName);
        dialects.add(dialectClass.newInstance());
      }

      if (dstName != null && dstClassName != null) {
        @SuppressWarnings("unchecked")
        Class<? extends DataSourceType> dstClass =
            (Class<? extends DataSourceType>) pluginLoader.loadClass(dstClassName);
        types.put(dstName, dstClass);
      }
    } catch (ClassCastException e) {
      throw new RuntimeException(e);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private List<File> scanJarsInPluginsDirectory() {
    if (pluginsDir == null || !pluginsDir.exists()) {
      return Collections.emptyList();
    }
    File[] matches = pluginsDir.listFiles(new FileFilter() {
      @Override
      public boolean accept(File pathname) {
        String n = pathname.getName();
        return (n.endsWith(".jar") && pathname.isFile());
      }
    });
    if (matches == null) {
      return Collections.emptyList();
    }
    Arrays.sort(matches, new Comparator<File>() {
      @Override
      public int compare(File o1, File o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
    return Arrays.asList(matches);
  }
}
