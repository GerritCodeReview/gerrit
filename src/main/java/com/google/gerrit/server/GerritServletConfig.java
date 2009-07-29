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

import com.google.gerrit.client.reviewdb.Branch;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.git.MergeQueue;
import com.google.gerrit.git.PushAllProjectsOp;
import com.google.gerrit.git.ReplicationQueue;
import com.google.gerrit.git.WorkQueue;
import com.google.gerrit.server.patch.PatchDetailServiceImpl;
import com.google.gerrit.server.ssh.GerritSshDaemon;
import com.google.gerrit.server.ssh.SshDaemonModule;
import com.google.gerrit.server.ssh.SshServlet;
import com.google.gwtexpui.server.CacheControlFilter;
import com.google.gwtjsonrpc.client.RemoteJsonService;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.BindingAnnotation;
import com.google.inject.ConfigurationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.servlet.GuiceServletContextListener;
import com.google.inject.servlet.ServletModule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.security.ProviderException;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContextEvent;
import javax.sql.DataSource;

/** Configures the web application environment for Gerrit Code Review. */
public class GerritServletConfig extends GuiceServletContextListener {
  private static final Logger log =
      LoggerFactory.getLogger(GerritServletConfig.class);

  @Retention(RetentionPolicy.RUNTIME)
  @BindingAnnotation
  private static @interface ServletName {
    String value();
  }

  private static final class ServletNameImpl implements ServletName {
    private final String name;

    ServletNameImpl(final String name) {
      this.name = name;
    }

    @Override
    public String value() {
      return name;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
      return ServletName.class;
    }

    @Override
    public String toString() {
      return "ServletName[" + value() + "]";
    }
  }

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
        serve("/login").with(OpenIdLoginServlet.class);
        serve("/ssh_info").with(SshServlet.class);
        serve("/cat/*").with(CatServlet.class);
        serve("/static/*").with(StaticServlet.class);

        rpc(AccountServiceImpl.class);
        rpc(AccountSecurityImpl.class);
        rpc(GroupAdminServiceImpl.class);
        rpc(ChangeDetailServiceImpl.class);
        rpc(ChangeListServiceImpl.class);
        rpc(ChangeManageServiceImpl.class);
        rpc(OpenIdServiceImpl.class);
        rpc(PatchDetailServiceImpl.class);
        rpc(ProjectAdminServiceImpl.class);
        rpc(SuggestServiceImpl.class);
        rpc(SystemInfoServiceImpl.class);

        if (BecomeAnyAccountLoginServlet.isAllowed()) {
          serve("/become").with(BecomeAnyAccountLoginServlet.class);
        }
      }

      private void rpc(Class<? extends RemoteJsonService> clazz) {
        String name = clazz.getSimpleName();
        if (name.endsWith("Impl")) {
          name = name.substring(0, name.length() - 4);
        }
        rpc(name, clazz);
      }

      private void rpc(final String name,
          Class<? extends RemoteJsonService> clazz) {
        final Key<GerritJsonServlet> srv =
            Key.get(GerritJsonServlet.class, new ServletNameImpl(name));
        final GerritJsonServletProvider provider =
            new GerritJsonServletProvider(clazz);
        serve("/gerrit/rpc/" + name).with(srv);
        bind(srv).toProvider(provider).in(Scopes.SINGLETON);
      }
    };
  }

  private final Injector injector =
      Guice.createInjector(createServletModule(), new GerritServerModule(),
          new SshDaemonModule());

  @Override
  protected Injector getInjector() {
    return injector;
  }

  @Override
  public void contextInitialized(final ServletContextEvent event) {
    super.contextInitialized(event);

    try {
      startReplication();
    } catch (ConfigurationException e) {
      log.error("Unable to restart replication queue", e);
    } catch (ProviderException e) {
      log.error("Unable to restart replication queue", e);
    }

    try {
      restartPendingMerges();
    } catch (ConfigurationException e) {
      log.error("Unable to restart merge queue", e);
    } catch (ProviderException e) {
      log.error("Unable to restart merge queue", e);
    }

    try {
      injector.getInstance(GerritSshDaemon.class).start();
    } catch (ConfigurationException e) {
      log.error("Unable to start SSHD", e);
    } catch (ProviderException e) {
      log.error("Unable to start SSHD", e);
    } catch (IOException e) {
      log.error("Unable to start SSHD", e);
    }
  }

  private void startReplication() {
    final ReplicationQueue rq = injector.getInstance(ReplicationQueue.class);
    if (rq.isEnabled()) {
      final SchemaFactory<ReviewDb> sf =
          injector.getInstance(Key
              .get(new TypeLiteral<SchemaFactory<ReviewDb>>() {}));
      WorkQueue.schedule(new PushAllProjectsOp(sf, rq), 30, TimeUnit.SECONDS);
    }
  }

  private void restartPendingMerges() {
    final MergeQueue mq = injector.getInstance(MergeQueue.class);
    final SchemaFactory<ReviewDb> sf =
        injector.getInstance(Key
            .get(new TypeLiteral<SchemaFactory<ReviewDb>>() {}));
    WorkQueue.schedule(new Runnable() {
      public void run() {
        final HashSet<Branch.NameKey> pending = new HashSet<Branch.NameKey>();
        try {
          final ReviewDb c = sf.open();
          try {
            for (final Change change : c.changes().allSubmitted()) {
              pending.add(change.getDest());
            }
          } finally {
            c.close();
          }
        } catch (OrmException e) {
          log.error("Cannot reload MergeQueue", e);
        }

        for (final Branch.NameKey branch : pending) {
          mq.schedule(branch);
        }
      }

      @Override
      public String toString() {
        return "Reload Submit Queue";
      }
    }, 15, TimeUnit.SECONDS);
  }

  @Override
  public void contextDestroyed(final ServletContextEvent event) {
    try {
      injector.getInstance(GerritSshDaemon.class).stop();
    } catch (ConfigurationException e) {
      // Assume it never started.
    } catch (ProviderException e) {
      // Assume it never started.
    }

    WorkQueue.terminate();
    GerritServer.closeCacheManager();

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
    } catch (ProviderException ce) {
      // Assume it never started.
    }

    super.contextDestroyed(event);
  }
}
