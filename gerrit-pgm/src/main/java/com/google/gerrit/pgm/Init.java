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
import static com.google.inject.Stage.PRODUCTION;

import com.google.gerrit.common.PageLinks;
import com.google.gerrit.pgm.init.Browser;
import com.google.gerrit.pgm.init.InitFlags;
import com.google.gerrit.pgm.init.InitModule;
import com.google.gerrit.pgm.init.ReloadSiteLibrary;
import com.google.gerrit.pgm.init.SitePathInitializer;
import com.google.gerrit.pgm.util.ConsoleUI;
import com.google.gerrit.pgm.util.Die;
import com.google.gerrit.pgm.util.ErrorLogFile;
import com.google.gerrit.pgm.util.IoUtil;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.schema.SchemaUpdater;
import com.google.gerrit.server.schema.UpdateUI;
import com.google.gerrit.server.util.HostPlatform;
import com.google.gwtorm.jdbc.JdbcExecutor;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.gwtorm.server.StatementExecutor;
import com.google.inject.AbstractModule;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.spi.Message;

import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Initialize a new Gerrit installation. */
public class Init extends SiteProgram {
  @Option(name = "--batch", usage = "Batch mode; skip interactive prompting")
  private boolean batchMode;

  @Option(name = "--no-auto-start", usage = "Don't automatically start daemon after init")
  private boolean noAutoStart;

  @Override
  public int run() throws Exception {
    ErrorLogFile.errorOnlyConsole();

    final SiteInit init = createSiteInit();
    init.flags.autoStart = !noAutoStart && init.site.isNew;

    final SiteRun run;
    try {
      init.initializer.run();
      init.flags.deleteOnFailure = false;

      run = createSiteRun(init);
      run.upgradeSchema();
    } catch (Exception failure) {
      if (init.flags.deleteOnFailure) {
        recursiveDelete(getSitePath());
      }
      throw failure;
    } catch (Error failure) {
      if (init.flags.deleteOnFailure) {
        recursiveDelete(getSitePath());
      }
      throw failure;
    }

    System.err.println("Initialized " + getSitePath().getCanonicalPath());
    run.start();
    return 0;
  }

  static class SiteInit {
    final SitePaths site;
    final InitFlags flags;
    final ConsoleUI ui;
    final SitePathInitializer initializer;

    @Inject
    SiteInit(final SitePaths site, final InitFlags flags, final ConsoleUI ui,
        final SitePathInitializer initializer) {
      this.site = site;
      this.flags = flags;
      this.ui = ui;
      this.initializer = initializer;
    }
  }

