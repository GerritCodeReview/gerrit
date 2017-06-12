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

package com.google.gerrit.pgm.init;

import static com.google.gerrit.reviewdb.server.ReviewDbUtil.unwrapDb;
import static com.google.gerrit.server.schema.DataSourceProvider.Context.SINGLE_USER;
import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.Stage.PRODUCTION;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.gerrit.common.Die;
import com.google.gerrit.common.IoUtil;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InitFlags;
import com.google.gerrit.pgm.init.api.InstallAllPlugins;
import com.google.gerrit.pgm.init.api.InstallPlugins;
import com.google.gerrit.pgm.init.api.LibraryDownload;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.GerritServerConfigModule;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.plugins.JarScanner;
import com.google.gerrit.server.schema.SchemaUpdater;
import com.google.gerrit.server.schema.UpdateUI;
import com.google.gerrit.server.securestore.SecureStore;
import com.google.gerrit.server.securestore.SecureStoreClassName;
import com.google.gerrit.server.securestore.SecureStoreProvider;
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
import com.google.inject.util.Providers;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Initialize a new Gerrit installation. */
public class BaseInit extends SiteProgram {
  private static final Logger log = LoggerFactory.getLogger(BaseInit.class);

  private final boolean standalone;
  private final boolean initDb;
  protected final PluginsDistribution pluginsDistribution;
  private final List<String> pluginsToInstall;

  private Injector sysInjector;

  protected BaseInit(PluginsDistribution pluginsDistribution, List<String> pluginsToInstall) {
    this.standalone = true;
    this.initDb = true;
    this.pluginsDistribution = pluginsDistribution;
    this.pluginsToInstall = pluginsToInstall;
  }

  public BaseInit(
      Path sitePath,
      boolean standalone,
      boolean initDb,
      PluginsDistribution pluginsDistribution,
      List<String> pluginsToInstall) {
    this(sitePath, null, standalone, initDb, pluginsDistribution, pluginsToInstall);
  }

  public BaseInit(
      Path sitePath,
      final Provider<DataSource> dsProvider,
      boolean standalone,
      boolean initDb,
      PluginsDistribution pluginsDistribution,
      List<String> pluginsToInstall) {
    super(sitePath, dsProvider);
    this.standalone = standalone;
    this.initDb = initDb;
    this.pluginsDistribution = pluginsDistribution;
    this.pluginsToInstall = pluginsToInstall;
  }

  @Override
  public int run() throws Exception {
    final SiteInit init = createSiteInit();
    if (beforeInit(init)) {
      return 0;
    }

    init.flags.autoStart = getAutoStart() && init.site.isNew;
    init.flags.dev = isDev() && init.site.isNew;
    init.flags.skipPlugins = skipPlugins();
    init.flags.deleteCaches = getDeleteCaches();

    final SiteRun run;
    try {
      init.initializer.run();
      init.flags.deleteOnFailure = false;

      run = createSiteRun(init);
      run.upgradeSchema();

      init.initializer.postRun(createSysInjector(init));
    } catch (Exception | Error failure) {
      if (init.flags.deleteOnFailure) {
        recursiveDelete(getSitePath());
      }
      throw failure;
    }

    System.err.println("Initialized " + getSitePath().toRealPath().normalize());
    afterInit(run);
    return 0;
  }

  protected boolean skipPlugins() {
    return false;
  }

  protected String getSecureStoreLib() {
    return null;
  }

  protected boolean skipAllDownloads() {
    return false;
  }

  protected List<String> getSkippedDownloads() {
    return Collections.emptyList();
  }

  /**
   * Invoked before site init is called.
   *
   * @param init initializer instance.
   * @throws Exception
   */
  protected boolean beforeInit(SiteInit init) throws Exception {
    return false;
  }

  /**
   * Invoked after site init is called.
   *
   * @param run completed run instance.
   * @throws Exception
   */
  protected void afterInit(SiteRun run) throws Exception {}

  protected List<String> getInstallPlugins() {
    try {
      if (pluginsToInstall != null && pluginsToInstall.isEmpty()) {
        return Collections.emptyList();
      }
      List<String> names = pluginsDistribution.listPluginNames();
      if (pluginsToInstall != null) {
        for (Iterator<String> i = names.iterator(); i.hasNext(); ) {
          String n = i.next();
          if (!pluginsToInstall.contains(n)) {
            i.remove();
          }
        }
      }
      return names;
    } catch (FileNotFoundException e) {
      log.warn("Couldn't find distribution archive location." + " No plugin will be installed");
      return null;
    }
  }

  protected boolean installAllPlugins() {
    return false;
  }

  protected boolean getAutoStart() {
    return false;
  }

  public static class SiteInit {
    public final SitePaths site;
    final InitFlags flags;
    final ConsoleUI ui;
    final SitePathInitializer initializer;

    @Inject
    SiteInit(
        final SitePaths site,
        final InitFlags flags,
        final ConsoleUI ui,
        final SitePathInitializer initializer) {
      this.site = site;
      this.flags = flags;
      this.ui = ui;
      this.initializer = initializer;
    }
  }

