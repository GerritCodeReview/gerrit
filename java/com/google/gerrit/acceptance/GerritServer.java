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
import static com.google.common.base.Preconditions.checkState;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest.TestTicker;
import com.google.gerrit.acceptance.FakeGroupAuditService.FakeGroupAuditServiceModule;
import com.google.gerrit.acceptance.ReindexGroupsAtStartup.ReindexGroupsAtStartupModule;
import com.google.gerrit.acceptance.ReindexProjectsAtStartup.ReindexProjectsAtStartupModule;
import com.google.gerrit.acceptance.config.ConfigAnnotationParser;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.config.GerritConfigs;
import com.google.gerrit.acceptance.config.GlobalPluginConfig;
import com.google.gerrit.acceptance.config.GlobalPluginConfigs;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.account.AccountOperationsImpl;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperationsImpl;
import com.google.gerrit.acceptance.testsuite.change.PerCommentOperationsImpl;
import com.google.gerrit.acceptance.testsuite.change.PerDraftCommentOperationsImpl;
import com.google.gerrit.acceptance.testsuite.change.PerPatchsetOperationsImpl;
import com.google.gerrit.acceptance.testsuite.change.PerRobotCommentOperationsImpl;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.acceptance.testsuite.group.GroupOperationsImpl;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperationsImpl;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperationsImpl;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.index.IndexType;
import com.google.gerrit.index.testing.FakeIndexModule;
import com.google.gerrit.lucene.LuceneIndexModule;
import com.google.gerrit.pgm.Daemon;
import com.google.gerrit.pgm.Init;
import com.google.gerrit.server.config.GerritRuntime;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.server.experiments.ConfigExperimentFeatures.ConfigExperimentFeaturesModule;
import com.google.gerrit.server.git.receive.AsyncReceiveCommits.AsyncReceiveCommitsModule;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.index.options.AutoFlush;
import com.google.gerrit.server.schema.JdbcAccountPatchReviewStore;
import com.google.gerrit.server.ssh.NoSshModule;
import com.google.gerrit.server.util.ReplicaUtil;
import com.google.gerrit.server.util.SocketUtil;
import com.google.gerrit.server.util.SystemLog;
import com.google.gerrit.testing.FakeEmailSender.FakeEmailSenderModule;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.gerrit.testing.SshMode;
import com.google.gerrit.testing.TestLoggingActivator;
import com.google.inject.AbstractModule;
import com.google.inject.BindingAnnotation;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.OptionalBinder;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.util.FS;
import org.junit.rules.TemporaryFolder;

public class GerritServer implements AutoCloseable {
  public static class StartupException extends Exception {
    private static final long serialVersionUID = 1L;

    StartupException(String msg, Throwable cause) {
      super(msg, cause);
    }
  }

  /** Marker on {@link InetSocketAddress} for test SSH server. */
  @Retention(RUNTIME)
  @BindingAnnotation
  public @interface TestSshServerAddress {}

  @AutoValue
  public abstract static class Description {
    public static Description forTestClass(
        org.junit.runner.Description testDesc, String configName) {
      VerifyNoPiiInChangeNotes verifyNoPiiInChangeNotes =
          get(VerifyNoPiiInChangeNotes.class, testDesc.getTestClass());
      return new AutoValue_GerritServer_Description(
          testDesc,
          configName,
          !has(UseLocalDisk.class, testDesc.getTestClass()) && !forceLocalDisk(),
          !has(NoHttpd.class, testDesc.getTestClass()),
          has(Sandboxed.class, testDesc.getTestClass()),
          has(SkipProjectClone.class, testDesc.getTestClass()),
          has(UseSsh.class, testDesc.getTestClass()),
          verifyNoPiiInChangeNotes != null && verifyNoPiiInChangeNotes.value(),
          false, // @UseSystemTime is only valid on methods.
          get(UseClockStep.class, testDesc.getTestClass()),
          get(UseTimezone.class, testDesc.getTestClass()),
          null, // @GerritConfig is only valid on methods.
          null, // @GerritConfigs is only valid on methods.
          null, // @GlobalPluginConfig is only valid on methods.
          null); // @GlobalPluginConfigs is only valid on methods.
    }

