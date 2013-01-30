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

import com.google.gerrit.common.ChangeHookRunner;
import com.google.gerrit.httpd.AllRequestFilter;
import com.google.gerrit.httpd.CacheBasedWebSession;
import com.google.gerrit.httpd.GerritUiOptions;
import com.google.gerrit.httpd.GitOverHttpModule;
import com.google.gerrit.httpd.HttpCanonicalWebUrlProvider;
import com.google.gerrit.httpd.RequestContextFilter;
import com.google.gerrit.httpd.WebModule;
import com.google.gerrit.httpd.WebSshGlueModule;
import com.google.gerrit.httpd.auth.openid.OpenIdModule;
import com.google.gerrit.httpd.plugins.HttpPluginModule;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.pgm.http.jetty.GetUserFilter;
import com.google.gerrit.pgm.http.jetty.JettyEnv;
import com.google.gerrit.pgm.http.jetty.JettyModule;
import com.google.gerrit.pgm.http.jetty.ProjectQoSFilter;
import com.google.gerrit.pgm.util.ErrorLogFile;
import com.google.gerrit.pgm.util.LogFileCompressor;
import com.google.gerrit.pgm.util.RuntimeShutdown;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.reviewdb.client.AuthType;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.cache.h2.DefaultCacheFactory;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.AuthConfigModule;
import com.google.gerrit.server.config.CanonicalWebUrlModule;
import com.google.gerrit.server.config.CanonicalWebUrlProvider;
import com.google.gerrit.server.config.GerritGlobalModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.MasterNodeStartup;
import com.google.gerrit.server.contact.HttpContactStoreConnection;
import com.google.gerrit.server.git.ReceiveCommitsExecutorModule;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.mail.SignedTokenEmailTokenVerifier;
import com.google.gerrit.server.mail.SmtpEmailSender;
import com.google.gerrit.server.plugins.PluginGuiceEnvironment;
import com.google.gerrit.server.plugins.PluginModule;
import com.google.gerrit.server.schema.SchemaUpdater;
import com.google.gerrit.server.schema.SchemaVersionCheck;
import com.google.gerrit.server.schema.UpdateUI;
import com.google.gerrit.server.ssh.NoSshModule;
import com.google.gerrit.server.ssh.NoSshKeyCache;
import com.google.gerrit.sshd.SshKeyCacheImpl;
import com.google.gerrit.sshd.SshModule;
import com.google.gerrit.sshd.commands.MasterCommandModule;
import com.google.gerrit.sshd.commands.SlaveCommandModule;
import com.google.gwtorm.jdbc.JdbcExecutor;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.gwtorm.server.StatementExecutor;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Config;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

/** Run SSH daemon portions of Gerrit. */
public class Daemon extends SiteProgram {
  private static final Logger log = LoggerFactory.getLogger(Daemon.class);

  @Option(name = "--enable-httpd", usage = "Enable the internal HTTP daemon")
  private Boolean httpd;

  @Option(name = "--disable-httpd", usage = "Disable the internal HTTP daemon")
  void setDisableHttpd(final boolean arg) {
    httpd = false;
  }

  @Option(name = "--enable-sshd", usage = "Enable the internal SSH daemon")
  private boolean sshd = true;

  @Option(name = "--disable-sshd", usage = "Disable the internal SSH daemon")
  void setDisableSshd(final boolean arg) {
    sshd = false;
  }

  @Option(name = "--slave", usage = "Support fetch only; implies --disable-httpd")
  private boolean slave;

  @Option(name = "--console-log", usage = "Log to console (not $site_path/logs)")
  private boolean consoleLog;

  @Option(name = "--run-id", usage = "Cookie to store in $site_path/logs/gerrit.run")
  private String runId;

  @Option(name = "--headless", usage = "Don't start the UI frontend")
  private boolean headless;

  private final LifecycleManager manager = new LifecycleManager();
  private Injector dbInjector;
  private Injector cfgInjector;
  private Injector sysInjector;
  private Injector sshInjector;
  private Injector webInjector;
  private Injector httpdInjector;
  private File runFile;

  private Runnable serverStarted;

  public Daemon() {
  }

  public Daemon(Runnable serverStarted) {
    this.serverStarted = serverStarted;
  }

