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

import static com.google.inject.Stage.PRODUCTION;

import com.google.gerrit.httpd.HttpCanonicalWebUrlProvider;
import com.google.gerrit.httpd.WebModule;
import com.google.gerrit.pgm.http.jetty.JettyEnv;
import com.google.gerrit.pgm.http.jetty.JettyModule;
import com.google.gerrit.pgm.http.jetty.JettyServer;
import com.google.gerrit.server.Lifecycle;
import com.google.gerrit.server.config.CanonicalWebUrlModule;
import com.google.gerrit.server.config.CanonicalWebUrlProvider;
import com.google.gerrit.server.config.DatabaseModule;
import com.google.gerrit.server.config.GerritConfigModule;
import com.google.gerrit.server.config.GerritGlobalModule;
import com.google.gerrit.server.config.GerritMasterLifecycle;
import com.google.gerrit.sshd.SshModule;
import com.google.gerrit.sshd.commands.MasterCommandModule;
import com.google.gerrit.sshd.commands.SlaveCommandModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provider;

import org.kohsuke.args4j.Option;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

/** Run SSH daemon portions of Gerrit. */
public class Daemon extends AbstractProgram {
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

  @Option(name = "--slave", usage = "support fetch only")
  private boolean slave;

  private Injector dbInjector;
  private Injector cfgInjector;
  private Injector sysInjector;
  private Injector sshInjector;
  private Injector webInjector;
  private Injector httpdInjector;

  @Override
  public int run() throws Exception {
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

    dbInjector = Guice.createInjector(PRODUCTION, new DatabaseModule());
    cfgInjector = dbInjector.createChildInjector(new GerritConfigModule());
    sysInjector = createSysInjector();

    if (sshd) {
      initSshd();
    }

    if (httpd) {
      initHttpd();
    }

    final Injector[] all =
        {dbInjector, cfgInjector, sysInjector, sshInjector, webInjector,
            httpdInjector};
    try {
      Lifecycle.start(all);
      if (httpd) {
        httpdInjector.getInstance(JettyServer.class).join();
        return 0;

      } else if (sshd) {
        return never();

      } else {
        throw die("No services enabled");
      }
    } finally {
      Lifecycle.stop(all);
    }
  }

  private Injector createSysInjector() {
    final List<Module> modules = new ArrayList<Module>();
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
      modules.add(new GerritMasterLifecycle());
    }
    return cfgInjector.createChildInjector(modules);
  }

  private void initSshd() {
    sshInjector = createSshInjector();
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
  }

  private Injector createWebInjector() {
    final List<Module> modules = new ArrayList<Module>();
    modules.add(sshInjector.getInstance(WebModule.class));
    return sysInjector.createChildInjector(modules);
  }

  private Injector createHttpdInjector() {
    final List<Module> modules = new ArrayList<Module>();
    modules.add(new JettyModule(new JettyEnv(webInjector)));
    return sysInjector.createChildInjector(modules);
  }
}
