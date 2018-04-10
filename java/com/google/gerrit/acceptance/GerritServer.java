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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.lucene.LuceneIndexModule;
import com.google.gerrit.pgm.Daemon;
import com.google.gerrit.pgm.Init;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.server.git.receive.AsyncReceiveCommits;
import com.google.gerrit.server.ssh.NoSshModule;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gerrit.server.util.SocketUtil;
import com.google.gerrit.server.util.SystemLog;
import com.google.gerrit.testing.FakeEmailSender;
import com.google.gerrit.testing.InMemoryDatabase;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.gerrit.testing.NoteDbChecker;
import com.google.gerrit.testing.NoteDbMode;
import com.google.gerrit.testing.SshMode;
import com.google.gerrit.testing.TempFileUtil;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.util.FS;

public class GerritServer implements AutoCloseable {
  public static class StartupException extends Exception {
    private static final long serialVersionUID = 1L;

    StartupException(String msg, Throwable cause) {
      super(msg, cause);
    }
  }

  @AutoValue
  public abstract static class Description {
    public static Description forTestClass(
        org.junit.runner.Description testDesc, String configName) {
      return new AutoValue_GerritServer_Description(
          testDesc,
          configName,
          !has(UseLocalDisk.class, testDesc.getTestClass()) && !forceLocalDisk(),
          !has(NoHttpd.class, testDesc.getTestClass()),
          has(Sandboxed.class, testDesc.getTestClass()),
          has(UseSsh.class, testDesc.getTestClass()),
          null, // @GerritConfig is only valid on methods.
          null, // @GerritConfigs is only valid on methods.
          null, // @GlobalPluginConfig is only valid on methods.
          null); // @GlobalPluginConfigs is only valid on methods.
    }

    public static Description forTestMethod(
        org.junit.runner.Description testDesc, String configName) {
      return new AutoValue_GerritServer_Description(
          testDesc,
          configName,
          (testDesc.getAnnotation(UseLocalDisk.class) == null
                  && !has(UseLocalDisk.class, testDesc.getTestClass()))
              && !forceLocalDisk(),
          testDesc.getAnnotation(NoHttpd.class) == null
              && !has(NoHttpd.class, testDesc.getTestClass()),
          testDesc.getAnnotation(Sandboxed.class) != null
              || has(Sandboxed.class, testDesc.getTestClass()),
          testDesc.getAnnotation(UseSsh.class) != null
              || has(UseSsh.class, testDesc.getTestClass()),
          testDesc.getAnnotation(GerritConfig.class),
          testDesc.getAnnotation(GerritConfigs.class),
          testDesc.getAnnotation(GlobalPluginConfig.class),
          testDesc.getAnnotation(GlobalPluginConfigs.class));
    }

    private static boolean has(Class<? extends Annotation> annotation, Class<?> clazz) {
      for (; clazz != null; clazz = clazz.getSuperclass()) {
        if (clazz.getAnnotation(annotation) != null) {
          return true;
        }
      }
      return false;
    }

    abstract org.junit.runner.Description testDescription();

    @Nullable
    abstract String configName();

    abstract boolean memory();

    abstract boolean httpd();

    abstract boolean sandboxed();

    abstract boolean useSshAnnotation();

    boolean useSsh() {
      return useSshAnnotation() && SshMode.useSsh();
    }

    @Nullable
    abstract GerritConfig config();

    @Nullable
    abstract GerritConfigs configs();

    @Nullable
    abstract GlobalPluginConfig pluginConfig();

    @Nullable
    abstract GlobalPluginConfigs pluginConfigs();

    private void checkValidAnnotations() {
      if (configs() != null && config() != null) {
        throw new IllegalStateException("Use either @GerritConfigs or @GerritConfig not both");
      }
      if (pluginConfigs() != null && pluginConfig() != null) {
        throw new IllegalStateException(
            "Use either @GlobalPluginConfig or @GlobalPluginConfigs not both");
      }
      if ((pluginConfigs() != null || pluginConfig() != null) && memory()) {
        throw new IllegalStateException("Must use @UseLocalDisk with @GlobalPluginConfig(s)");
      }
    }

    private Config buildConfig(Config baseConfig) {
      if (configs() != null) {
        return ConfigAnnotationParser.parse(baseConfig, configs());
      } else if (config() != null) {
        return ConfigAnnotationParser.parse(baseConfig, config());
      } else {
        return baseConfig;
      }
    }

