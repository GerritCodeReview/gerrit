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

import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.pgm.Daemon;
import com.google.gerrit.pgm.Init;
import com.google.gerrit.server.config.FactoryModule;
import com.google.inject.Injector;
import com.google.inject.Module;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class GerritServer {

  /** Returns fully started Gerrit server */
  static GerritServer start() throws Exception {

    final String sitePath = initSite();

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
        int rc = daemon.main(new String[] {"-d", sitePath, "--headless" });
        if (rc != 0) {
          System.out.println("Failed to start Gerrit daemon. Check "
              + sitePath + "/logs/error_log");
          serverStarted.reset();
        }
        return null;
      };
    });

    serverStarted.await();
    System.out.println("Gerrit Server Started");

    Injector i = createTestInjector(daemon);
    return new GerritServer(i, daemon, daemonService);
  }

  private static String initSite() throws Exception {
    String dt = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
    File tmp = File.createTempFile("gerrit_test_" + dt + "_", "_site");
    String path = tmp.getPath();
    if (!tmp.delete() || !tmp.mkdir()) {
      throw new IOException("Cannot create " + path);
    }
    Init init = new Init();
    int rc = init.main(new String[] {"-d", path, "--batch", "--no-auto-start"});
    if (rc != 0) {
      throw new RuntimeException("Couldn't initialize site");
    }
    return path;
  }

  private static Injector createTestInjector(Daemon daemon) throws Exception {
    Injector sysInjector = get(daemon, "sysInjector");
    Module module = new FactoryModule() {
      @Override
      protected void configure() {
        bind(AccountCreator.class);
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

  private Daemon daemon;
  private ExecutorService daemonService;
  private Injector testInjector;

  private GerritServer(Injector testInjector,
      Daemon daemon, ExecutorService daemonService) {
    this.testInjector = testInjector;
    this.daemon = daemon;
    this.daemonService = daemonService;
  }

  Injector getTestInjector() {
    return testInjector;
  }

  void stop() throws Exception {
    LifecycleManager manager = get(daemon, "manager");
    System.out.println("Gerrit Server Shutdown");
    manager.stop();
    daemonService.shutdownNow();
    daemonService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
  }
}