  private SiteInit createSiteInit() {
    final ConsoleUI ui = getConsoleUI();
    final Path sitePath = getSitePath();
    final List<Module> m = new ArrayList<>();
    final SecureStoreInitData secureStoreInitData = discoverSecureStoreClass();
    final String currentSecureStoreClassName = getConfiguredSecureStoreClass();

    if (secureStoreInitData != null
        && currentSecureStoreClassName != null
        && !currentSecureStoreClassName.equals(secureStoreInitData.className)) {
      String err =
          String.format(
              "Different secure store was previously configured: %s. "
                  + "Use SwitchSecureStore program to switch between implementations.",
              currentSecureStoreClassName);
      throw die(err);
    }

    m.add(new GerritServerConfigModule());
    m.add(new InitModule(standalone, initDb));
    m.add(
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(ConsoleUI.class).toInstance(ui);
            bind(Path.class).annotatedWith(SitePath.class).toInstance(sitePath);
            List<String> plugins =
                MoreObjects.firstNonNull(getInstallPlugins(), new ArrayList<String>());
            bind(new TypeLiteral<List<String>>() {})
                .annotatedWith(InstallPlugins.class)
                .toInstance(plugins);
            bind(new TypeLiteral<Boolean>() {})
                .annotatedWith(InstallAllPlugins.class)
                .toInstance(installAllPlugins());
            bind(PluginsDistribution.class).toInstance(pluginsDistribution);

            String secureStoreClassName;
            if (secureStoreInitData != null) {
              secureStoreClassName = secureStoreInitData.className;
            } else {
              secureStoreClassName = currentSecureStoreClassName;
            }
            if (secureStoreClassName != null) {
              ui.message("Using secure store: %s\n", secureStoreClassName);
            }
            bind(SecureStoreInitData.class).toProvider(Providers.of(secureStoreInitData));
            bind(String.class)
                .annotatedWith(SecureStoreClassName.class)
                .toProvider(Providers.of(secureStoreClassName));
            bind(SecureStore.class).toProvider(SecureStoreProvider.class).in(SINGLETON);
            bind(new TypeLiteral<List<String>>() {})
                .annotatedWith(LibraryDownload.class)
                .toInstance(getSkippedDownloads());
            bind(Boolean.class).annotatedWith(LibraryDownload.class).toInstance(skipAllDownloads());
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

  private SecureStoreInitData discoverSecureStoreClass() {
    String secureStore = getSecureStoreLib();
    if (Strings.isNullOrEmpty(secureStore)) {
      return null;
    }

    try {
      Path secureStoreLib = Paths.get(secureStore);
      if (!Files.exists(secureStoreLib)) {
        throw new InvalidSecureStoreException(String.format("File %s doesn't exist", secureStore));
      }
      JarScanner scanner = new JarScanner(secureStoreLib);
      List<String> secureStores = scanner.findSubClassesOf(SecureStore.class);
      if (secureStores.isEmpty()) {
        throw new InvalidSecureStoreException(
            String.format(
                "Cannot find class implementing %s interface in %s",
                SecureStore.class.getName(), secureStore));
      }
      if (secureStores.size() > 1) {
        throw new InvalidSecureStoreException(
            String.format(
                "%s has more that one implementation of %s interface",
                secureStore, SecureStore.class.getName()));
      }
      IoUtil.loadJARs(secureStoreLib);
      return new SecureStoreInitData(secureStoreLib, secureStores.get(0));
    } catch (IOException e) {
      throw new InvalidSecureStoreException(String.format("%s is not a valid jar", secureStore));
    }
  }

  public static class SiteRun {
    public final ConsoleUI ui;
    public final SitePaths site;
    public final InitFlags flags;
    final SchemaUpdater schemaUpdater;
    final SchemaFactory<ReviewDb> schema;
    final GitRepositoryManager repositoryManager;

    @Inject
    SiteRun(
        final ConsoleUI ui,
        final SitePaths site,
        final InitFlags flags,
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
      final List<String> pruneList = new ArrayList<>();
      schemaUpdater.update(
          new UpdateUI() {
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
          try (JdbcSchema db = (JdbcSchema) unwrapDb(schema.open());
              JdbcExecutor e = new JdbcExecutor(db)) {
            for (String sql : pruneList) {
              e.execute(sql);
            }
          }
        }
      }
    }
  }

  private SiteRun createSiteRun(final SiteInit init) {
    return createSysInjector(init).getInstance(SiteRun.class);
  }

  private Injector createSysInjector(final SiteInit init) {
    if (sysInjector == null) {
      final List<Module> modules = new ArrayList<>();
      modules.add(
          new AbstractModule() {
            @Override
            protected void configure() {
              bind(ConsoleUI.class).toInstance(init.ui);
              bind(InitFlags.class).toInstance(init.flags);
            }
          });
      sysInjector = createDbInjector(SINGLE_USER).createChildInjector(modules);
    }
    return sysInjector;
  }

  private static void recursiveDelete(Path path) {
    final String msg = "warn: Cannot remove ";
    try {
      Files.walkFileTree(
          path,
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path f, BasicFileAttributes attrs) throws IOException {
              try {
                Files.delete(f);
              } catch (IOException e) {
                System.err.println(msg + f);
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException err) {
              try {
                // Previously warned if err was not null; if dir is not empty as a
                // result, will cause an error that will be logged below.
                Files.delete(dir);
              } catch (IOException e) {
                System.err.println(msg + dir);
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path f, IOException e) {
              System.err.println(msg + f);
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      System.err.println(msg + path);
    }
  }

  protected boolean isDev() {
    return false;
  }

  protected boolean getDeleteCaches() {
    return false;
  }
}
