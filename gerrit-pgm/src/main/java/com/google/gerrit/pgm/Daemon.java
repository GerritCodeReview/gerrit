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

import static com.google.gerrit.server.schema.DataSourceProvider.Context.MULTI_USER;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.gerrit.common.EventBroker;
import com.google.gerrit.elasticsearch.ElasticIndexModule;
import com.google.gerrit.extensions.client.AuthType;
import com.google.gerrit.gpg.GpgModule;
import com.google.gerrit.httpd.AllRequestFilter;
import com.google.gerrit.httpd.GetUserFilter;
import com.google.gerrit.httpd.GitOverHttpModule;
import com.google.gerrit.httpd.H2CacheBasedWebSession;
import com.google.gerrit.httpd.HttpCanonicalWebUrlProvider;
import com.google.gerrit.httpd.RequestContextFilter;
import com.google.gerrit.httpd.RequestMetricsFilter;
import com.google.gerrit.httpd.RequireSslFilter;
import com.google.gerrit.httpd.WebModule;
import com.google.gerrit.httpd.WebSshGlueModule;
import com.google.gerrit.httpd.auth.oauth.OAuthModule;
import com.google.gerrit.httpd.auth.openid.OpenIdModule;
import com.google.gerrit.httpd.plugins.HttpPluginModule;
import com.google.gerrit.httpd.raw.StaticModule;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.lucene.LuceneIndexModule;
import com.google.gerrit.metrics.dropwizard.DropWizardMetricMaker;
import com.google.gerrit.pgm.http.jetty.JettyEnv;
import com.google.gerrit.pgm.http.jetty.JettyModule;
import com.google.gerrit.pgm.http.jetty.ProjectQoSFilter;
import com.google.gerrit.pgm.util.ErrorLogFile;
import com.google.gerrit.pgm.util.LogFileCompressor;
import com.google.gerrit.pgm.util.RuntimeShutdown;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.server.StartupChecks;
import com.google.gerrit.server.account.InternalAccountDirectory;
import com.google.gerrit.server.cache.h2.DefaultCacheFactory;
import com.google.gerrit.server.change.ChangeCleanupRunner;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.AuthConfigModule;
import com.google.gerrit.server.config.CanonicalWebUrlModule;
import com.google.gerrit.server.config.CanonicalWebUrlProvider;
import com.google.gerrit.server.config.DownloadConfig;
import com.google.gerrit.server.config.GerritGlobalModule;
import com.google.gerrit.server.config.GerritOptions;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.RestCacheAdminModule;
import com.google.gerrit.server.events.StreamEventsApiListener;
import com.google.gerrit.server.git.GarbageCollectionModule;
import com.google.gerrit.server.git.ReceiveCommitsExecutorModule;
import com.google.gerrit.server.git.SearchingChangeCacheImpl;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.index.DummyIndexModule;
import com.google.gerrit.server.index.IndexModule;
import com.google.gerrit.server.index.IndexModule.IndexType;
import com.google.gerrit.server.mail.SignedTokenEmailTokenVerifier;
import com.google.gerrit.server.mail.receive.MailReceiver;
import com.google.gerrit.server.mail.send.SmtpEmailSender;
import com.google.gerrit.server.mime.MimeUtil2Module;
import com.google.gerrit.server.patch.DiffExecutorModule;
import com.google.gerrit.server.plugins.PluginGuiceEnvironment;
import com.google.gerrit.server.plugins.PluginModule;
import com.google.gerrit.server.plugins.PluginRestApiModule;
import com.google.gerrit.server.project.DefaultPermissionBackendModule;
import com.google.gerrit.server.schema.DataSourceProvider;
import com.google.gerrit.server.schema.H2AccountPatchReviewStore;
import com.google.gerrit.server.schema.JdbcAccountPatchReviewStore;
import com.google.gerrit.server.schema.SchemaVersionCheck;
import com.google.gerrit.server.securestore.DefaultSecureStore;
import com.google.gerrit.server.securestore.SecureStore;
import com.google.gerrit.server.securestore.SecureStoreClassName;
import com.google.gerrit.server.securestore.SecureStoreProvider;
import com.google.gerrit.server.ssh.NoSshKeyCache;
import com.google.gerrit.server.ssh.NoSshModule;
import com.google.gerrit.server.ssh.SshAddressesModule;
import com.google.gerrit.sshd.SshHostKeyModule;
import com.google.gerrit.sshd.SshKeyCacheImpl;
import com.google.gerrit.sshd.SshModule;
import com.google.gerrit.sshd.commands.DefaultCommandModule;
import com.google.gerrit.sshd.commands.IndexCommandsModule;
import com.google.gerrit.sshd.plugin.LfsPluginAuthCommand;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Stage;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.eclipse.jgit.lib.Config;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Run SSH daemon portions of Gerrit. */
public class Daemon extends SiteProgram {
  private static final Logger log = LoggerFactory.getLogger(Daemon.class);

