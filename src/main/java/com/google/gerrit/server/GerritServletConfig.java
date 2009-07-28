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
import com.google.gerrit.server.patch.PatchDetailServiceImpl;
import com.google.gerrit.server.ssh.SshServlet;
import com.google.gwtexpui.server.CacheControlFilter;
import com.google.gwtjsonrpc.client.RemoteJsonService;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;
import com.google.inject.AbstractModule;
import com.google.inject.BindingAnnotation;
import com.google.inject.ConfigurationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.servlet.GuiceServletContextListener;
import com.google.inject.servlet.ServletModule;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.security.ProviderException;

import javax.servlet.ServletContextEvent;

/** Configures the web application environment for Gerrit Code Review. */
public class GerritServletConfig extends GuiceServletContextListener {
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

  private static Module createDatabaseModule() {
    return new AbstractModule() {
      @Override
      protected void configure() {
        try {
          bind(GerritServer.class).toInstance(GerritServer.getInstance(true));
        } catch (OrmException e) {
          addError(e);
        } catch (XsrfException e) {
          addError(e);
        }

        bind(ContactStore.class)
            .toProvider(EncryptedContactStoreProvider.class);
        bind(FileTypeRegistry.class).to(MimeUtilFileTypeRegistry.class);
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
