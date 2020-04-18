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

import static com.google.gerrit.common.Version.getVersion;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.elasticsearch.ElasticIndexModule;
import com.google.gerrit.extensions.client.AuthType;
import com.google.gerrit.gpg.GpgModule;
import com.google.gerrit.httpd.AllRequestFilter;
import com.google.gerrit.httpd.GerritAuthModule;
import com.google.gerrit.httpd.GetUserFilter;
import com.google.gerrit.httpd.GitOverHttpModule;
import com.google.gerrit.httpd.H2CacheBasedWebSession;
import com.google.gerrit.httpd.HttpCanonicalWebUrlProvider;
import com.google.gerrit.httpd.RequestCleanupFilter;
import com.google.gerrit.httpd.RequestContextFilter;
import com.google.gerrit.httpd.RequestMetricsFilter;
import com.google.gerrit.httpd.RequireSslFilter;
import com.google.gerrit.httpd.SetThreadNameFilter;
import com.google.gerrit.httpd.WebModule;
import com.google.gerrit.httpd.WebSshGlueModule;
import com.google.gerrit.httpd.auth.oauth.OAuthModule;
import com.google.gerrit.httpd.auth.openid.OpenIdModule;
import com.google.gerrit.httpd.plugins.HttpPluginModule;
import com.google.gerrit.httpd.raw.StaticModule;
import com.google.gerrit.index.IndexType;
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
import com.google.gerrit.server.LibModuleLoader;
import com.google.gerrit.server.LibModuleType;
import com.google.gerrit.server.ModuleOverloader;
import com.google.gerrit.server.StartupChecks;
import com.google.gerrit.server.account.AccountDeactivator;
import com.google.gerrit.server.account.InternalAccountDirectory;
import com.google.gerrit.server.api.GerritApiModule;
import com.google.gerrit.server.api.PluginApiModule;
import com.google.gerrit.server.audit.AuditModule;
import com.google.gerrit.server.cache.h2.H2CacheModule;
import com.google.gerrit.server.cache.mem.DefaultMemoryCacheModule;
import com.google.gerrit.server.change.ChangeCleanupRunner;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.AuthConfigModule;
import com.google.gerrit.server.config.CanonicalWebUrlModule;
import com.google.gerrit.server.config.CanonicalWebUrlProvider;
import com.google.gerrit.server.config.DefaultUrlFormatter;
import com.google.gerrit.server.config.DownloadConfig;
import com.google.gerrit.server.config.GerritGlobalModule;
import com.google.gerrit.server.config.GerritInstanceIdModule;
import com.google.gerrit.server.config.GerritInstanceNameModule;
import com.google.gerrit.server.config.GerritOptions;
import com.google.gerrit.server.config.GerritRuntime;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SshClientImplementation;
import com.google.gerrit.server.config.SysExecutorModule;
import com.google.gerrit.server.events.EventBroker;
import com.google.gerrit.server.events.StreamEventsApiListener;
import com.google.gerrit.server.git.GarbageCollectionModule;
import com.google.gerrit.server.git.SearchingChangeCacheImpl;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.group.PeriodicGroupIndexer;
import com.google.gerrit.server.index.IndexModule;
import com.google.gerrit.server.index.OnlineUpgrader;
import com.google.gerrit.server.index.VersionManager;
import com.google.gerrit.server.mail.SignedTokenEmailTokenVerifier;
import com.google.gerrit.server.mail.receive.MailReceiver;
import com.google.gerrit.server.mail.send.SmtpEmailSender;
import com.google.gerrit.server.mime.MimeUtil2Module;
import com.google.gerrit.server.patch.DiffExecutorModule;
import com.google.gerrit.server.permissions.DefaultPermissionBackendModule;
import com.google.gerrit.server.plugins.PluginGuiceEnvironment;
import com.google.gerrit.server.plugins.PluginModule;
import com.google.gerrit.server.project.DefaultProjectNameLockManager;
import com.google.gerrit.server.restapi.RestApiModule;
import com.google.gerrit.server.schema.JdbcAccountPatchReviewStore;
import com.google.gerrit.server.schema.NoteDbSchemaVersionCheck;
import com.google.gerrit.server.securestore.DefaultSecureStore;
import com.google.gerrit.server.securestore.SecureStore;
import com.google.gerrit.server.securestore.SecureStoreClassName;
import com.google.gerrit.server.securestore.SecureStoreProvider;
import com.google.gerrit.server.ssh.NoSshKeyCache;
import com.google.gerrit.server.ssh.NoSshModule;
import com.google.gerrit.server.ssh.SshAddressesModule;
import com.google.gerrit.server.submit.LocalMergeSuperSetComputation;
import com.google.gerrit.server.submit.SubscriptionGraph;
import com.google.gerrit.sshd.SshHostKeyModule;
import com.google.gerrit.sshd.SshKeyCacheImpl;
import com.google.gerrit.sshd.SshModule;
import com.google.gerrit.sshd.commands.DefaultCommandModule;
import com.google.gerrit.sshd.commands.IndexCommandsModule;
import com.google.gerrit.sshd.commands.SequenceCommandsModule;
import com.google.gerrit.sshd.plugin.LfsPluginAuthCommand;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Stage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.sshd.DefaultProxyDataFactory;
import org.eclipse.jgit.transport.sshd.JGitKeyCache;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.jgit.util.FS;
import org.kohsuke.args4j.Option;

