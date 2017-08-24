// Copyright (C) 2011 The Android Open Source Project
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

import static com.google.gerrit.server.schema.DataSourceProvider.Context.SINGLE_USER;

import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.pgm.rules.PrologCompiler;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.googlecode.prolog_cafe.exceptions.CompileException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

/**
 * Gets rules.pl at refs/meta/config and compiles into jar file called rules-(sha1 of rules.pl).jar
 * in (site-path)/cache/rules
 */
public class Rulec extends SiteProgram {
  @Option(name = "--all", usage = "recompile all rules")
  private boolean all;

  @Option(name = "--quiet", usage = "suppress some messages")
  private boolean quiet;

  @Argument(
    index = 0,
    multiValued = true,
    metaVar = "PROJECT",
    usage = "project to compile rules for"
  )
  private List<String> projectNames = new ArrayList<>();

  private Injector dbInjector;

  private final LifecycleManager manager = new LifecycleManager();

  @Inject private GitRepositoryManager gitManager;

  @Inject private PrologCompiler.Factory jarFactory;

  @Override
  public int run() throws Exception {
    dbInjector = createDbInjector(SINGLE_USER);
    manager.add(dbInjector);
    manager.start();
    dbInjector
        .createChildInjector(
            new FactoryModule() {
              @Override
              protected void configure() {
                factory(PrologCompiler.Factory.class);
              }
            })
        .injectMembers(this);

    LinkedHashSet<Project.NameKey> names = new LinkedHashSet<>();
    for (String name : projectNames) {
      names.add(new Project.NameKey(name));
    }
    if (all) {
      names.addAll(gitManager.list());
    }

    boolean error = false;
    for (Project.NameKey project : names) {
      try (Repository git = gitManager.openRepository(project)) {
        switch (jarFactory.create(git).call()) {
          case NO_RULES:
            if (!all || projectNames.contains(project.get())) {
              System.err.println("error: No rules.pl in " + project.get());
              error = true;
            }
            break;

          case COMPILED:
            if (!quiet) {
              System.out.format("Compiled %-60s ... SUCCESS", project.get());
              System.out.println();
            }
            break;
        }
      } catch (CompileException err) {
        if (showStackTrace) {
          err.printStackTrace();
        } else {
          System.err.println("fatal: " + err.getMessage());
        }
        error = true;
      }
    }

    return !error ? 0 : 1;
  }
}