  @Option(name = "--enable-httpd", usage = "Enable the internal HTTP daemon")
  private Boolean httpd;

  @Option(name = "--disable-httpd", usage = "Disable the internal HTTP daemon")
  void setDisableHttpd(@SuppressWarnings("unused") boolean arg) {
    httpd = false;
  }

  @Option(name = "--enable-sshd", usage = "Enable the internal SSH daemon")
  private boolean sshd = true;

  @Option(name = "--disable-sshd", usage = "Disable the internal SSH daemon")
  void setDisableSshd(@SuppressWarnings("unused") boolean arg) {
    sshd = false;
  }

  @Option(name = "--slave", usage = "Support fetch only")
  private boolean slave;

  @Option(name = "--console-log", usage = "Log to console (not $site_path/logs)")
  private boolean consoleLog;

  @Option(name = "-s", usage = "Start interactive shell")
  private boolean inspector;

  @Option(name = "--run-id", usage = "Cookie to store in $site_path/logs/gerrit.run")
  private String runId;

  @Option(name = "--headless", usage = "Don't start the UI frontend")
  private boolean headless;

  @Option(name = "--polygerrit-dev", usage = "Force PolyGerrit UI for development")
  private boolean polyGerritDev;

  @Option(
    name = "--init",
    aliases = {"-i"},
    usage = "Init site before starting the daemon"
  )
  private boolean doInit;

  @Option(name = "--stop-only", usage = "Stop the daemon", hidden = true)
  private boolean stopOnly;

  private final LifecycleManager manager = new LifecycleManager();
  private Injector dbInjector;
  private Injector cfgInjector;
  private Config config;
  private Injector sysInjector;
  private Injector sshInjector;
  private Injector webInjector;
  private Injector httpdInjector;
  private Path runFile;
  private boolean test;
  private AbstractModule luceneModule;
  private Module emailModule;

  private Runnable serverStarted;
  private IndexType indexType;

  public Daemon() {}

  @VisibleForTesting
  public Daemon(Runnable serverStarted, Path sitePath) {
    super(sitePath);
    this.serverStarted = serverStarted;
  }

  @VisibleForTesting
  public void setEnableSshd(boolean enable) {
    sshd = enable;
  }

  public void setEnableHttpd(boolean enable) {
    httpd = enable;
  }

  @Override
  public int run() throws Exception {
    if (stopOnly) {
      RuntimeShutdown.manualShutdown();
      return 0;
    }
    if (doInit) {
      try {
        new Init(getSitePath()).run();
      } catch (Exception e) {
        throw die("Init failed", e);
      }
    }
    mustHaveValidSite();
    Thread.setDefaultUncaughtExceptionHandler(
        new UncaughtExceptionHandler() {
          @Override
          public void uncaughtException(Thread t, Throwable e) {
            log.error("Thread " + t.getName() + " threw exception", e);
          }
        });

    if (runId != null) {
      runFile = getSitePath().resolve("logs").resolve("gerrit.run");
    }

    if (httpd == null) {
      httpd = !slave;
    }

    if (!httpd && !sshd) {
      throw die("No services enabled, nothing to do");
    }

    try {
      start();
      RuntimeShutdown.add(
          () -> {
            log.info("caught shutdown, cleaning up");
            stop();
          });

      log.info("Gerrit Code Review " + myVersion() + " ready");
      if (runId != null) {
        try {
          Files.write(runFile, (runId + "\n").getBytes(UTF_8));
          runFile.toFile().setReadable(true, false);
        } catch (IOException err) {
          log.warn("Cannot write --run-id to " + runFile, err);
        }
      }

      if (serverStarted != null) {
        serverStarted.run();
      }

      if (inspector) {
        JythonShell shell = new JythonShell();
        shell.set("m", manager);
        shell.set("ds", dbInjector.getInstance(DataSourceProvider.class));
        shell.set("schk", dbInjector.getInstance(SchemaVersionCheck.class));
        shell.set("d", this);
        shell.run();
      } else {
        RuntimeShutdown.waitFor();
      }
      return 0;
    } catch (Throwable err) {
      log.error("Unable to start daemon", err);
      return 1;
    }
  }

