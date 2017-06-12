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

import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.lucene.LuceneIndexModule;
import com.google.gerrit.pgm.Daemon;
import com.google.gerrit.pgm.Init;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.AsyncReceiveCommits;
import com.google.gerrit.server.ssh.NoSshModule;
import com.google.gerrit.server.util.SocketUtil;
import com.google.gerrit.server.util.SystemLog;
import com.google.gerrit.testutil.FakeEmailSender;
import com.google.gerrit.testutil.NoteDbChecker;
import com.google.gerrit.testutil.NoteDbMode;
import com.google.gerrit.testutil.TempFileUtil;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Paths;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.util.FS;

public class GerritServer {
  @AutoValue
  abstract static class Description {
    static Description forTestClass(org.junit.runner.Description testDesc, String configName) {
      return new AutoValue_GerritServer_Description(
          configName,
          true, // @UseLocalDisk is only valid on methods.
          !has(NoHttpd.class, testDesc.getTestClass()),
          has(Sandboxed.class, testDesc.getTestClass()),
          null, // @GerritConfig is only valid on methods.
          null); // @GerritConfigs is only valid on methods.
    }

    static Description forTestMethod(org.junit.runner.Description testDesc, String configName) {
      return new AutoValue_GerritServer_Description(
          configName,
          testDesc.getAnnotation(UseLocalDisk.class) == null,
          testDesc.getAnnotation(NoHttpd.class) == null
              && !has(NoHttpd.class, testDesc.getTestClass()),
          testDesc.getAnnotation(Sandboxed.class) != null
              || has(Sandboxed.class, testDesc.getTestClass()),
          testDesc.getAnnotation(GerritConfig.class),
          testDesc.getAnnotation(GerritConfigs.class));
    }

    private static boolean has(Class<? extends Annotation> annotation, Class<?> clazz) {
      for (; clazz != null; clazz = clazz.getSuperclass()) {
        if (clazz.getAnnotation(annotation) != null) {
          return true;
        }
      }
      return false;
    }

    @Nullable
    abstract String configName();

    abstract boolean memory();

    abstract boolean httpd();

    abstract boolean sandboxed();

    @Nullable
    abstract GerritConfig config();

    @Nullable
    abstract GerritConfigs configs();

    private Config buildConfig(Config baseConfig) {
      if (configs() != null && config() != null) {
        throw new IllegalStateException("Use either @GerritConfigs or @GerritConfig not both");
      }
      if (configs() != null) {
        return ConfigAnnotationParser.parse(baseConfig, configs());
      } else if (config() != null) {
        return ConfigAnnotationParser.parse(baseConfig, config());
      } else {
        return baseConfig;
      }
    }
  }

  /** Returns fully started Gerrit server */
  static GerritServer start(Description desc, Config baseConfig) throws Exception {
    Config cfg = desc.buildConfig(baseConfig);
    Logger.getLogger("com.google.gerrit").setLevel(Level.DEBUG);
    final CyclicBarrier serverStarted = new CyclicBarrier(2);
    final Daemon daemon =
        new Daemon(
            new Runnable() {
              @Override
              public void run() {
                try {
                  serverStarted.await();
                } catch (InterruptedException | BrokenBarrierException e) {
                  throw new RuntimeException(e);
                }
              }
            },
            Paths.get(baseConfig.getString("gerrit", null, "tempSiteDir")));
    daemon.setEmailModuleForTesting(new FakeEmailSender.Module());

    final File site;
    ExecutorService daemonService = null;
    if (desc.memory()) {
      site = null;
      mergeTestConfig(cfg);
      // Set the log4j configuration to an invalid one to prevent system logs
      // from getting configured and creating log files.
      System.setProperty(SystemLog.LOG4J_CONFIGURATION, "invalidConfiguration");
      cfg.setBoolean("httpd", null, "requestLog", false);
      cfg.setBoolean("sshd", null, "requestLog", false);
      cfg.setBoolean("index", "lucene", "testInmemory", true);
      cfg.setString("gitweb", null, "cgi", "");
      daemon.setEnableHttpd(desc.httpd());
      daemon.setLuceneModule(LuceneIndexModule.singleVersionAllLatest(0));
      daemon.setDatabaseForTesting(
          ImmutableList.<Module>of(new InMemoryTestingDatabaseModule(cfg)));
      daemon.start();
    } else {
      site = initSite(cfg);
      daemonService = Executors.newSingleThreadExecutor();
      daemonService.submit(
          new Callable<Void>() {
            @Override
            public Void call() throws Exception {
              int rc =
                  daemon.main(
                      new String[] {
                        "-d", site.getPath(), "--headless", "--console-log", "--show-stack-trace",
                      });
              if (rc != 0) {
                System.err.println("Failed to start Gerrit daemon");
                serverStarted.reset();
              }
              return null;
            }
          });
      serverStarted.await();
      System.out.println("Gerrit Server Started");
    }

    Injector i = createTestInjector(daemon);
    return new GerritServer(desc, i, daemon, daemonService);
  }

