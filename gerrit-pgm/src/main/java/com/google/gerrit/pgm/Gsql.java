// Copyright (C) 2009 The Android Open Source Project
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
import com.google.gerrit.pgm.util.RuntimeShutdown;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.sshd.commands.QueryShell;
import com.google.gerrit.sshd.commands.QueryShell.Factory;
import com.google.inject.Injector;
import java.io.IOException;
import org.kohsuke.args4j.Option;

/** Run Gerrit's SQL query tool */
public class Gsql extends SiteProgram {
  private final LifecycleManager manager = new LifecycleManager();
  private Injector dbInjector;

  @Option(name = "--format", usage = "Set output format")
  private QueryShell.OutputFormat format = QueryShell.OutputFormat.PRETTY;

  @Option(name = "-c", metaVar = "SQL QUERY", usage = "Query to execute")
  private String query;

  @Override
  public int run() throws Exception {
    mustHaveValidSite();

    dbInjector = createDbInjector(SINGLE_USER);
    manager.add(dbInjector);
    manager.start();
    RuntimeShutdown.add(
        new Runnable() {
          @Override
          public void run() {
            try {
              System.in.close();
            } catch (IOException e) {
              // Ignored
            }
            manager.stop();
          }
        });
    final QueryShell shell = shellFactory().create(System.in, System.out);
    shell.setOutputFormat(format);
    if (query != null) {
      shell.execute(query);
    } else {
      shell.run();
    }
    return 0;
  }

  private Factory shellFactory() {
    return dbInjector
        .createChildInjector(
            new FactoryModule() {
              @Override
              protected void configure() {
                factory(QueryShell.Factory.class);
              }
            })
        .getInstance(QueryShell.Factory.class);
  }
}