    private Map<String, Config> buildPluginConfigs() {
      if (pluginConfigs() != null) {
        return ConfigAnnotationParser.parse(pluginConfigs());
      } else if (pluginConfig() != null) {
        return ConfigAnnotationParser.parse(pluginConfig());
      }
      return new HashMap<>();
    }
  }

  private static boolean forceLocalDisk() {
    String value = Strings.nullToEmpty(System.getenv("GERRIT_FORCE_LOCAL_DISK"));
    if (value.isEmpty()) {
      value = Strings.nullToEmpty(System.getProperty("gerrit.forceLocalDisk"));
    }
    switch (value.trim().toLowerCase(Locale.US)) {
      case "1":
      case "yes":
      case "true":
        return true;
      default:
        return false;
    }
  }

  /**
   * Initializes on-disk site but does not start server.
   *
   * @param desc server description
   * @param baseConfig default config values; merged with config from {@code desc} and then written
   *     into {@code site/etc/gerrit.config}.
   * @param site temp directory where site will live.
   * @throws Exception
   */
  public static void init(Description desc, Config baseConfig, Path site) throws Exception {
    checkArgument(!desc.memory(), "can't initialize site path for in-memory test: %s", desc);
    Config cfg = desc.buildConfig(baseConfig);
    Map<String, Config> pluginConfigs = desc.buildPluginConfigs();

    MergeableFileBasedConfig gerritConfig =
        new MergeableFileBasedConfig(
            site.resolve("etc").resolve("gerrit.config").toFile(), FS.DETECTED);
    gerritConfig.load();
    gerritConfig.merge(cfg);
    mergeTestConfig(gerritConfig);
    gerritConfig.save();

    Init init = new Init();
    int rc =
        init.main(
            new String[] {
              "-d", site.toString(), "--batch", "--no-auto-start", "--skip-plugins",
            });
    if (rc != 0) {
      throw new RuntimeException("Couldn't initialize site");
    }

    for (String pluginName : pluginConfigs.keySet()) {
      MergeableFileBasedConfig pluginCfg =
          new MergeableFileBasedConfig(
              site.resolve("etc").resolve(pluginName + ".config").toFile(), FS.DETECTED);
      pluginCfg.load();
      pluginCfg.merge(pluginConfigs.get(pluginName));
      pluginCfg.save();
    }
  }

  /**
   * Initializes new Gerrit site and returns started server.
   *
   * <p>A new temporary directory for the site will be created with {@link TempFileUtil}, even in
   * the server is otherwise configured in-memory. Closing the server stops the daemon but does not
   * delete the temporary directory. Callers may either get the directory with {@link
   * #getSitePath()} and delete it manually, or call {@link TempFileUtil#cleanup()}.
   *
   * @param desc server description.
   * @param baseConfig default config values; merged with config from {@code desc}.
   * @param testSysModules additional Guice modules to use.
   * @return started server.
   * @throws Exception
   */
  public static GerritServer initAndStart(
      Description desc, Config baseConfig, @Nullable List<Module> testSysModules) throws Exception {
    Path site = TempFileUtil.createTempDirectory().toPath();
    baseConfig = new Config(baseConfig);
    baseConfig.setString("gerrit", null, "basePath", site.resolve("git").toString());
    baseConfig.setString("gerrit", null, "tempSiteDir", site.toString());
    try {
      if (!desc.memory()) {
        init(desc, baseConfig, site);
      }
      return start(desc, baseConfig, site, testSysModules, null, null);
    } catch (Exception e) {
      TempFileUtil.recursivelyDelete(site.toFile());
      throw e;
    }
  }

