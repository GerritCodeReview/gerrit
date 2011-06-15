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

import static com.google.gerrit.server.schema.DataSourceProvider.Context.MULTI_USER;

import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.pgm.util.PrologJar;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Injector;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Option;

/**
 * Gets rules.pl at refs/meta/config and compiles into jar file called
 * rules-(sha1 of rules.pl).jar in (site-path)/cache/rules
 */
public class Rulec extends SiteProgram {
  @Option(name = "--name", required = true, metaVar = "PROJECT",
      usage = "project to compile rules for")
  private String projectName;

  private Injector dbInjector;
  private final LifecycleManager manager = new LifecycleManager();

  @Inject
  private GitRepositoryManager gitManager;

  @Inject
  @GerritServerConfig
  private Config config;

  @Inject
  private SitePaths site;

  @Override
  public int run() throws Exception {
    dbInjector = createDbInjector(MULTI_USER);
    manager.add(dbInjector);
    manager.start();
    dbInjector.injectMembers(this);

    Project.NameKey project = new Project.NameKey(projectName);
    Repository git = gitManager.openRepository(project);

    try {
      PrologJar jarMaker = new PrologJar(config, site, git);
      return jarMaker.run() ? 0 : 1;
    } finally {
      git.close();
    }
  }
}