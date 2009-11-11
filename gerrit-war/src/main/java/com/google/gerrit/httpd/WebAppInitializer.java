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

package com.google.gerrit.httpd;

import static com.google.inject.Stage.PRODUCTION;

import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.server.config.CanonicalWebUrlModule;
import com.google.gerrit.server.config.DatabaseModule;
import com.google.gerrit.server.config.GerritGlobalModule;
import com.google.gerrit.server.config.MasterNodeStartup;
import com.google.gerrit.sshd.SshModule;
import com.google.gerrit.sshd.commands.MasterCommandModule;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.servlet.GuiceServletContextListener;
import com.google.inject.spi.Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.servlet.ServletContextEvent;
import javax.servlet.http.HttpServletRequest;

/** Configures the web application environment for Gerrit Code Review. */
public class WebAppInitializer extends GuiceServletContextListener {
  private static final Logger log =
      LoggerFactory.getLogger(WebAppInitializer.class);

  private Injector dbInjector;
  private Injector sysInjector;
  private Injector webInjector;
  private Injector sshInjector;
  private LifecycleManager manager;

  private synchronized void init() {
    if (manager == null) {
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

      sysInjector =
          GerritGlobalModule.createInjector(dbInjector,
              new CanonicalWebUrlModule() {
                @Override
                protected Class<? extends Provider<String>> provider() {
                  return HttpCanonicalWebUrlProvider.class;
                }
              });
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
      sysInjector.getInstance(HttpCanonicalWebUrlProvider.class)
          .setHttpServletRequest(
              webInjector.getProvider(HttpServletRequest.class));

      manager = new LifecycleManager();
      manager.add(dbInjector);
      manager.add(sysInjector);
      manager.add(sshInjector);
      manager.add(webInjector);
    }
  }

  private Injector createSshInjector() {
    final List<Module> modules = new ArrayList<Module>();
    modules.add(new SshModule());
    modules.add(new MasterCommandModule());
    modules.add(new MasterNodeStartup());
    return sysInjector.createChildInjector(modules);
  }

  private Injector createWebInjector() {
    final List<Module> modules = new ArrayList<Module>();
    modules.add(sshInjector.getInstance(WebModule.class));
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
    manager.start();
  }

  @Override
  public void contextDestroyed(final ServletContextEvent event) {
    if (manager != null) {
      manager.stop();
      manager = null;
    }
    super.contextDestroyed(event);
  }
}
