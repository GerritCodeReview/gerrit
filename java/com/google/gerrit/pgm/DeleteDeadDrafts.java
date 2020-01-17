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

import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InstallAllPlugins;
import com.google.gerrit.pgm.init.api.InstallPlugins;
import com.google.gerrit.pgm.init.api.Section;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.server.config.GerritServerConfigModule;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.DeleteDeadDraftCommentsRefs;
import com.google.gerrit.server.securestore.SecureStoreClassName;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Providers;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;

/**
 * A pgm which can be used to clean zombie draft comments refs More context in
 * https://gerrit-review.googlesource.com/c/gerrit/+/246233
 *
 * <p>The implementation is in {@link DeleteDeadDraftCommentsRefs}
 */
public class DeleteDeadDrafts extends SiteProgram {
  private final String ALL_USERS = "All-Users";

  @Override
  public int run() throws IOException, OrmException {
    mustHaveValidSite();
    Injector sysInjector = getSysInjector();
    GitRepositoryManager gitRepoManager = sysInjector.getInstance(GitRepositoryManager.class);
    Optional<NameKey> allUsersKey =
        gitRepoManager.list().stream()
            .filter(project -> project.get().equals(ALL_USERS))
            .findFirst();
    if (!allUsersKey.isPresent()) {
      throw new RepositoryNotFoundException("All-Users repository not found");
    }
    Repository usersRepo = gitRepoManager.openRepository(allUsersKey.get());
    DeleteDeadDraftCommentsRefs cleanup = new DeleteDeadDraftCommentsRefs(usersRepo);
    cleanup.execute();
    return 0;
  }

  private Injector getSysInjector() {
    List<Module> modules = new ArrayList<>();
    modules.add(
        new FactoryModule() {
          @Override
          protected void configure() {
            bind(Path.class).annotatedWith(SitePath.class).toInstance(getSitePath());
            bind(ConsoleUI.class).toInstance(ConsoleUI.getInstance(false));
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
