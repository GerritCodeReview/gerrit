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

import com.google.gerrit.httpd.HttpCanonicalWebUrlProvider;
import com.google.gerrit.httpd.WebModule;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.pgm.http.jetty.JettyEnv;
import com.google.gerrit.pgm.http.jetty.JettyModule;
import com.google.gerrit.pgm.http.jetty.ProjectQoSFilter;
import com.google.gerrit.pgm.util.ErrorLogFile;
import com.google.gerrit.pgm.util.LogFileCompressor;
import com.google.gerrit.pgm.util.RuntimeShutdown;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.server.config.AuthConfigModule;
import com.google.gerrit.server.config.CanonicalWebUrlModule;
import com.google.gerrit.server.config.CanonicalWebUrlProvider;
import com.google.gerrit.server.config.GerritGlobalModule;
import com.google.gerrit.server.config.MasterNodeStartup;
import com.google.gerrit.server.schema.SchemaVersionCheck;
import com.google.gerrit.sshd.SshModule;
import com.google.gerrit.sshd.commands.MasterCommandModule;
import com.google.gerrit.sshd.commands.SlaveCommandModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provider;

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

  private final LifecycleManager manager = new LifecycleManager();
  private Injector dbInjector;
  private Injector cfgInjector;
  private Injector sysInjector;
  private Injector sshInjector;
  private Injector webInjector;
  private Injector httpdInjector;
  private File runFile;

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
    if (httpd && !sshd) {
      // TODO Support HTTP without SSH.
      throw die("--enable-httpd currently requires --enable-sshd");
    }

    if (consoleLog) {
    } else {
      manager.add(ErrorLogFile.start(getSitePath()));
    }

    try {
      dbInjector = createDbInjector(MULTI_USER);
      cfgInjector = createCfgInjector();
      sysInjector = createSysInjector();
      manager.add(dbInjector, cfgInjector, sysInjector);

      if (sshd) {
        initSshd();
      }

      if (httpd) {
        initHttpd();
      }

      manager.start();
      RuntimeShutdown.add(new Runnable() {
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

      RuntimeShutdown.waitFor();
      return 0;
    } catch (Throwable err) {
      log.error("Unable to start daemon", err);
      return 1;
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
    modules.add(cfgInjector.getInstance(GerritGlobalModule.class));
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
    if (!slave) {
      modules.add(new MasterNodeStartup());
    }
    return cfgInjector.createChildInjector(modules);
  }

  private void initSshd() {
    sshInjector = createSshInjector();
    manager.add(sshInjector);
  }

  private Injector createSshInjector() {
    final List<Module> modules = new ArrayList<Module>();
    modules.add(new SshModule());
    if (slave) {
      modules.add(new SlaveCommandModule());
    } else {
      modules.add(new MasterCommandModule());
    }
    return sysInjector.createChildInjector(modules);
  }

  private void initHttpd() {
    webInjector = createWebInjector();

    sysInjector.getInstance(HttpCanonicalWebUrlProvider.class)
        .setHttpServletRequest(
            webInjector.getProvider(HttpServletRequest.class));

    httpdInjector = createHttpdInjector();
    manager.add(webInjector, httpdInjector);
  }

  private Injector createWebInjector() {
    final List<Module> modules = new ArrayList<Module>();
    modules.add(sshInjector.getInstance(WebModule.class));
    modules.add(sshInjector.getInstance(ProjectQoSFilter.Module.class));
    return sysInjector.createChildInjector(modules);
  }

  private Injector createHttpdInjector() {
    final List<Module> modules = new ArrayList<Module>();
    modules.add(new JettyModule(new JettyEnv(webInjector)));
    return webInjector.createChildInjector(modules);
  }
}
