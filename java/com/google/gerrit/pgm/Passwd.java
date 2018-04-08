// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.pgm;

import com.google.common.base.Splitter;
import com.google.gerrit.config.SitePath;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InstallAllPlugins;
import com.google.gerrit.pgm.init.api.InstallPlugins;
import com.google.gerrit.pgm.init.api.Section;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.server.config.GerritServerConfigModule;
import com.google.gerrit.server.securestore.SecureStoreClassName;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Providers;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.args4j.Argument;

public class Passwd extends SiteProgram {
  private String section;
  private String key;

  @Argument(
    metaVar = "SECTION.KEY",
    index = 0,
    required = true,
    usage = "Section and key separated by a dot of the password to set"
  )
  private String sectionAndKey;

  @Argument(metaVar = "PASSWORD", index = 1, required = false, usage = "Password to set")
  private String password;

  private void init() {
    List<String> varParts = Splitter.on('.').splitToList(sectionAndKey);
    if (varParts.size() != 2) {
      throw new IllegalArgumentException(
          "Invalid name '" + sectionAndKey + "': expected section.key format");
    }
    section = varParts.get(0);
    key = varParts.get(1);
  }

  @Override
  public int run() throws Exception {
    init();
    SetPasswd setPasswd = getSysInjector().getInstance(SetPasswd.class);
    setPasswd.run(section, key, password);
    return 0;
  }

  private Injector getSysInjector() {
    List<Module> modules = new ArrayList<>();
    modules.add(
        new FactoryModule() {
          @Override
          protected void configure() {
            bind(Path.class).annotatedWith(SitePath.class).toInstance(getSitePath());
            bind(ConsoleUI.class).toInstance(ConsoleUI.getInstance(password != null));
            factory(Section.Factory.class);
            bind(Boolean.class).annotatedWith(InstallAllPlugins.class).toInstance(Boolean.FALSE);
            bind(new TypeLiteral<List<String>>() {})
                .annotatedWith(InstallPlugins.class)
                .toInstance(new ArrayList<String>());
            bind(String.class)
                .annotatedWith(SecureStoreClassName.class)
                .toProvider(Providers.of(getConfiguredSecureStoreClass()));
          }
        });
    modules.add(new GerritServerConfigModule());
    return Guice.createInjector(modules);
  }
}
