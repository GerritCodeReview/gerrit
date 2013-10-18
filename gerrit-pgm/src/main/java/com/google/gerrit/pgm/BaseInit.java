// Copyright (C) 2013 The Android Open Source Project
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

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.gerrit.pgm.init.InitFlags;
import com.google.gerrit.pgm.init.InitModule;
import com.google.gerrit.pgm.init.InstallPlugins;
import com.google.gerrit.pgm.init.SitePathInitializer;
import com.google.gerrit.pgm.util.ConsoleUI;
import com.google.gerrit.pgm.util.Die;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.schema.SchemaUpdater;
import com.google.gerrit.server.schema.UpdateUI;
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
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.Message;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

/** Initialize a new Gerrit installation. */
public class BaseInit extends SiteProgram {

  private final boolean standalone;

  public BaseInit() {
    this.standalone = true;
  }

  public BaseInit(File sitePath, boolean standalone) {
    this(sitePath, null, standalone);
  }

  public BaseInit(File sitePath, final Provider<DataSource> dsProvider,
      boolean standalone) {
    super(sitePath, dsProvider);
    this.standalone = standalone;
  }

  @Override
  public int run() throws Exception {
    final SiteInit init = createSiteInit();
    if (beforeInit(init)) {
      return 0;
    }

    init.flags.autoStart = getAutoStart() && init.site.isNew;
    init.flags.skipPlugins = skipPlugins();

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
    afterInit(run);
    return 0;
  }

  protected boolean skipPlugins() {
    return false;
  }

  protected boolean beforeInit(SiteInit init) throws Exception {
    return false;
  }

  protected void afterInit(SiteRun run) throws Exception {
  }

  protected List<String> getInstallPlugins() {
    return null;
  }

  protected boolean getAutoStart() {
    return false;
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
    final ConsoleUI ui = getConsoleUI();
    final File sitePath = getSitePath();
    final List<Module> m = new ArrayList<Module>();

    m.add(new InitModule(standalone));
    m.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(ConsoleUI.class).toInstance(ui);
        bind(File.class).annotatedWith(SitePath.class).toInstance(sitePath);
        List<String> plugins =
            Objects.firstNonNull(getInstallPlugins(), Lists.<String> newArrayList());
        bind(new TypeLiteral<List<String>>() {}).annotatedWith(
            InstallPlugins.class).toInstance(plugins);
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

  protected ConsoleUI getConsoleUI() {
    return ConsoleUI.getInstance(false);
  }

  static class SiteRun {
    final ConsoleUI ui;
    final SitePaths site;
    final InitFlags flags;
    final SchemaUpdater schemaUpdater;
    final SchemaFactory<ReviewDb> schema;
    final GitRepositoryManager repositoryManager;

    @Inject
    SiteRun(final ConsoleUI ui, final SitePaths site, final InitFlags flags,
        final SchemaUpdater schemaUpdater,
        final SchemaFactory<ReviewDb> schema,
        final GitRepositoryManager repositoryManager) {
      this.ui = ui;
      this.site = site;
      this.flags = flags;
      this.schemaUpdater = schemaUpdater;
      this.schema = schema;
      this.repositoryManager = repositoryManager;
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