/** Run SSH daemon portions of Gerrit. */
public class Daemon extends SiteProgram {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

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

  @Option(
      name = "--replica",
      aliases = {"--slave"},
      usage = "Support fetch only")
  private boolean replica;

  @Option(name = "--console-log", usage = "Log to console (not $site_path/logs)")
  private boolean consoleLog;

  @Option(name = "-s", usage = "Start interactive shell")
  private boolean inspector;

  @Option(name = "--run-id", usage = "Cookie to store in $site_path/logs/gerrit.run")
  private String runId;

  @Option(name = "--headless", usage = "Don't start the UI frontend")
  private boolean headless;

  private String devCdn = "";

  @Option(name = "--dev-cdn", usage = "Use specified cdn for serving static content.")
  private void setDevCdn(String cdn) {
    if (cdn == null) {
      cdn = "";
    }
    if (cdn.endsWith("/")) {
      cdn = cdn.substring(0, cdn.length() - 1);
    }
    devCdn = cdn;
  }

  @Option(
      name = "--init",
      aliases = {"-i"},
      usage = "Init site before starting the daemon")
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
  private boolean inMemoryTest;
  private AbstractModule luceneModule;
  private Module emailModule;
  private List<Module> testSysModules = new ArrayList<>();
  private Module auditEventModule;

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

  @VisibleForTesting
  public boolean getEnableSshd() {
    return sshd;
  }

  public void setEnableHttpd(boolean enable) {
    httpd = enable;
  }

  public void setReplica(boolean replica) {
    this.replica = replica;
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
        (t, e) -> logger.atSevere().withCause(e).log("Thread %s threw exception", t.getName()));

    if (runId != null) {
      runFile = getSitePath().resolve("logs").resolve("gerrit.run");
    }

    if (httpd == null) {
      httpd = !replica;
    }

    if (!httpd && !sshd) {
      throw die("No services enabled, nothing to do");
    }

    try {
      start();
      RuntimeShutdown.add(
          () -> {
            logger.atInfo().log("caught shutdown, cleaning up");
            stop();
          });

      logger.atInfo().log("Gerrit Code Review %s ready", myVersion());
      if (runId != null) {
        try {
          Files.write(runFile, (runId + "\n").getBytes(UTF_8));
          runFile.toFile().setReadable(true, false);
        } catch (IOException err) {
          logger.atWarning().withCause(err).log("Cannot write --run-id to %s", runFile);
        }
      }

      if (serverStarted != null) {
        serverStarted.run();
      }

      if (inspector) {
        JythonShell shell = new JythonShell();
        shell.set("m", manager);
        shell.set("d", this);
        shell.run();
      } else {
        RuntimeShutdown.waitFor();
      }
      return 0;
    } catch (Throwable err) {
      logger.atSevere().withCause(err).log("Unable to start daemon");
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
    inMemoryTest = true;
    headless = true;
  }