  @VisibleForTesting
  public LifecycleManager getLifecycleManager() {
    return manager;
  }

  @VisibleForTesting
  public void setDatabaseForTesting(List<Module> modules) {
    dbInjector = Guice.createInjector(Stage.PRODUCTION, modules);
    test = true;
    headless = true;
  }

  @VisibleForTesting
  public void setEmailModuleForTesting(Module module) {
    emailModule = module;
  }

  @VisibleForTesting
  public void setLuceneModule(LuceneIndexModule m) {
    luceneModule = m;
    test = true;
  }

  @VisibleForTesting
  public void start() throws IOException {
    if (dbInjector == null) {
      dbInjector = createDbInjector(true /* enableMetrics */, MULTI_USER);
    }
    cfgInjector = createCfgInjector();
    config = cfgInjector.getInstance(Key.get(Config.class, GerritServerConfig.class));
    if (!slave) {
      initIndexType();
    }
    sysInjector = createSysInjector();
    sysInjector.getInstance(PluginGuiceEnvironment.class).setDbCfgInjector(dbInjector, cfgInjector);
    manager.add(dbInjector, cfgInjector, sysInjector);

    if (!consoleLog) {
      manager.add(ErrorLogFile.start(getSitePath(), config));
    }

    sshd &= !sshdOff();
    if (sshd) {
      initSshd();
    }

    if (MoreObjects.firstNonNull(httpd, true)) {
      initHttpd();
    }

    manager.start();
  }

  @VisibleForTesting
  public void stop() {
    if (runId != null) {
      try {
        Files.delete(runFile);
      } catch (IOException err) {
        log.warn("failed to delete " + runFile, err);
      }
    }
    manager.stop();
  }

  private boolean sshdOff() {
    return new SshAddressesModule().getListenAddresses(config).isEmpty();
  }

  private String myVersion() {
    return com.google.gerrit.common.Version.getVersion();
  }

  private Injector createCfgInjector() {
    final List<Module> modules = new ArrayList<>();
    modules.add(new AuthConfigModule());
    return dbInjector.createChildInjector(modules);
  }