  /**
   * Starts Gerrit server from existing on-disk site.
   *
   * @param desc server description.
   * @param baseConfig default config values; merged with config from {@code desc}.
   * @param site existing temporary directory for site. Required, but may be empty, for in-memory
   *     servers. For on-disk servers, assumes that {@link #init} was previously called to
   *     initialize this directory. Can be retrieved from the returned instance via {@link
   *     #getSitePath()}.
   * @param testSysModules optional additional modules to add to the system injector.
   * @param inMemoryRepoManager {@link InMemoryRepositoryManager} that should be used if the site is
   *     started in memory
   * @param inMemoryDatabaseInstance {@link com.google.gerrit.testing.InMemoryDatabase.Instance}
   *     that should be used if the site is started in memory
   * @param additionalArgs additional command-line arguments for the daemon program; only allowed if
   *     the test is not in-memory.
   * @return started server.
   * @throws Exception
   */
  public static GerritServer start(
      Description desc,
      Config baseConfig,
      Path site,
      @Nullable List<Module> testSysModules,
      @Nullable InMemoryRepositoryManager inMemoryRepoManager,
      @Nullable InMemoryDatabase.Instance inMemoryDatabaseInstance,
      String... additionalArgs)
      throws Exception {
    checkArgument(site != null, "site is required (even for in-memory server");
    desc.checkValidAnnotations();
    Logger.getLogger("com.google.gerrit").setLevel(Level.DEBUG);
    CyclicBarrier serverStarted = new CyclicBarrier(2);
    Daemon daemon =
        new Daemon(
            () -> {
              try {
                serverStarted.await();
              } catch (InterruptedException | BrokenBarrierException e) {
                throw new RuntimeException(e);
              }
            },
            site);
    daemon.setEmailModuleForTesting(new FakeEmailSender.Module());
    daemon.setAdditionalSysModulesForTesting(testSysModules);
    daemon.setEnableSshd(desc.useSsh());
    daemon.setSlave(isSlave(baseConfig));

    if (desc.memory()) {
      checkArgument(additionalArgs.length == 0, "cannot pass args to in-memory server");
      return startInMemory(
          desc, site, baseConfig, daemon, inMemoryRepoManager, inMemoryDatabaseInstance);
    }
    return startOnDisk(desc, site, daemon, serverStarted, additionalArgs);
  }

  private static GerritServer startInMemory(
      Description desc,
      Path site,
      Config baseConfig,
      Daemon daemon,
      @Nullable InMemoryRepositoryManager inMemoryRepoManager,
      @Nullable InMemoryDatabase.Instance inMemoryDatabaseInstance)
      throws Exception {
    Config cfg = desc.buildConfig(baseConfig);
    mergeTestConfig(cfg);
    // Set the log4j configuration to an invalid one to prevent system logs
    // from getting configured and creating log files.
    System.setProperty(SystemLog.LOG4J_CONFIGURATION, "invalidConfiguration");
    cfg.setBoolean("httpd", null, "requestLog", false);
    cfg.setBoolean("sshd", null, "requestLog", false);
    cfg.setBoolean("index", "lucene", "testInmemory", true);
    cfg.setString("gitweb", null, "cgi", "");
    daemon.setEnableHttpd(desc.httpd());
    daemon.setLuceneModule(LuceneIndexModule.singleVersionAllLatest(0, isSlave(baseConfig)));
    daemon.setDatabaseForTesting(
        ImmutableList.<Module>of(
            new InMemoryTestingDatabaseModule(
                cfg, site, inMemoryRepoManager, inMemoryDatabaseInstance)));
    daemon.start();
    return new GerritServer(desc, null, createTestInjector(daemon), daemon, null);
  }

  private static boolean isSlave(Config baseConfig) {
    return baseConfig.getBoolean("container", "slave", false);
  }

  private static GerritServer startOnDisk(
      Description desc,
      Path site,
      Daemon daemon,
      CyclicBarrier serverStarted,
      String[] additionalArgs)
      throws Exception {
    checkNotNull(site);
    ExecutorService daemonService = Executors.newSingleThreadExecutor();
    String[] args =
        Stream.concat(
                Stream.of(
                    "-d", site.toString(), "--headless", "--console-log", "--show-stack-trace"),
                Arrays.stream(additionalArgs))
            .toArray(String[]::new);
    @SuppressWarnings("unused")
    Future<?> possiblyIgnoredError =
        daemonService.submit(
            () -> {
              int rc = daemon.main(args);
              if (rc != 0) {
                System.err.println("Failed to start Gerrit daemon");
                serverStarted.reset();
              }
              return null;
            });
    try {
      serverStarted.await();
    } catch (BrokenBarrierException e) {
      daemon.stop();
      throw new StartupException("Failed to start Gerrit daemon; see log", e);
    }
    System.out.println("Gerrit Server Started");

    return new GerritServer(desc, site, createTestInjector(daemon), daemon, daemonService);
  }

  private static void mergeTestConfig(Config cfg) {
    String forceEphemeralPort = String.format("%s:0", getLocalHost().getHostName());
    String url = "http://" + forceEphemeralPort + "/";
    cfg.setString("gerrit", null, "canonicalWebUrl", url);
    cfg.setString("httpd", null, "listenUrl", url);

    if (cfg.getString("sshd", null, "listenAddress") == null) {
      cfg.setString("sshd", null, "listenAddress", forceEphemeralPort);
    }
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
    cfg.setBoolean("index", null, "reindexAfterRefUpdate", false);

    NoteDbMode.newNotesMigrationFromEnv().setConfigValues(cfg);
  }

