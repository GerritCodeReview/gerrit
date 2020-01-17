// Copyright (C) 2020 The Android Open Source Project
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

import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.server.config.GerritServerConfigModule;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.server.notedb.DeleteZombieCommentsRefs;
import com.google.gerrit.server.schema.SchemaModule;
import com.google.gerrit.server.securestore.SecureStoreClassName;
import com.google.gwtorm.server.OrmException;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Providers;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A pgm which can be used to clean zombie draft comments refs More context in
 * https://gerrit-review.googlesource.com/c/gerrit/+/246233
 *
 * <p>The implementation is in {@link DeleteZombieCommentsRefs}
 */
public class DeleteZombieDrafts extends SiteProgram {
  @Override
  public int run() throws IOException, OrmException {
    mustHaveValidSite();
    DeleteZombieCommentsRefs cleanup = getSysInjector().getInstance(DeleteZombieCommentsRefs.class);
    cleanup.execute();
    return 0;
  }

  private Injector getSysInjector() {
    List<Module> modules = new ArrayList<>();
    modules.add(
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(Path.class).annotatedWith(SitePath.class).toInstance(getSitePath());
            bind(ConsoleUI.class).toInstance(ConsoleUI.getInstance(false));
            bind(String.class)
                .annotatedWith(SecureStoreClassName.class)
                .toProvider(Providers.of(getConfiguredSecureStoreClass()));
          }
        });
    modules.add(new GerritServerConfigModule());
    modules.add(new SchemaModule());
    return Guice.createInjector(modules);
  }
}
