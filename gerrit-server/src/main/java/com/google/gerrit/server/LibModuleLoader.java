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

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

public class LibModuleLoader {
  private static final Logger log = LoggerFactory
      .getLogger(LibModuleLoader.class);

  private final Config config;
  private final Injector configInjector;

  public static List<com.google.inject.Module> loadModules(
      Injector configInjector) {
    return configInjector.getInstance(LibModuleLoader.class).loadModules();
  }

  @Inject
  LibModuleLoader(@GerritServerConfig Config config, Injector configInjector) {
    this.config = config;
    this.configInjector = configInjector;
  }

  private List<com.google.inject.Module> loadModules() {
    return Arrays.stream(config.getStringList("gerrit", null, "installModule"))
        .map(this::loadModule).collect(toList());
  }

  @SuppressWarnings("unchecked")
  private Module loadModule(String className) {
    Module libModule = null;
    try {
      Class<Module> moduleClass = (Class<Module>) Class.forName(className);
      libModule = configInjector.getInstance(moduleClass);
      log.info("Installed {}", className);
      return libModule;
    } catch (ClassNotFoundException e) {
      log.error("Unable to load LibModule " + className, e);
      throw new RuntimeException(e);
    }
  }
}