  @VisibleForTesting
  public void setEmailModuleForTesting(Module module) {
    emailModule = module;
  }

  @VisibleForTesting
  public void setAuditEventModuleForTesting(Module module) {
    auditEventModule = module;
  }

  @VisibleForTesting
  public void setLuceneModule(LuceneIndexModule m) {
    luceneModule = m;
    inMemoryTest = true;
  }

  @VisibleForTesting
  public void addAdditionalSysModuleForTesting(@Nullable Module... modules) {
    testSysModules.addAll(Arrays.asList(modules));
  }

  @VisibleForTesting
  public void start() throws IOException {
    if (dbInjector == null) {
      dbInjector = createDbInjector(true /* enableMetrics */);
    }
    cfgInjector = createCfgInjector();
    config = cfgInjector.getInstance(Key.get(Config.class, GerritServerConfig.class));
    indexType = IndexModule.getIndexType(cfgInjector);
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
        logger.atWarning().withCause(err).log("failed to delete %s", runFile);
      }
    }
    manager.stop();
  }

  @Override
  protected GerritRuntime getGerritRuntime() {
    return GerritRuntime.DAEMON;
  }

  private boolean sshdOff() {
    return new SshAddressesModule().provideListenAddresses(config).isEmpty();
  }

  private String myVersion() {
    List<String> versionParts = new ArrayList<>();
    if (replica) {
      versionParts.add("[replica]");
    }
    if (headless) {
      versionParts.add("[headless]");
    }
    versionParts.add(getVersion());
    return Joiner.on(" ").join(versionParts);
  }

  private Injector createCfgInjector() {
    final List<Module> modules = new ArrayList<>();
    modules.add(new AuthConfigModule());
    return dbInjector.createChildInjector(modules);
  }

  private Injector createSysInjector() {
    final List<Module> modules = new ArrayList<>();
    modules.add(NoteDbSchemaVersionCheck.module());
    modules.add(new DropWizardMetricMaker.RestModule());
    modules.add(new LogFileCompressor.Module());

    // Index module shutdown must happen before work queue shutdown, otherwise
    // work queue can get stuck waiting on index futures that will never return.
    modules.add(createIndexModule());

    modules.add(new SubscriptionGraph.Module());
    modules.add(new WorkQueue.Module());
    modules.add(new StreamEventsApiListener.Module());
    modules.add(new EventBroker.Module());
    modules.add(new JdbcAccountPatchReviewStore.Module(config));
    modules.add(new SysExecutorModule());
    modules.add(new DiffExecutorModule());
    modules.add(new MimeUtil2Module());
    modules.add(cfgInjector.getInstance(GerritGlobalModule.class));
    modules.add(new GerritApiModule());
    modules.add(new PluginApiModule());

    modules.add(new SearchingChangeCacheImpl.Module(replica));
    modules.add(new InternalAccountDirectory.Module());
    modules.add(new DefaultPermissionBackendModule());
    modules.add(new DefaultMemoryCacheModule());
    modules.add(new H2CacheModule());
    modules.add(cfgInjector.getInstance(MailReceiver.Module.class));
    if (emailModule != null) {
      modules.add(emailModule);
    } else {
      modules.add(new SmtpEmailSender.Module());
    }
    if (auditEventModule != null) {
      modules.add(auditEventModule);
    } else {
      modules.add(new AuditModule());
    }
    modules.add(new SignedTokenEmailTokenVerifier.Module());
    modules.add(new PluginModule());
    if (VersionManager.getOnlineUpgrade(config)) {
      modules.add(new OnlineUpgrader.Module());
    }
    modules.add(new RestApiModule());
    modules.add(new GpgModule(config));
    modules.add(new StartupChecks.Module());
    modules.add(new GerritInstanceNameModule());
    modules.add(new GerritInstanceIdModule());
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
    modules.add(new DefaultUrlFormatter.Module());
    if (SshClientImplementation.APACHE
        == config.getEnum("ssh", null, "clientImplementation", SshClientImplementation.JSCH)) {
      SshdSessionFactory factory =
          new SshdSessionFactory(new JGitKeyCache(), new DefaultProxyDataFactory());
      factory.setHomeDirectory(FS.DETECTED.userHome());
      SshSessionFactory.setInstance(factory);
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
            bind(GerritOptions.class).toInstance(new GerritOptions(headless, replica, devCdn));
            if (inMemoryTest) {
              bind(String.class)
                  .annotatedWith(SecureStoreClassName.class)
                  .toInstance(DefaultSecureStore.class.getName());
              bind(SecureStore.class).toProvider(SecureStoreProvider.class);
            }
          }
        });
    modules.add(new GarbageCollectionModule());
    if (replica) {
      modules.add(new PeriodicGroupIndexer.Module());
    } else {
      modules.add(new AccountDeactivator.Module());
      modules.add(new ChangeCleanupRunner.Module());
    }
    modules.addAll(testSysModules);
    modules.add(new LocalMergeSuperSetComputation.Module());
    modules.add(new DefaultProjectNameLockManager.Module());
    return cfgInjector.createChildInjector(
        ModuleOverloader.override(
            modules, LibModuleLoader.loadModules(cfgInjector, LibModuleType.SYS_MODULE)));
  }

  private Module createIndexModule() {
    if (luceneModule != null) {
      return luceneModule;
    }
    if (indexType.isLucene()) {
      return LuceneIndexModule.latestVersion(replica);
    }
    if (indexType.isElasticsearch()) {
      return ElasticIndexModule.latestVersion(replica);
    }
    throw new IllegalStateException("unsupported index.type = " + indexType);
  }

  private void initSshd() {
    sshInjector = createSshInjector();
    sysInjector.getInstance(PluginGuiceEnvironment.class).setSshInjector(sshInjector);
    manager.add(sshInjector);
  }

  private Injector createSshInjector() {
    final List<Module> modules = new ArrayList<>();
    modules.add(sysInjector.getInstance(SshModule.class));
    if (!inMemoryTest) {
      modules.add(new SshHostKeyModule());
    }
    modules.add(
        new DefaultCommandModule(
            replica,
            sysInjector.getInstance(DownloadConfig.class),
            sysInjector.getInstance(LfsPluginAuthCommand.Module.class)));
    if (!replica) {
      modules.add(new IndexCommandsModule(sysInjector));
      modules.add(new SequenceCommandsModule());
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
    modules.add(RequestContextFilter.module());
    modules.add(RequestMetricsFilter.module());
    modules.add(H2CacheBasedWebSession.module());
    modules.add(sysInjector.getInstance(GerritAuthModule.class));
    modules.add(sysInjector.getInstance(GitOverHttpModule.class));
    if (sshd) {
      modules.add(new ProjectQoSFilter.Module());
    }
    modules.add(RequestCleanupFilter.module());
    modules.add(AllRequestFilter.module());
    modules.add(SetThreadNameFilter.module());
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
    GerritOptions opts = sysInjector.getInstance(GerritOptions.class);
    if (opts.enableMasterFeatures()) {
      modules.add(sysInjector.getInstance(StaticModule.class));
    }

    return sysInjector.createChildInjector(modules);
  }

  private Injector createHttpdInjector() {
    final List<Module> modules = new ArrayList<>();
    modules.add(new JettyModule(new JettyEnv(webInjector)));
    return webInjector.createChildInjector(modules);
  }
}
