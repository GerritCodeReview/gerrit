// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import java.lang.reflect.Field;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.pgm.Daemon;
import com.google.gerrit.server.config.FactoryModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.assistedinject.Assisted;

class GerritServer {
  interface Factory {
    GerritServer create(Daemon daemon, ExecutorService daemonService);
  }

  private AccountCreator accountCreator;
  private Daemon daemon;
  private ExecutorService daemonService;

  @Inject
  GerritServer(AccountCreator accountCreator, @Assisted Daemon daemon,
      @Assisted ExecutorService daemonService) {
    this.accountCreator = accountCreator;
    this.daemon = daemon;
    this.daemonService = daemonService;
  }

  /** Returns fully started Gerrit server */
  static GerritServer start(final GerritSite site) throws Exception {
    final CyclicBarrier serverStarted = new CyclicBarrier(2);
    final Daemon daemon = new Daemon(new Runnable() {
      public void run() {
        try {
          serverStarted.await();
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        } catch (BrokenBarrierException e) {
          throw new RuntimeException(e);
        }
      }
    });
    ExecutorService daemonService = Executors.newSingleThreadExecutor();
    daemonService.submit(new Callable<Void>() {
      public Void call() throws Exception {
        daemon.main(new String[] {"-d", site.getPath(), "--headless" });
        return null;
      };
    });

    serverStarted.await();

    Injector i = createTestInjector(daemon);
    return i.getInstance(GerritServer.Factory.class).create(daemon, daemonService);
  }

  AccountCreator getAccountCreator() {
    return accountCreator;
  }

  void stop() throws Exception {
    LifecycleManager manager = get(daemon, "manager");
    System.out.println("Gerrit Server Shutdown");
    manager.stop();
    daemonService.shutdownNow();
    daemonService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
  }

  private static Injector createTestInjector(Daemon daemon) throws Exception {
    Injector sysInjector = get(daemon, "sysInjector");
    Module module = new FactoryModule() {
      @Override
      protected void configure() {
        bind(AccountCreator.class);
        factory(GerritServer.Factory.class);
      }
    };
    return sysInjector.createChildInjector(module);
  }

  @SuppressWarnings("unchecked")
  private static <T> T get(Object obj, String field) throws SecurityException,
      NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
    Field f = obj.getClass().getDeclaredField(field);
    f.setAccessible(true);
    return (T) f.get(obj);
  }
}