  private static File initSite(Config base) throws Exception {
    File tmp = TempFileUtil.createTempDirectory();
    Init init = new Init();
    int rc =
        init.main(
            new String[] {
              "-d", tmp.getPath(), "--batch", "--no-auto-start", "--skip-plugins",
            });
    if (rc != 0) {
      throw new RuntimeException("Couldn't initialize site");
    }

    MergeableFileBasedConfig cfg =
        new MergeableFileBasedConfig(new File(new File(tmp, "etc"), "gerrit.config"), FS.DETECTED);
    cfg.load();
    cfg.merge(base);
    mergeTestConfig(cfg);
    cfg.save();
    return tmp;
  }

  private static void mergeTestConfig(Config cfg) {
    String forceEphemeralPort = String.format("%s:0", getLocalHost().getHostName());
    String url = "http://" + forceEphemeralPort + "/";
    cfg.setString("gerrit", null, "canonicalWebUrl", url);
    cfg.setString("httpd", null, "listenUrl", url);
    cfg.setString("sshd", null, "listenAddress", forceEphemeralPort);
    cfg.setBoolean("sshd", null, "testUseInsecureRandom", true);
    cfg.unset("cache", null, "directory");
    cfg.setString("gerrit", null, "basePath", "git");
    cfg.setBoolean("sendemail", null, "enable", true);
    cfg.setInt("sendemail", null, "threadPoolSize", 0);
    cfg.setInt("cache", "projects", "checkFrequency", 0);
    cfg.setInt("plugins", null, "checkFrequency", 0);

    cfg.setInt("sshd", null, "threads", 1);
    cfg.setInt("sshd", null, "commandStartThreads", 1);
    cfg.setInt("receive", null, "threadPoolSize", 1);
    cfg.setInt("index", null, "threads", 1);
  }

  private static Injector createTestInjector(Daemon daemon) throws Exception {
    Injector sysInjector = get(daemon, "sysInjector");
    Module module =
        new FactoryModule() {
          @Override
          protected void configure() {
            bind(AccountCreator.class);
            factory(PushOneCommit.Factory.class);
            install(InProcessProtocol.module());
            install(new NoSshModule());
            install(new AsyncReceiveCommits.Module());
          }
        };
    return sysInjector.createChildInjector(module);
  }

  @SuppressWarnings("unchecked")
  private static <T> T get(Object obj, String field)
      throws SecurityException, NoSuchFieldException, IllegalArgumentException,
          IllegalAccessException {
    Field f = obj.getClass().getDeclaredField(field);
    f.setAccessible(true);
    return (T) f.get(obj);
  }

  private static InetAddress getLocalHost() {
    return InetAddress.getLoopbackAddress();
  }

  private final Description desc;

  private Daemon daemon;
  private ExecutorService daemonService;
  private Injector testInjector;
  private String url;
  private InetSocketAddress sshdAddress;
  private InetSocketAddress httpAddress;

  private GerritServer(
      Description desc, Injector testInjector, Daemon daemon, ExecutorService daemonService) {
    this.desc = desc;
    this.testInjector = testInjector;
    this.daemon = daemon;
    this.daemonService = daemonService;

    Config cfg = testInjector.getInstance(Key.get(Config.class, GerritServerConfig.class));
    url = cfg.getString("gerrit", null, "canonicalWebUrl");
    URI uri = URI.create(url);

    sshdAddress = SocketUtil.resolve(cfg.getString("sshd", null, "listenAddress"), 0);
    httpAddress = new InetSocketAddress(uri.getHost(), uri.getPort());
  }

  String getUrl() {
    return url;
  }

  InetSocketAddress getSshdAddress() {
    return sshdAddress;
  }

  InetSocketAddress getHttpAddress() {
    return httpAddress;
  }

  Injector getTestInjector() {
    return testInjector;
  }

  Description getDescription() {
    return desc;
  }

  void stop() throws Exception {
    try {
      if (NoteDbMode.get().equals(NoteDbMode.CHECK)) {
        testInjector.getInstance(NoteDbChecker.class).rebuildAndCheckAllChanges();
      }
    } finally {
      daemon.getLifecycleManager().stop();
      if (daemonService != null) {
        System.out.println("Gerrit Server Shutdown");
        daemonService.shutdownNow();
        daemonService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
      }
      RepositoryCache.clear();
    }
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).addValue(desc).toString();
  }
}
