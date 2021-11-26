// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server;

import static java.util.stream.Collectors.toList;

import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.index.IndexType;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.index.AbstractIndexModule;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.ProvisionException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;

/** Loads configured Guice modules from {@code gerrit.installModule}. */
public class LibModuleLoader {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String FAKE_INDEX_MODULE_CLASS_NAME =
      "com.google.gerrit.index.testing.FakeIndexModule";
  public static final String LUCENE_INDEX_MODULE_CLASS_NAME =
      "com.google.gerrit.lucene.LuceneIndexModule";
  public static final String INDEX_MODULE_SYS_PROP = "gerrit.index.module";
  public static final String INDEX_MODULE_ENV_VAR = "GERRIT_INDEX_MODULE";

  public static Optional<AbstractIndexModule> fromEnvironment(
      Map<String, Integer> versions, int threads, boolean replica) {

    String indexModuleClassName;
    if (!Strings.isNullOrEmpty(System.getenv(INDEX_MODULE_ENV_VAR))) {
      indexModuleClassName = System.getenv(INDEX_MODULE_ENV_VAR);
    } else if (!Strings.isNullOrEmpty(System.getProperty(INDEX_MODULE_SYS_PROP))) {
      indexModuleClassName = System.getProperty(INDEX_MODULE_SYS_PROP);
    } else {
      IndexType indexType = IndexType.fromEnvironment().orElse(new IndexType("fake"));
      if (indexType.isLucene()) {
        indexModuleClassName = LUCENE_INDEX_MODULE_CLASS_NAME;
      } else {
        indexModuleClassName = FAKE_INDEX_MODULE_CLASS_NAME;
      }
    }
    return Optional.of(createIndexModule(indexModuleClassName, versions, threads, replica));
  }

  public static List<Module> loadModules(Injector parent, LibModuleType moduleType) {
    Config cfg = getConfig(parent);
    return Arrays.stream(cfg.getStringList("gerrit", null, "install" + moduleType.getConfigKey()))
        .map(m -> createModule(parent, m))
        .collect(toList());
  }

  public static Optional<AbstractIndexModule> loadIndexModule(
      Injector parent, Map<String, Integer> versions, int threads, boolean replica) {
    Config cfg = getConfig(parent);
    return Arrays.stream(
            cfg.getStringList(
                "gerrit", null, "install" + LibModuleType.INDEX_MODULE_TYPE.getConfigKey()))
        .map(m -> createIndexModule(m, versions, threads, replica))
        .findFirst();
  }

  public static AbstractIndexModule createIndexModule(
      String className, Map<String, Integer> versions, int threads, boolean replica) {
    Class<Module> clazz = loadModule(className);
    try {

      Method m =
          clazz.getMethod("singleVersionWithExplicitVersions", Map.class, int.class, boolean.class);

      AbstractIndexModule module = (AbstractIndexModule) m.invoke(null, versions, threads, replica);
      logger.atInfo().log("Installed module %s", className);
      return module;
    } catch (Exception e) {
      logger.atSevere().withCause(e).log("Unable to load libModule for %s", className);
      throw new IllegalStateException(e);
    }
  }

  private static Config getConfig(Injector i) {
    return i.getInstance(Key.get(Config.class, GerritServerConfig.class));
  }

  private static Module createModule(Injector injector, String className) {
    Module m = injector.getInstance(loadModule(className));
    logger.atInfo().log("Installed module %s", className);
    return m;
  }

  @SuppressWarnings("unchecked")
  private static Class<Module> loadModule(String className) {
    try {
      return (Class<Module>) Class.forName(className);
    } catch (ClassNotFoundException | LinkageError e) {
      String msg = "Cannot load LibModule " + className;
      logger.atSevere().withCause(e).log(msg);
      throw new ProvisionException(msg, e);
    }
  }
}
