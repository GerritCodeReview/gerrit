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

import com.google.gerrit.git.WorkQueue;
import com.google.gerrit.server.patch.PatchDetailServiceSrv;
import com.google.gerrit.server.ssh.SshServlet;
import com.google.gwtexpui.server.CacheControlFilter;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;
import com.google.inject.AbstractModule;
import com.google.inject.ConfigurationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.servlet.GuiceServletContextListener;
import com.google.inject.servlet.ServletModule;

import org.openid4java.consumer.ConsumerException;

import java.security.ProviderException;

import javax.servlet.ServletContextEvent;

/** Configures the web application environment for Gerrit Code Review. */
public class GerritServletConfig extends GuiceServletContextListener {
  private static Module createServletModule() {
    return new ServletModule() {
      @Override
      protected void configureServlets() {
        filter("/*").through(UrlRewriteFilter.class);

        filter("/*").through(Key.get(CacheControlFilter.class));
        bind(Key.get(CacheControlFilter.class)).in(Scopes.SINGLETON);

        serve("/Gerrit", "/Gerrit/*").with(HostPageServlet.class);
        serve("/prettify/*").with(PrettifyServlet.class);
        serve("/login").with(OpenIdLoginServlet.class);
        serve("/ssh_info").with(SshServlet.class);
        serve("/cat/*").with(CatServlet.class);
        serve("/static/*").with(StaticServlet.class);

        rpc(AccountServiceSrv.class);
        rpc(AccountSecuritySrv.class);
        rpc(GroupAdminServiceSrv.class);
        rpc(ChangeDetailServiceSrv.class);
        rpc(ChangeListServiceSrv.class);
        rpc(ChangeManageServiceSrv.class);
        rpc(OpenIdServiceSrv.class);
        rpc(PatchDetailServiceSrv.class);
        rpc(ProjectAdminServiceSrv.class);
        rpc(SuggestServiceSrv.class);
        rpc(SystemInfoServiceSrv.class);

        if (BecomeAnyAccountLoginServlet.isAllowed()) {
          serve("/become").with(BecomeAnyAccountLoginServlet.class);
        }
      }

      private void rpc(Class<? extends GerritJsonServlet> clazz) {
        String name = clazz.getSimpleName();
        if (name.endsWith("Srv")) {
          name = name.substring(0, name.length() - 3);
        }
        rpc(name, clazz);
      }

      private void rpc(String name, Class<? extends GerritJsonServlet> clazz) {
        serve("/gerrit/rpc/" + name).with(clazz);
      }
    };
  }

  private static Module createDatabaseModule() {
    return new AbstractModule() {
      @Override
      protected void configure() {
        try {
          final GerritServer gs = GerritServer.getInstance(true);
          bind(GerritServer.class).toInstance(gs);
          bind(ContactStore.class).toInstance(EncryptedContactStore.create(gs));
          bind(OpenIdServiceImpl.class).toInstance(new OpenIdServiceImpl(gs));
          bind(FileTypeRegistry.class).toInstance(new FileTypeRegistry(gs));
        } catch (OrmException e) {
          addError(e);
        } catch (XsrfException e) {
          addError(e);
        } catch (ConsumerException e) {
          addError(e);
        }
      }
    };
  }

  private final Injector injector =
      Guice.createInjector(createDatabaseModule(), createServletModule());

  @Override
  protected Injector getInjector() {
    return injector;
  }

  @Override
  public void contextInitialized(final ServletContextEvent event) {
    super.contextInitialized(event);
  }

  @Override
  public void contextDestroyed(final ServletContextEvent event) {
    try {
      final GerritServer gs = injector.getInstance(Key.get(GerritServer.class));
      gs.closeDataSource();
    } catch (ConfigurationException ce) {
      // Assume it never started.
    } catch (ProviderException ce) {
      // Assume it never started.
    }
    WorkQueue.terminate();
    super.contextDestroyed(event);
  }
}