  private Injector createSysInjector() {
    final List<Module> modules = new ArrayList<>();
    modules.add(SchemaVersionCheck.module());
    modules.add(new DropWizardMetricMaker.RestModule());
    modules.add(new LogFileCompressor.Module());

    // Plugin module needs to be inserted *before* the index module.
    // There is the concept of LifecycleModule, in Gerrit's own extension
    // to Guice, which has these:
    //  listener().to(SomeClassImplementingLifecycleListener.class);
    // and the start() methods of each such listener are executed in the
    // order they are declared.
    // Makes sure that PluginLoader.start() is executed before the
    // LuceneIndexModule.start() so that plugins get loaded and the respective
    // Guice modules installed so that the on-line reindexing will happen
    // with the proper classes (e.g. group backends, custom Prolog
    // predicates) and the associated rules ready to be evaluated.
    modules.add(new PluginModule());

    // Index module shutdown must happen before work queue shutdown, otherwise
    // work queue can get stuck waiting on index futures that will never return.
    modules.add(createIndexModule());

    modules.add(new WorkQueue.Module());
    modules.add(new StreamEventsApiListener.Module());
    modules.add(new EventBroker.Module());
    modules.add(
        test
            ? new H2AccountPatchReviewStore.InMemoryModule()
            : new JdbcAccountPatchReviewStore.Module(config));
    modules.add(new ReceiveCommitsExecutorModule());
    modules.add(new DiffExecutorModule());
    modules.add(new MimeUtil2Module());
    modules.add(cfgInjector.getInstance(GerritGlobalModule.class));
    modules.add(new SearchingChangeCacheImpl.Module(slave));
    modules.add(new InternalAccountDirectory.Module());
    modules.add(new DefaultPermissionBackendModule());
    modules.add(new DefaultCacheFactory.Module());
    modules.add(cfgInjector.getInstance(MailReceiver.Module.class));
    if (emailModule != null) {
      modules.add(emailModule);
    } else {
      modules.add(new SmtpEmailSender.Module());
    }
    modules.add(new SignedTokenEmailTokenVerifier.Module());
    modules.add(new PluginRestApiModule());
    modules.add(new RestCacheAdminModule());
    modules.add(new GpgModule(config));
    modules.add(new StartupChecks.Module());
    if (MoreObjects.firstNonNull(httpd, true)) {
      modules.add(
          new CanonicalWebUrlModule() {
            @Override
            protected Class<? extends Provider<String>> provider() {
              return HttpCanonicalWebUrlProvider.class;
            }
          });
    } else {
      modules.add(
          new CanonicalWebUrlModule() {
            @Override
            protected Class<? extends Provider<String>> provider() {
              return CanonicalWebUrlProvider.class;
            }
          });
    }
    if (sshd) {
      modules.add(SshKeyCacheImpl.module());
    } else {
      modules.add(NoSshKeyCache.module());
    }
    modules.add(
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(GerritOptions.class)
                .toInstance(new GerritOptions(config, headless, slave, polyGerritDev));
            if (test) {
              bind(String.class)
                  .annotatedWith(SecureStoreClassName.class)
                  .toInstance(DefaultSecureStore.class.getName());
              bind(SecureStore.class).toProvider(SecureStoreProvider.class);
            }
          }
        });
    modules.add(new GarbageCollectionModule());
    if (!slave) {
      modules.add(new ChangeCleanupRunner.Module());
    }
    return cfgInjector.createChildInjector(modules);
  }

  private Module createIndexModule() {
    if (slave) {
      return new DummyIndexModule();
    }
    if (luceneModule != null) {
      return luceneModule;
    }
    switch (indexType) {
      case LUCENE:
        return LuceneIndexModule.latestVersionWithOnlineUpgrade();
      case ELASTICSEARCH:
        return ElasticIndexModule.latestVersionWithOnlineUpgrade();
      default:
        throw new IllegalStateException("unsupported index.type = " + indexType);
    }
  }

  private void initIndexType() {
    indexType = IndexModule.getIndexType(cfgInjector);
    switch (indexType) {
      case LUCENE:
      case ELASTICSEARCH:
        break;
      default:
        throw new IllegalStateException("unsupported index.type = " + indexType);
    }
  }

  private void initSshd() {
    sshInjector = createSshInjector();
    sysInjector.getInstance(PluginGuiceEnvironment.class).setSshInjector(sshInjector);
    manager.add(sshInjector);
  }

  private Injector createSshInjector() {
    final List<Module> modules = new ArrayList<>();
    modules.add(sysInjector.getInstance(SshModule.class));
    if (!test) {
      modules.add(new SshHostKeyModule());
    }
    modules.add(
        new DefaultCommandModule(
            slave,
            sysInjector.getInstance(DownloadConfig.class),
            sysInjector.getInstance(LfsPluginAuthCommand.Module.class)));
    if (!slave && indexType == IndexType.LUCENE) {
      modules.add(new IndexCommandsModule());
    }
    return sysInjector.createChildInjector(modules);
  }

  private void initHttpd() {
    webInjector = createWebInjector();

    sysInjector.getInstance(PluginGuiceEnvironment.class).setHttpInjector(webInjector);

    sysInjector
        .getInstance(HttpCanonicalWebUrlProvider.class)
        .setHttpServletRequest(webInjector.getProvider(HttpServletRequest.class));

    httpdInjector = createHttpdInjector();
    manager.add(webInjector, httpdInjector);
  }

  private Injector createWebInjector() {
    final List<Module> modules = new ArrayList<>();
    if (sshd) {
      modules.add(new ProjectQoSFilter.Module());
    }
    modules.add(RequestContextFilter.module());
    modules.add(AllRequestFilter.module());
    modules.add(RequestMetricsFilter.module());
    modules.add(H2CacheBasedWebSession.module());
    modules.add(sysInjector.getInstance(GitOverHttpModule.class));
    modules.add(sysInjector.getInstance(WebModule.class));
    modules.add(sysInjector.getInstance(RequireSslFilter.Module.class));
    modules.add(new HttpPluginModule());
    if (sshd) {
      modules.add(sshInjector.getInstance(WebSshGlueModule.class));
    } else {
      modules.add(new NoSshModule());
    }

    AuthConfig authConfig = cfgInjector.getInstance(AuthConfig.class);
    if (authConfig.getAuthType() == AuthType.OPENID
        || authConfig.getAuthType() == AuthType.OPENID_SSO) {
      modules.add(new OpenIdModule());
    } else if (authConfig.getAuthType() == AuthType.OAUTH) {
      modules.add(new OAuthModule());
    }
    modules.add(sysInjector.getInstance(GetUserFilter.Module.class));

    // StaticModule contains a "/*" wildcard, place it last.
    modules.add(sysInjector.getInstance(StaticModule.class));

    return sysInjector.createChildInjector(modules);
  }

  private Injector createHttpdInjector() {
    final List<Module> modules = new ArrayList<>();
    modules.add(new JettyModule(new JettyEnv(webInjector)));
    return webInjector.createChildInjector(modules);
  }
}
