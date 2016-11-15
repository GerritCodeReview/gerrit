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
import com.google.gerrit.server.git.GitRepositoryManagerModule;
import com.google.inject.Inject;
import com.google.inject.Injector;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

public class LibModuleLoader {
  private static final Logger log = LoggerFactory
      .getLogger(LibModuleLoader.class);

  static final String SECTION_NAME = "libModule";
  static final String CLASS_NAME = "className";

  private final Config config;
  private final Injector injector;

  @Inject
  LibModuleLoader(@GerritServerConfig Config config, Injector injector) {
    this.config = config;
    this.injector = injector;
  }

  public static Injector createChildInjector(Injector parentInjector) {
    LibModuleLoader moduleLoader =
        parentInjector.getInstance(LibModuleLoader.class);
    return parentInjector.createChildInjector(moduleLoader.modules());
  }

  public static List<com.google.inject.Module> modules(Injector parentInjector) {
    return parentInjector.getInstance(LibModuleLoader.class).modules();
  }

  public List<com.google.inject.Module> modules() {
    return Arrays.stream(config.getStringList(SECTION_NAME, null, CLASS_NAME))
        .map(this::loadModule).collect(toList());
  }

  @SuppressWarnings("unchecked")
  public com.google.inject.Module loadModule(String className) {
    try {
      Class<? extends com.google.inject.Module> moduleClass =
          className == null ? GitRepositoryManagerModule.class
              : (Class<? extends com.google.inject.Module>) Class
                  .forName(className);

      log.info("LibModule {} loaded", className, moduleClass);
      return injector.getInstance(moduleClass);
    } catch (ClassNotFoundException e) {
      log.error("Unable to load GitRepositoryModule", e);
      throw new RuntimeException(e);
    }
  }
}
