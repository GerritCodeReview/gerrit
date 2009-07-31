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

package com.google.gerrit.server;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.Stage.DEVELOPMENT;

import com.google.gerrit.client.data.GerritConfig;
import com.google.gerrit.git.PushAllProjectsOp;
import com.google.gerrit.git.ReloadSubmitQueueOp;
import com.google.gerrit.git.WorkQueue;
import com.google.gerrit.server.config.DatabaseModule;
import com.google.gerrit.server.config.FactoryModule;
import com.google.gerrit.server.config.GerritConfigProvider;
import com.google.gerrit.server.config.GerritServerModule;
import com.google.gerrit.server.mail.RegisterNewEmailSender;
import com.google.gerrit.server.rpc.UiRpcModule;
import com.google.gerrit.server.ssh.SshDaemonModule;
import com.google.gerrit.server.ssh.Sshd;
import com.google.gwtexpui.server.CacheControlFilter;
import com.google.inject.ConfigurationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.ProvisionException;
import com.google.inject.Scopes;
import com.google.inject.servlet.GuiceServletContextListener;
import com.google.inject.servlet.ServletModule;

import net.sf.ehcache.CacheManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContextEvent;
import javax.sql.DataSource;

/** Configures the web application environment for Gerrit Code Review. */
public class GerritServletConfig extends GuiceServletContextListener {
  private static final Logger log =
      LoggerFactory.getLogger(GerritServletConfig.class);

  private static Module createServletModule() {
    return new ServletModule() {
      @Override
      protected void configureServlets() {
        filter("/*").through(UrlRewriteFilter.class);

        filter("/*").through(Key.get(CacheControlFilter.class));
        bind(Key.get(CacheControlFilter.class)).in(Scopes.SINGLETON);

        bind(GerritCall.class);

        serve("/Gerrit", "/Gerrit/*").with(HostPageServlet.class);
        serve("/prettify/*").with(PrettifyServlet.class);
        serve("/ssh_info").with(SshServlet.class);
        serve("/cat/*").with(CatServlet.class);
        serve("/static/*").with(StaticServlet.class);

        if (BecomeAnyAccountLoginServlet.isAllowed()) {
          serve("/become").with(BecomeAnyAccountLoginServlet.class);
        }
      }
    };
  }

  private final Injector rootInjector;
  private final Injector webInjector;
  private final Injector sshInjector;

  public GerritServletConfig() {
    // Sadly we use DEVELOPMENT right now to permit lazy construction on
    // everything. This allows us to perform a graceful shutdown during
    // contextDestroyed if we failed part way through contextInitialized.
    //
    rootInjector = Guice.createInjector(DEVELOPMENT, new GerritServerModule());
    sshInjector = rootInjector.createChildInjector(new SshDaemonModule());
    webInjector = rootInjector.createChildInjector(new FactoryModule() {
      @Override
      protected void configure() {
        bind(Sshd.class).toProvider(sshInjector.getProvider(Sshd.class));
        bind(GerritConfig.class).toProvider(GerritConfigProvider.class).in(
            SINGLETON);

        install(createServletModule());
        install(new UiRpcModule());
        factory(RegisterNewEmailSender.Factory.class);
      }
    });
  }

  @Override
  protected Injector getInjector() {
    return webInjector;
  }

  @Override
  public void contextInitialized(final ServletContextEvent event) {
    super.contextInitialized(event);

    try {
      rootInjector.getInstance(PushAllProjectsOp.Factory.class).create(null)
          .start(30, TimeUnit.SECONDS);
    } catch (ConfigurationException e) {
      log.error("Unable to restart replication queue", e);
    } catch (ProvisionException e) {
      log.error("Unable to restart replication queue", e);
    }

    try {
      rootInjector.getInstance(ReloadSubmitQueueOp.Factory.class).create()
          .start(15, TimeUnit.SECONDS);
    } catch (ConfigurationException e) {
      log.error("Unable to restart merge queue", e);
    } catch (ProvisionException e) {
      log.error("Unable to restart merge queue", e);
    }

    try {
      sshInjector.getInstance(Sshd.class).start();
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
      sshInjector.getInstance(Sshd.class).stop();
    } catch (ConfigurationException e) {
    } catch (ProvisionException e) {
    }

    try {
      rootInjector.getInstance(WorkQueue.class).shutdown();
    } catch (ConfigurationException e) {
    } catch (ProvisionException e) {
    }

    try {
      rootInjector.getInstance(CacheManager.class).shutdown();
    } catch (ConfigurationException e) {
    } catch (ProvisionException e) {
    }

    try {
      closeDataSource(rootInjector.getInstance(DatabaseModule.DS));
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
