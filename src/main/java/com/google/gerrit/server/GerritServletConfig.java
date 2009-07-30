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

import com.google.gerrit.git.PushAllProjectsOp;
import com.google.gerrit.git.ReloadSubmitQueueOp;
import com.google.gerrit.git.WorkQueue;
import com.google.gerrit.server.config.FactoryModule;
import com.google.gerrit.server.config.GerritServerModule;
import com.google.gerrit.server.mail.RegisterNewEmailSender;
import com.google.gerrit.server.rpc.UiRpcModule;
import com.google.gerrit.server.ssh.GerritSshDaemon;
import com.google.gerrit.server.ssh.SshDaemonModule;
import com.google.gerrit.server.ssh.SshServlet;
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
import java.util.ArrayList;
import java.util.List;
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

  private static Module createOtherModule() {
    return new FactoryModule() {
      @Override
      protected void configure() {
        factory(RegisterNewEmailSender.Factory.class);
      }
    };
  }

  private final Injector injector;

  public GerritServletConfig() {
    final List<Module> modules = new ArrayList<Module>();
    modules.add(createServletModule());
    modules.add(new UiRpcModule());
    modules.add(createOtherModule());
    modules.add(new GerritServerModule());
    modules.add(new SshDaemonModule());
    injector = Guice.createInjector(modules);
  }

  @Override
  protected Injector getInjector() {
    return injector;
  }

  @Override
  public void contextInitialized(final ServletContextEvent event) {
    super.contextInitialized(event);

    try {
      injector.getInstance(PushAllProjectsOp.Factory.class).create(null).start(
          30, TimeUnit.SECONDS);
    } catch (ConfigurationException e) {
      log.error("Unable to restart replication queue", e);
    } catch (ProvisionException e) {
      log.error("Unable to restart replication queue", e);
    }

    try {
      injector.getInstance(ReloadSubmitQueueOp.Factory.class).create().start(
          15, TimeUnit.SECONDS);
    } catch (ConfigurationException e) {
      log.error("Unable to restart merge queue", e);
    } catch (ProvisionException e) {
      log.error("Unable to restart merge queue", e);
    }

    try {
      injector.getInstance(GerritSshDaemon.class).start();
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
      injector.getInstance(GerritSshDaemon.class).stop();
    } catch (ConfigurationException e) {
      // Assume it never started.
    } catch (ProvisionException e) {
      // Assume it never started.
    }

    try {
      injector.getInstance(WorkQueue.class).shutdown();
    } catch (ConfigurationException e) {
      // Assume it never started.
    } catch (ProvisionException e) {
      // Assume it never started.
    }

    try {
      injector.getInstance(CacheManager.class).shutdown();
    } catch (ConfigurationException e) {
      // Assume it never started.
    } catch (ProvisionException e) {
      // Assume it never started.
    }

    try {
      final DataSource ds = injector.getInstance(GerritServerModule.DS);
      try {
        final Class<?> type = Class.forName("com.mchange.v2.c3p0.DataSources");
        if (type.isInstance(ds)) {
          type.getMethod("destroy", DataSource.class).invoke(null, ds);
        }
      } catch (Throwable bad) {
        // Oh well, its not a c3p0 pooled connection. Too bad its
        // not standardized how "good applications cleanup".
      }
    } catch (ConfigurationException ce) {
      // Assume it never started.
    } catch (ProvisionException ce) {
      // Assume it never started.
    }

    super.contextDestroyed(event);
  }
}