  @Override
  public int run() throws Exception {
    mustHaveValidSite();
    Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
      @Override
      public void uncaughtException(Thread t, Throwable e) {
        log.error("Thread " + t.getName() + " threw exception", e);
      }
    });

    if (runId != null) {
      runFile = new File(new File(getSitePath(), "logs"), "gerrit.run");
    }

    if (httpd == null) {
      httpd = !slave;
    }

    if (!httpd && !sshd) {
      throw die("No services enabled, nothing to do");
    }
    if (slave && httpd) {
      throw die("Cannot combine --slave and --enable-httpd");
    }

    if (consoleLog) {
    } else {
      manager.add(ErrorLogFile.start(getSitePath()));
    }

    try {
      dbInjector = createDbInjector(MULTI_USER);
      cfgInjector = createCfgInjector();
      sysInjector = createSysInjector();
      sysInjector.getInstance(PluginGuiceEnvironment.class)
        .setCfgInjector(cfgInjector);
      sysInjector.getInstance(SchemaUpgrade.class).upgradeSchema();
      manager.add(dbInjector, cfgInjector, sysInjector);

      if (sshd) {
        initSshd();
      }

      if (httpd) {
        initHttpd();
      }

      manager.start();
      RuntimeShutdown.add(new Runnable() {
        @Override
        public void run() {
          log.info("caught shutdown, cleaning up");
          if (runId != null) {
            runFile.delete();
          }
          manager.stop();
        }
      });

      log.info("Gerrit Code Review " + myVersion() + " ready");
      if (runId != null) {
        try {
          runFile.createNewFile();
          runFile.setReadable(true, false);

          FileOutputStream out = new FileOutputStream(runFile);
          try {
            out.write((runId + "\n").getBytes("UTF-8"));
          } finally {
            out.close();
          }
        } catch (IOException err) {
          log.warn("Cannot write --run-id to " + runFile, err);
        }
      }

      if (serverStarted != null) {
        serverStarted.run();
      }

      RuntimeShutdown.waitFor();
      return 0;
    } catch (Throwable err) {
      log.error("Unable to start daemon", err);
      return 1;
    }
  }

  static class SchemaUpgrade {

    private final Config config;
    private final SchemaUpdater updater;
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    SchemaUpgrade(@GerritServerConfig Config config, SchemaUpdater updater,
        SchemaFactory<ReviewDb> schema) {
      this.config = config;
      this.updater = updater;
      this.schema = schema;
    }

    void upgradeSchema() throws OrmException {
      SchemaUpgradePolicy policy =
          config.getEnum("site", null, "upgradeSchemaOnStartup",
              SchemaUpgradePolicy.OFF);
      if (policy == SchemaUpgradePolicy.AUTO
          || policy == SchemaUpgradePolicy.AUTO_NO_PRUNE) {
        final List<String> pruneList = new ArrayList<String>();
        updater.update(new UpdateUI() {
          @Override
          public void message(String msg) {
            log.info(msg);
          }

          @Override
          public boolean yesno(boolean def, String msg) {
            return true;
          }

          @Override
          public boolean isBatch() {
            return true;
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

        if (!pruneList.isEmpty() && policy == SchemaUpgradePolicy.AUTO) {
          log.info("Pruning: " + pruneList.toString());
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


  private String myVersion() {
    return com.google.gerrit.common.Version.getVersion();
  }

  private Injector createCfgInjector() {
    final List<Module> modules = new ArrayList<Module>();
    modules.add(new AuthConfigModule());
    return dbInjector.createChildInjector(modules);
  }

  private Injector createSysInjector() {
    final List<Module> modules = new ArrayList<Module>();
    modules.add(SchemaVersionCheck.module());
    modules.add(new LogFileCompressor.Module());
    modules.add(new WorkQueue.Module());
    modules.add(new ChangeHookRunner.Module());
    modules.add(new ReceiveCommitsExecutorModule());
    modules.add(cfgInjector.getInstance(GerritGlobalModule.class));
    modules.add(new DefaultCacheFactory.Module());
    modules.add(new SmtpEmailSender.Module());
    modules.add(new SignedTokenEmailTokenVerifier.Module());
    modules.add(new PluginModule());
    if (httpd) {
      modules.add(new CanonicalWebUrlModule() {
        @Override
        protected Class<? extends Provider<String>> provider() {
          return HttpCanonicalWebUrlProvider.class;
        }
      });
    } else {
      modules.add(new CanonicalWebUrlModule() {
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
    if (!slave) {
      modules.add(new MasterNodeStartup());
    }
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(GerritUiOptions.class).toInstance(new GerritUiOptions(headless));
      }
    });
    return cfgInjector.createChildInjector(modules);
  }

  private void initSshd() {
    sshInjector = createSshInjector();
    sysInjector.getInstance(PluginGuiceEnvironment.class)
        .setSshInjector(sshInjector);
    manager.add(sshInjector);
  }

  private Injector createSshInjector() {
    final List<Module> modules = new ArrayList<Module>();
    if (sshd) {
      modules.add(sysInjector.getInstance(SshModule.class));
      if (slave) {
        modules.add(new SlaveCommandModule());
      } else {
        modules.add(new MasterCommandModule());
      }
    } else {
      modules.add(new NoSshModule());
    }
    return sysInjector.createChildInjector(modules);
  }

  private void initHttpd() {
    webInjector = createWebInjector();

    sysInjector.getInstance(PluginGuiceEnvironment.class)
        .setHttpInjector(webInjector);

    sysInjector.getInstance(HttpCanonicalWebUrlProvider.class)
        .setHttpServletRequest(
            webInjector.getProvider(HttpServletRequest.class));

    httpdInjector = createHttpdInjector();
    manager.add(webInjector, httpdInjector);
  }

  private Injector createWebInjector() {
    final List<Module> modules = new ArrayList<Module>();
    if (sshd) {
      modules.add(new ProjectQoSFilter.Module());
    }
    modules.add(RequestContextFilter.module());
    modules.add(AllRequestFilter.module());
    modules.add(CacheBasedWebSession.module());
    modules.add(HttpContactStoreConnection.module());
    modules.add(sysInjector.getInstance(GitOverHttpModule.class));
    modules.add(sysInjector.getInstance(WebModule.class));
    modules.add(new HttpPluginModule());
    if (sshd) {
      modules.add(sshInjector.getInstance(WebSshGlueModule.class));
    } else {
      modules.add(new NoSshModule());
    }

    AuthConfig authConfig = cfgInjector.getInstance(AuthConfig.class);
    if (authConfig.getAuthType() == AuthType.OPENID ||
        authConfig.getAuthType() == AuthType.OPENID_SSO) {
      modules.add(new OpenIdModule());
    }
    modules.add(sysInjector.getInstance(GetUserFilter.Module.class));

    return sysInjector.createChildInjector(modules);
  }

  private Injector createHttpdInjector() {
    final List<Module> modules = new ArrayList<Module>();
    modules.add(new JettyModule(new JettyEnv(webInjector)));
    return webInjector.createChildInjector(modules);
  }
}