  private static Injector createTestInjector(Daemon daemon) throws Exception {
    Injector sysInjector = get(daemon, "sysInjector");
    Module module =
        new FactoryModule() {
          @Override
          protected void configure() {
            bindConstant().annotatedWith(SshEnabled.class).to(daemon.getEnableSshd());
            bind(AccountCreator.class);
            factory(PushOneCommit.Factory.class);
            install(InProcessProtocol.module());
            install(new NoSshModule());
            install(new AsyncReceiveCommits.Module());
            factory(ProjectResetter.Builder.Factory.class);
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
  private final Path sitePath;

  private Daemon daemon;
  private ExecutorService daemonService;
  private Injector testInjector;
  private String url;
  private InetSocketAddress sshdAddress;
  private InetSocketAddress httpAddress;

  private GerritServer(
      Description desc,
      @Nullable Path sitePath,
      Injector testInjector,
      Daemon daemon,
      @Nullable ExecutorService daemonService) {
    this.desc = checkNotNull(desc);
    this.sitePath = sitePath;
    this.testInjector = checkNotNull(testInjector);
    this.daemon = checkNotNull(daemon);
    this.daemonService = daemonService;

    Config cfg = testInjector.getInstance(Key.get(Config.class, GerritServerConfig.class));
    url = cfg.getString("gerrit", null, "canonicalWebUrl");
    URI uri = URI.create(url);

    String addr = cfg.getString("sshd", null, "listenAddress");
    // We do not use InitSshd.isOff to avoid coupling GerritServer to the SSH code.
    if (!"off".equalsIgnoreCase(addr)) {
      sshdAddress = SocketUtil.resolve(cfg.getString("sshd", null, "listenAddress"), 0);
    }
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

  public Injector getTestInjector() {
    return testInjector;
  }

  Description getDescription() {
    return desc;
  }

  public static GerritServer restartAsSlave(GerritServer server) throws Exception {
    checkState(server.desc.sandboxed(), "restarting as slave requires @Sandboxed");

    Path site = server.testInjector.getInstance(Key.get(Path.class, SitePath.class));

    Config cfg = server.testInjector.getInstance(Key.get(Config.class, GerritServerConfig.class));
    cfg.setBoolean("container", null, "slave", true);

    InMemoryRepositoryManager inMemoryRepoManager = null;
    if (hasBinding(server.testInjector, InMemoryRepositoryManager.class)) {
      inMemoryRepoManager = server.testInjector.getInstance(InMemoryRepositoryManager.class);
    }

    InMemoryDatabase.Instance dbInstance = null;
    if (hasBinding(server.testInjector, InMemoryDatabase.class)) {
      InMemoryDatabase inMemoryDatabase = server.testInjector.getInstance(InMemoryDatabase.class);
      dbInstance = inMemoryDatabase.getDbInstance();
      dbInstance.setKeepOpen(true);
    }
    try {
      server.close();
      server.daemon.stop();
      return start(server.desc, cfg, site, null, inMemoryRepoManager, dbInstance);
    } finally {
      if (dbInstance != null) {
        dbInstance.setKeepOpen(false);
      }
    }
  }

  private static boolean hasBinding(Injector injector, Class<?> clazz) {
    return injector.getExistingBinding(Key.get(clazz)) != null;
  }

  @Override
  public void close() throws Exception {
    try {
      checkNoteDbState();
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

  public Path getSitePath() {
    return sitePath;
  }

  private void checkNoteDbState() throws Exception {
    NoteDbMode mode = NoteDbMode.get();
    if (mode != NoteDbMode.CHECK && mode != NoteDbMode.PRIMARY) {
      return;
    }
    NoteDbChecker checker = testInjector.getInstance(NoteDbChecker.class);
    OneOffRequestContext oneOffRequestContext =
        testInjector.getInstance(OneOffRequestContext.class);
    try (ManualRequestContext ctx = oneOffRequestContext.open()) {
      if (mode == NoteDbMode.CHECK) {
        checker.rebuildAndCheckAllChanges();
      } else if (mode == NoteDbMode.PRIMARY) {
        checker.assertNoReviewDbChanges(desc.testDescription());
      }
    }
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).addValue(desc).toString();
  }
}
