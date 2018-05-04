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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.ProvisionException;
import java.util.Arrays;
import java.util.List;
import org.eclipse.jgit.lib.Config;

/** Loads configured Guice modules from {@code gerrit.installModule}. */
public class LibModuleLoader {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static List<Module> loadModules(Injector parent) {
    Config cfg = getConfig(parent);
    return Arrays.stream(cfg.getStringList("gerrit", null, "installModule"))
        .map(m -> createModule(parent, m))
        .collect(toList());
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
