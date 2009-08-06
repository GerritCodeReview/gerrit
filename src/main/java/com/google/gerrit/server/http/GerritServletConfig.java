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

package com.google.gerrit.server.http;

import static com.google.inject.Stage.PRODUCTION;

import com.google.gerrit.git.PushAllProjectsOp;
import com.google.gerrit.git.ReloadSubmitQueueOp;
import com.google.gerrit.git.WorkQueue;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.CanonicalWebUrlProvider;
import com.google.gerrit.server.config.DatabaseModule;
import com.google.gerrit.server.config.GerritGlobalModule;
import com.google.gerrit.server.openid.OpenIdModule;
import com.google.gerrit.server.ssh.SshDaemon;
import com.google.gerrit.server.ssh.SshDaemonModule;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.inject.ConfigurationException;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.servlet.GuiceServletContextListener;
import com.google.inject.servlet.ServletModule;
import com.google.inject.spi.Message;

import net.sf.ehcache.CacheManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContextEvent;
import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;

/** Configures the web application environment for Gerrit Code Review. */
public class GerritServletConfig extends GuiceServletContextListener {
  private static final Logger log =
      LoggerFactory.getLogger(GerritServletConfig.class);

  private Injector dbInjector;
  private Injector sysInjector;
  private Injector webInjector;
  private Injector sshInjector;

  private synchronized void init() {
    if (sysInjector == null) {
      try {
        dbInjector = Guice.createInjector(PRODUCTION, new DatabaseModule());
      } catch (CreationException ce) {
        final Message first = ce.getErrorMessages().iterator().next();
        final StringBuilder buf = new StringBuilder();
        buf.append(first.getMessage());
        Throwable why = first.getCause();
        while (why != null) {
          buf.append("\n  caused by ");
          buf.append(why.toString());
          why = why.getCause();
        }
        if (first.getCause() != null) {
          buf.append("\n");
          buf.append("\nResolve above errors before continuing.");
          buf.append("\nComplete stack trace follows:");
        }
        log.error(buf.toString(), first.getCause());
        throw new CreationException(Collections.singleton(first));
      }

      sysInjector = dbInjector.createChildInjector(new GerritGlobalModule());
      sshInjector = createSshInjector();
      webInjector = createWebInjector();

      // Push the Provider<HttpServletRequest> down into the canonical
      // URL provider. Its optional for that provider, but since we can
      // supply one we should do so, in case the administrator has not
      // setup the canonical URL in the configuration file.
      //
      // Note we have to do this manually as Guice failed to do the
      // injection here because the HTTP environment is not visible
      // to the core server modules.
      //
      sysInjector.getInstance(CanonicalWebUrlProvider.class)
          .setHttpServletRequest(
              webInjector.getProvider(HttpServletRequest.class));
    }
  }

  private Injector createSshInjector() {
    return sysInjector.createChildInjector(new SshDaemonModule());
  }

  private Injector createWebInjector() {
    final Provider<SshInfo> sshInfo = sshInjector.getProvider(SshInfo.class);
    final AuthConfig auth = sysInjector.getInstance(AuthConfig.class);

    final List<Module> modules = new ArrayList<Module>();
    modules.add(new WebModule(sshInfo));

    switch (auth.getLoginType()) {
      case OPENID:
        modules.add(new OpenIdModule());
        break;

      case DEVELOPMENT_BECOME_ANY_ACCOUNT:
        modules.add(new ServletModule() {
          @Override
          protected void configureServlets() {
            serve("/become").with(BecomeAnyAccountLoginServlet.class);
          }
        });
        break;
    }

    return sysInjector.createChildInjector(modules);
  }

  @Override
  protected Injector getInjector() {
    init();
    return webInjector;
  }

  @Override
  public void contextInitialized(final ServletContextEvent event) {
    super.contextInitialized(event);
    init();

    try {
      sysInjector.getInstance(PushAllProjectsOp.Factory.class).create(null)
          .start(30, TimeUnit.SECONDS);
    } catch (ConfigurationException e) {
      log.error("Unable to restart replication queue", e);
    } catch (ProvisionException e) {
      log.error("Unable to restart replication queue", e);
    }

    try {
      sysInjector.getInstance(ReloadSubmitQueueOp.Factory.class).create()
          .start(15, TimeUnit.SECONDS);
    } catch (ConfigurationException e) {
      log.error("Unable to restart merge queue", e);
    } catch (ProvisionException e) {
      log.error("Unable to restart merge queue", e);
    }

    try {
      sshInjector.getInstance(SshDaemon.class).start();
    } catch (ConfigurationException e) {
      log.error("Unable to start SSHD", e);
    } catch (ProvisionException e) {
      log.error("Unable to start SSHD", e);
    } catch (IOException e) {
      log.error("Unable to start SSHD", e);
    }
  }

  @Override
  public void contextDestroyed(final ServletContextEvent event) {
    try {
      if (sshInjector != null) {
        sshInjector.getInstance(SshDaemon.class).stop();
      }
    } catch (ConfigurationException e) {
    } catch (ProvisionException e) {
    }

    try {
      if (sysInjector != null) {
        sysInjector.getInstance(WorkQueue.class).shutdown();
      }
    } catch (ConfigurationException e) {
    } catch (ProvisionException e) {
    }

    try {
      if (sysInjector != null) {
        sysInjector.getInstance(CacheManager.class).shutdown();
      }
    } catch (ConfigurationException e) {
    } catch (ProvisionException e) {
    }

    try {
      if (dbInjector != null) {
        closeDataSource(dbInjector.getInstance(DatabaseModule.DS));
      }
    } catch (ConfigurationException ce) {
    } catch (ProvisionException ce) {
    }

    super.contextDestroyed(event);
  }

  private void closeDataSource(final DataSource ds) {
    try {
      final Class<?> type = Class.forName("com.mchange.v2.c3p0.DataSources");
      if (type.isInstance(ds)) {
        type.getMethod("destroy", DataSource.class).invoke(null, ds);
        return;
      }
    } catch (Throwable bad) {
      // Oh well, its not a c3p0 pooled connection. Too bad its
      // not standardized how "good applications cleanup".
    }
  }
}