    public static Description forTestMethod(
        org.junit.runner.Description testDesc, String configName) {
      UseClockStep useClockStep = testDesc.getAnnotation(UseClockStep.class);
      if (testDesc.getAnnotation(UseSystemTime.class) == null && useClockStep == null) {
        // Only read the UseClockStep from the class if on method level neither @UseSystemTime nor
        // @UseClockStep have been used.
        // If the method defines @UseSystemTime or @UseClockStep it should overwrite @UseClockStep
        // on class level.
        useClockStep = get(UseClockStep.class, testDesc.getTestClass());
      }
      VerifyNoPiiInChangeNotes verifyNoPiiInChangeNotes =
          testDesc.getAnnotation(VerifyNoPiiInChangeNotes.class);
      if (verifyNoPiiInChangeNotes == null) {
        verifyNoPiiInChangeNotes = get(VerifyNoPiiInChangeNotes.class, testDesc.getTestClass());
      }

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
          testDesc.getAnnotation(SkipProjectClone.class) != null
              || has(SkipProjectClone.class, testDesc.getTestClass()),
          testDesc.getAnnotation(UseSsh.class) != null
              || has(UseSsh.class, testDesc.getTestClass()),
          verifyNoPiiInChangeNotes != null && verifyNoPiiInChangeNotes.value(),
          testDesc.getAnnotation(UseSystemTime.class) != null,
          useClockStep,
          testDesc.getAnnotation(UseTimezone.class) != null
              ? testDesc.getAnnotation(UseTimezone.class)
              : get(UseTimezone.class, testDesc.getTestClass()),
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

    @Nullable
    private static <T extends Annotation> T get(Class<T> annotation, Class<?> clazz) {
      for (; clazz != null; clazz = clazz.getSuperclass()) {
        if (clazz.getAnnotation(annotation) != null) {
          return clazz.getAnnotation(annotation);
        }
      }
      return null;
    }

    abstract org.junit.runner.Description testDescription();

    @Nullable
    abstract String configName();

    abstract boolean memory();

    abstract boolean httpd();

    abstract boolean sandboxed();

    abstract boolean skipProjectClone();

    abstract boolean useSshAnnotation();

    abstract boolean verifyNoPiiInChangeNotes();

    boolean useSsh() {
      return useSshAnnotation() && SshMode.useSsh();
    }

    abstract boolean useSystemTime();

    @Nullable
    abstract UseClockStep useClockStep();

    @Nullable
    abstract UseTimezone useTimezone();

    @Nullable
    abstract GerritConfig config();

    @Nullable
    abstract GerritConfigs configs();

    @Nullable
    abstract GlobalPluginConfig pluginConfig();

    @Nullable
    abstract GlobalPluginConfigs pluginConfigs();

    private void checkValidAnnotations() {
      if (useClockStep() != null && useSystemTime()) {
        throw new IllegalStateException("Use either @UseClockStep or @UseSystemTime, not both");
      }
      if (configs() != null && config() != null) {
        throw new IllegalStateException("Use either @GerritConfigs or @GerritConfig, not both");
      }
      if (pluginConfigs() != null && pluginConfig() != null) {
        throw new IllegalStateException(
            "Use either @GlobalPluginConfig or @GlobalPluginConfigs, not both");
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
    String configuredIndexBackend = cfg.getString("index", null, "type");
    if (configuredIndexBackend == null) {
      // Propagate index type to pgms that run off of the gerrit.config file on local disk.
      IndexType indexType = IndexType.fromEnvironment().orElse(new IndexType("fake"));
      gerritConfig.setString("index", null, "type", indexType.isLucene() ? "lucene" : "fake");
    }
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
   * <p>A new temporary directory for the site will be created with {@code temporaryFolder}, even in
   * the server is otherwise configured in-memory. Closing the server stops the daemon but does not
   * delete the temporary directory..
   *
   * @param temporaryFolder helper rule for creating site directories.
   * @param desc server description.
   * @param baseConfig default config values; merged with config from {@code desc}.
   * @param testSysModule additional Guice module to use.
   * @param testSshModule additional Guice module to use.
   * @return started server.
   */
  public static GerritServer initAndStart(
      TemporaryFolder temporaryFolder,
      Description desc,
      Config baseConfig,
      @Nullable Module testSysModule,
      @Nullable Module testAuditModule,
      @Nullable Module testSshModule)
      throws Exception {
    Path site = temporaryFolder.newFolder().toPath();
    try {
      if (!desc.memory()) {
        init(desc, baseConfig, site);
      }
      return start(desc, baseConfig, site, testSysModule, testAuditModule, testSshModule, null);
    } catch (Exception e) {
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
   * @param testSysModule optional additional module to add to the system injector.
   * @param testSshModule optional additional module to add to the ssh injector.
   * @param inMemoryRepoManager {@link InMemoryRepositoryManager} that should be used if the site is
   *     started in memory
   * @param additionalArgs additional command-line arguments for the daemon program; only allowed if
   *     the test is not in-memory.
   * @return started server.
   */
  public static GerritServer start(
      Description desc,
      Config baseConfig,
      Path site,
      @Nullable Module testSysModule,
      @Nullable Module testAuditModule,
      @Nullable Module testSshModule,
      @Nullable InMemoryRepositoryManager inMemoryRepoManager,
      String... additionalArgs)
      throws Exception {
    checkArgument(site != null, "site is required (even for in-memory server");
    desc.checkValidAnnotations();
    TestLoggingActivator.configureLogging();
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
    daemon.setEmailModuleForTesting(new FakeEmailSenderModule());
    daemon.setAuditEventModuleForTesting(
        MoreObjects.firstNonNull(testAuditModule, new FakeGroupAuditServiceModule()));
    if (testSysModule != null) {
      daemon.addAdditionalSysModuleForTesting(testSysModule);
    }
    if (testSshModule != null) {
      daemon.addAdditionalSshModuleForTesting(testSshModule);
    }
    daemon.setEnableSshd(desc.useSsh());
    daemon.addAdditionalSysModuleForTesting(
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(CommitValidationListener.class)
                .annotatedWith(Exports.named("object-visibility-listener"))
                .to(GitObjectVisibilityChecker.class);
          }
        });
    daemon.addAdditionalSysModuleForTesting(
        new AbstractModule() {
          @Override
          protected void configure() {
            super.configure();
            // GerritServer isn't restarted between tests. TestTicker allows to replace actual
            // Ticker in tests without restarting server and transparently for other code.
            // Alternative option with Provider<Ticker> is less convinient, because it affects how
            // gerrit code should be written - i.e. Ticker must not be stored in fields and must
            // always be obtained from the provider.
            TestTicker testTicker = new TestTicker();
            OptionalBinder.newOptionalBinder(binder(), Ticker.class)
                .setBinding()
                .toInstance(testTicker);
            bind(TestTicker.class).toInstance(testTicker);
          }
        });
    daemon.setEnableHttpd(desc.httpd());
    // Assure that SSHD is enabled if HTTPD is not required, otherwise the Gerrit server would not
    // even start.
    daemon.setEnableSshd(!desc.httpd() || desc.useSsh());
    daemon.setReplica(
        ReplicaUtil.isReplica(baseConfig) || ReplicaUtil.isReplica(desc.buildConfig(baseConfig)));

    if (desc.memory()) {
      checkArgument(additionalArgs.length == 0, "cannot pass args to in-memory server");
      return startInMemory(desc, site, baseConfig, daemon, inMemoryRepoManager);
    }
    return startOnDisk(desc, site, daemon, serverStarted, additionalArgs);
  }

  private static GerritServer startInMemory(
      Description desc,
      Path site,
      Config baseConfig,
      Daemon daemon,
      @Nullable InMemoryRepositoryManager inMemoryRepoManager)
      throws Exception {
    Config cfg = desc.buildConfig(baseConfig);
    mergeTestConfig(cfg);
    // Set the log4j configuration to an invalid one to prevent system logs
    // from getting configured and creating log files.
    System.setProperty(SystemLog.LOG4J_CONFIGURATION, "invalidConfiguration");
    cfg.setBoolean("httpd", null, "requestLog", false);
    cfg.setBoolean("sshd", null, "requestLog", false);
    cfg.setBoolean("index", "lucene", "testInmemory", true);
    cfg.setBoolean("index", null, "onlineUpgrade", false);
    cfg.setString("gitweb", null, "cgi", "");
    cfg.setString(
        "accountPatchReviewDb", null, "url", JdbcAccountPatchReviewStore.TEST_IN_MEMORY_URL);

    String configuredIndexBackend = cfg.getString("index", null, "type");
    IndexType indexType;
    if (configuredIndexBackend != null) {
      // Explicitly configured index backend from gerrit.config trumps any other ways to configure
      // index backends so that Reindex tests can be explicit about the backend they want to test
      // against.
      indexType = new IndexType(configuredIndexBackend);
    } else {
      // Allow configuring the index backend based on sys/env variables so that integration tests
      // can be run against different index backends.
      indexType = IndexType.fromEnvironment().orElse(new IndexType("fake"));
    }
    if (indexType.isLucene()) {
      daemon.setIndexModule(
          LuceneIndexModule.singleVersionAllLatest(
              0, ReplicaUtil.isReplica(baseConfig), AutoFlush.ENABLED));
    } else {
      daemon.setIndexModule(FakeIndexModule.latestVersion(false));
    }

    daemon.setEnableHttpd(desc.httpd());
    daemon.setInMemory(true);
    daemon.setDatabaseForTesting(
        ImmutableList.of(
            new InMemoryTestingDatabaseModule(cfg, site, inMemoryRepoManager),
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(GerritRuntime.class).toInstance(GerritRuntime.DAEMON);
              }
            },
            new ConfigExperimentFeaturesModule()));
    daemon.addAdditionalSysModuleForTesting(
        new ReindexProjectsAtStartupModule(), new ReindexGroupsAtStartupModule());
    daemon.start();
    return new GerritServer(desc, null, createTestInjector(daemon), daemon, null);
  }

  private static GerritServer startOnDisk(
      Description desc,
      Path site,
      Daemon daemon,
      CyclicBarrier serverStarted,
      String[] additionalArgs)
      throws Exception {
    requireNonNull(site);
    daemon.addAdditionalSysModuleForTesting(
        new ReindexProjectsAtStartupModule(), new ReindexGroupsAtStartupModule());
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

    if (cfg.getString("gerrit", null, "canonicalWebUrl") == null) {
      cfg.setString("gerrit", null, "canonicalWebUrl", url);
    }
    if (cfg.getString("httpd", null, "listenUrl") == null) {
      cfg.setString("httpd", null, "listenUrl", url);
    }
    if (cfg.getString("sshd", null, "listenAddress") == null) {
      cfg.setString("sshd", null, "listenAddress", forceEphemeralPort);
    }
    cfg.setBoolean("sshd", null, "testUseInsecureRandom", true);
    cfg.unset("cache", null, "directory");
    cfg.setString("gerrit", null, "basePath", "git");
    cfg.setBoolean("sendemail", null, "enable", true);
    cfg.setInt("sendemail", null, "threadPoolSize", 0);
    cfg.setInt("plugins", null, "checkFrequency", 0);

    cfg.setInt("sshd", null, "threads", 1);
    cfg.setInt("sshd", null, "commandStartThreads", 1);
    cfg.setInt("receive", null, "threadPoolSize", 1);
    cfg.setInt("index", null, "threads", 1);
    if (cfg.getString("index", null, "mergeabilityComputationBehavior") == null) {
      cfg.setString("index", null, "mergeabilityComputationBehavior", "NEVER");
    }
  }

  private static Injector createTestInjector(Daemon daemon) throws Exception {
    Injector sysInjector = getInjector(daemon, "sysInjector");
    Module module =
        new FactoryModule() {
          @Override
          protected void configure() {
            bindConstant().annotatedWith(SshEnabled.class).to(daemon.getEnableSshd());
            bind(AccountCreator.class);
            bind(AccountOperations.class).to(AccountOperationsImpl.class);
            bind(GroupOperations.class).to(GroupOperationsImpl.class);
            bind(ProjectOperations.class).to(ProjectOperationsImpl.class);
            bind(RequestScopeOperations.class).to(RequestScopeOperationsImpl.class);
            bind(ChangeOperations.class).to(ChangeOperationsImpl.class);
            factory(PerPatchsetOperationsImpl.Factory.class);
            factory(PerCommentOperationsImpl.Factory.class);
            factory(PerDraftCommentOperationsImpl.Factory.class);
            factory(PerRobotCommentOperationsImpl.Factory.class);
            factory(PushOneCommit.Factory.class);
            install(InProcessProtocol.module());
            install(new NoSshModule());
            install(new AsyncReceiveCommitsModule());
            factory(ProjectResetter.Builder.Factory.class);
          }

          @Provides
          @Singleton
          @Nullable
          @TestSshServerAddress
          InetSocketAddress getSshAddress(@GerritServerConfig Config cfg) {
            String addr = cfg.getString("sshd", null, "listenAddress");
            // We do not use InitSshd.isOff to avoid coupling GerritServer to the SSH code.
            return !"off".equalsIgnoreCase(addr)
                ? SocketUtil.resolve(cfg.getString("sshd", null, "listenAddress"), 0)
                : null;
          }
        };
    return sysInjector.createChildInjector(module);
  }

  private static Injector getInjector(Object obj, String field)
      throws SecurityException, NoSuchFieldException, IllegalArgumentException,
          IllegalAccessException {
    Field f = obj.getClass().getDeclaredField(field);
    f.setAccessible(true);
    Object v = f.get(obj);
    checkArgument(v instanceof Injector, "not an Injector: %s", v);
    return (Injector) f.get(obj);
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
  private InetSocketAddress httpAddress;

  private GerritServer(
      Description desc,
      @Nullable Path sitePath,
      Injector testInjector,
      Daemon daemon,
      @Nullable ExecutorService daemonService) {
    this.desc = requireNonNull(desc);
    this.sitePath = sitePath;
    this.testInjector = requireNonNull(testInjector);
    this.daemon = requireNonNull(daemon);
    this.daemonService = daemonService;

    Config cfg = testInjector.getInstance(Key.get(Config.class, GerritServerConfig.class));
    url = cfg.getString("gerrit", null, "canonicalWebUrl");
    URI uri = URI.create(url);
    httpAddress = new InetSocketAddress(uri.getHost(), uri.getPort());
  }

  public String getUrl() {
    return url;
  }

  InetSocketAddress getHttpAddress() {
    return httpAddress;
  }

  public Injector getTestInjector() {
    return testInjector;
  }

  public Injector getHttpdInjector() {
    return daemon.getHttpdInjector();
  }

  Description getDescription() {
    return desc;
  }

  public static GerritServer restartAsSlave(GerritServer server) throws Exception {
    checkState(server.desc.sandboxed(), "restarting as slave requires @Sandboxed");

    Path site = server.testInjector.getInstance(Key.get(Path.class, SitePath.class));

    Config cfg = server.testInjector.getInstance(Key.get(Config.class, GerritServerConfig.class));
    cfg.setBoolean("container", null, "replica", true);

    InMemoryRepositoryManager inMemoryRepoManager = null;
    if (hasBinding(server.testInjector, InMemoryRepositoryManager.class)) {
      inMemoryRepoManager = server.testInjector.getInstance(InMemoryRepositoryManager.class);
    }

    server.close();
    server.daemon.stop();
    return start(server.desc, cfg, site, null, null, null, inMemoryRepoManager);
  }

  public static GerritServer restart(
      GerritServer server, @Nullable Module testSysModule, @Nullable Module testSshModule)
      throws Exception {
    checkState(server.desc.sandboxed(), "restarting as slave requires @Sandboxed");
    Config cfg = server.testInjector.getInstance(Key.get(Config.class, GerritServerConfig.class));
    Path site = server.testInjector.getInstance(Key.get(Path.class, SitePath.class));

    InMemoryRepositoryManager inMemoryRepoManager = null;
    if (hasBinding(server.testInjector, InMemoryRepositoryManager.class)) {
      inMemoryRepoManager = server.testInjector.getInstance(InMemoryRepositoryManager.class);
    }

    server.close();
    server.daemon.stop();
    return start(server.desc, cfg, site, testSysModule, null, testSshModule, inMemoryRepoManager);
  }

  private static boolean hasBinding(Injector injector, Class<?> clazz) {
    return injector.getExistingBinding(Key.get(clazz)) != null;
  }

  @Override
  public void close() throws Exception {
    daemon.getLifecycleManager().stop();
    if (daemonService != null) {
      System.out.println("Gerrit Server Shutdown");
      daemonService.shutdownNow();
      daemonService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
    }
    RepositoryCache.clear();
  }

  public Path getSitePath() {
    return sitePath;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).addValue(desc).toString();
  }

  public boolean isReplica() {
    return daemon.isReplica();
  }
}