  private SiteInit createSiteInit() {
    final ConsoleUI ui = ConsoleUI.getInstance(batchMode);
    final File sitePath = getSitePath();
    final List<Module> m = new ArrayList<Module>();

    m.add(new InitModule());
    m.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(ConsoleUI.class).toInstance(ui);
        bind(File.class).annotatedWith(SitePath.class).toInstance(sitePath);
        bind(ReloadSiteLibrary.class).toInstance(new ReloadSiteLibrary() {
          @Override
          public void reload() {
            Init.super.loadSiteLib();
          }
        });
      }
    });

    try {
      return Guice.createInjector(PRODUCTION, m).getInstance(SiteInit.class);
    } catch (CreationException ce) {
      final Message first = ce.getErrorMessages().iterator().next();
      Throwable why = first.getCause();

      if (why instanceof Die) {
        throw (Die) why;
      }

      final StringBuilder buf = new StringBuilder(ce.getMessage());
      while (why != null) {
        buf.append("\n");
        buf.append(why.getMessage());
        why = why.getCause();
        if (why != null) {
          buf.append("\n  caused by ");
        }
      }
      throw die(buf.toString(), new RuntimeException("InitInjector failed", ce));
    }
  }

  static class SiteRun {
    final ConsoleUI ui;
    final SitePaths site;
    final InitFlags flags;
    final SchemaUpdater schemaUpdater;
    final SchemaFactory<ReviewDb> schema;
    final GitRepositoryManager repositoryManager;
    final Browser browser;

    @Inject
    SiteRun(final ConsoleUI ui, final SitePaths site, final InitFlags flags,
        final SchemaUpdater schemaUpdater,
        final SchemaFactory<ReviewDb> schema,
        final GitRepositoryManager repositoryManager,
        final Browser browser) {
      this.ui = ui;
      this.site = site;
      this.flags = flags;
      this.schemaUpdater = schemaUpdater;
      this.schema = schema;
      this.repositoryManager = repositoryManager;
      this.browser = browser;
    }

    void upgradeSchema() throws OrmException {
      final List<String> pruneList = new ArrayList<String>();
      schemaUpdater.update(new UpdateUI() {
        @Override
        public void message(String msg) {
          System.err.println(msg);
          System.err.flush();
        }

        @Override
        public boolean yesno(boolean def, String msg) {
          return ui.yesno(def, msg);
        }

        @Override
        public boolean isBatch() {
          return ui.isBatch();
        }

        @Override
        public void pruneSchema(StatementExecutor e, List<String> prune) {
          for (String p : prune) {
            if (!pruneList.contains(p)) {
              pruneList.add(p);
            }
          }
        }
      });

      if (!pruneList.isEmpty()) {
        StringBuilder msg = new StringBuilder();
        msg.append("Execute the following SQL to drop unused objects:\n");
        msg.append("\n");
        for (String sql : pruneList) {
          msg.append("  ");
          msg.append(sql);
          msg.append(";\n");
        }

        if (ui.isBatch()) {
          System.err.print(msg);
          System.err.flush();

        } else if (ui.yesno(true, "%s\nExecute now", msg)) {
          final JdbcSchema db = (JdbcSchema) schema.open();
          try {
            final JdbcExecutor e = new JdbcExecutor(db);
            try {
              for (String sql : pruneList) {
                e.execute(sql);
              }
            } finally {
              e.close();
            }
          } finally {
            db.close();
          }
        }
      }
    }

    void start() throws Exception {
      if (flags.autoStart) {
        if (HostPlatform.isWin32()) {
          System.err.println("Automatic startup not supported on Win32.");

        } else {
          startDaemon();
          if (!ui.isBatch()) {
            browser.open(PageLinks.ADMIN_PROJECTS);
          }
        }
      }
    }

    void startDaemon() {
      final String[] argv = {site.gerrit_sh.getAbsolutePath(), "start"};
      final Process proc;
      try {
        System.err.println("Executing " + argv[0] + " " + argv[1]);
        proc = Runtime.getRuntime().exec(argv);
      } catch (IOException e) {
        System.err.println("error: cannot start Gerrit: " + e.getMessage());
        return;
      }

      try {
        proc.getOutputStream().close();
      } catch (IOException e) {
      }

      IoUtil.copyWithThread(proc.getInputStream(), System.err);
      IoUtil.copyWithThread(proc.getErrorStream(), System.err);

      for (;;) {
        try {
          final int rc = proc.waitFor();
          if (rc != 0) {
            System.err.println("error: cannot start Gerrit: exit status " + rc);
          }
          break;
        } catch (InterruptedException e) {
          // retry
        }
      }
    }

  }

  private SiteRun createSiteRun(final SiteInit init) {
    return createSysInjector(init).getInstance(SiteRun.class);
  }

  private Injector createSysInjector(final SiteInit init) {
    final List<Module> modules = new ArrayList<Module>();
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(ConsoleUI.class).toInstance(init.ui);
        bind(InitFlags.class).toInstance(init.flags);
      }
    });
    return createDbInjector(SINGLE_USER).createChildInjector(modules);
  }

  private static void recursiveDelete(File path) {
    File[] entries = path.listFiles();
    if (entries != null) {
      for (File e : entries) {
        recursiveDelete(e);
      }
    }
    if (!path.delete() && path.exists()) {
      System.err.println("warn: Cannot remove " + path);
    }
  }
}
