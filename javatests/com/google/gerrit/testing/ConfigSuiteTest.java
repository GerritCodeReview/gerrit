package com.google.gerrit.testing;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.google.gerrit.testing.ConfigSuite.AfterConfig;
import com.google.gerrit.testing.ConfigSuite.BeforeConfig;
import com.google.gerrit.testing.ConfigSuite.ConfigRule;
import org.eclipse.jgit.lib.Config;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.Statement;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ConfigSuiteTest {
  @Mock private ConfigBasedTestListener configBasedTestListener;
  private RunNotifier notifier;

  @Before
  public void setUp() {
    notifier = new RunNotifier();
    ConfigBasedTest.setConfigBasedTestListener(configBasedTestListener);
  }

  @After
  public void tearDown() {
    ConfigBasedTest.setConfigBasedTestListener(null);
  }

  @Test
  public void methodsExecuteInCorrectOrder() throws Exception {
    InOrder inOrder = Mockito.inOrder(configBasedTestListener);
    new ConfigSuite(ConfigBasedTest.class).run(notifier);
    inOrder.verify(configBasedTestListener, Mockito.times(1)).classRuleExecuted();
    inOrder.verify(configBasedTestListener, Mockito.times(1)).beforeClassExecuted();
    // default config
    inOrder.verify(configBasedTestListener, Mockito.times(1)).configRuleExectued();
    inOrder.verify(configBasedTestListener, Mockito.times(1)).beforeConfigExecuted();
    inOrder.verify(configBasedTestListener, Mockito.times(2)).testExecuted(any(), any(), any());
    inOrder.verify(configBasedTestListener, Mockito.times(1)).afterConfigExecuted();
    // first config
    inOrder.verify(configBasedTestListener, Mockito.times(1)).configRuleExectued();
    inOrder.verify(configBasedTestListener, Mockito.times(1)).beforeConfigExecuted();
    inOrder.verify(configBasedTestListener, Mockito.times(2)).testExecuted(any(), any(), any());
    inOrder.verify(configBasedTestListener, Mockito.times(1)).afterConfigExecuted();
    // second config
    inOrder.verify(configBasedTestListener, Mockito.times(1)).configRuleExectued();
    inOrder.verify(configBasedTestListener, Mockito.times(1)).beforeConfigExecuted();
    inOrder.verify(configBasedTestListener, Mockito.times(2)).testExecuted(any(), any(), any());
    inOrder.verify(configBasedTestListener, Mockito.times(1)).afterConfigExecuted();

    inOrder.verify(configBasedTestListener, Mockito.times(1)).afterClassExecuted();
  }

  @Test
  public void beforeClassExecutedOnce() throws Exception {
    new ConfigSuite(ConfigBasedTest.class).run(notifier);
    verify(configBasedTestListener, Mockito.times(1)).beforeClassExecuted();
  }

  @Test
  public void afterClassExecutedOnce() throws Exception {
    new ConfigSuite(ConfigBasedTest.class).run(notifier);
    verify(configBasedTestListener, Mockito.times(1)).afterClassExecuted();
  }

  @Test
  public void classRuleExecutedOnlyOnce() throws Exception {
    new ConfigSuite(ConfigBasedTest.class).run(notifier);
    verify(configBasedTestListener, Mockito.times(1)).classRuleExecuted();
  }

  @Test
  public void beforeConfigExecutedForEachConfig() throws Exception {
    new ConfigSuite(ConfigBasedTest.class).run(notifier);
    // default, firstConfig, secondConfig
    verify(configBasedTestListener, Mockito.times(3)).beforeConfigExecuted();
  }

  @Test
  public void afterConfigExecutedForEachConfig() throws Exception {
    new ConfigSuite(ConfigBasedTest.class).run(notifier);
    // default, firstConfig, secondConfig
    verify(configBasedTestListener, Mockito.times(3)).afterConfigExecuted();
  }

  @Test
  public void configRuleExecutedForEachConfig() throws Exception {
    new ConfigSuite(ConfigBasedTest.class).run(notifier);
    // default, firstConfig, secondConfig
    verify(configBasedTestListener, Mockito.times(3)).afterConfigExecuted();
  }

  @Test
  public void testsExecuteWithCorrectConfigAndName() throws Exception {
    new ConfigSuite(ConfigBasedTest.class).run(notifier);
    verify(configBasedTestListener, Mockito.times(6)).testExecuted(any(), any(), any());

    verify(configBasedTestListener, Mockito.times(1)).testExecuted("test1", "default", null);
    verify(configBasedTestListener, Mockito.times(1)).testExecuted("test2", "default", null);
    verify(configBasedTestListener, Mockito.times(1))
        .testExecuted("test1", "firstValue", "firstConfig");
    verify(configBasedTestListener, Mockito.times(1))
        .testExecuted("test2", "firstValue", "firstConfig");
    verify(configBasedTestListener, Mockito.times(1))
        .testExecuted("test1", "secondValue", "secondConfig");
    verify(configBasedTestListener, Mockito.times(1))
        .testExecuted("test2", "secondValue", "secondConfig");
  }

  @Test
  public void testResultWasSuccessful() throws Exception {
    Result result = new Result();
    notifier.addListener(result.createListener());
    new ConfigSuite(ConfigBasedTest.class).run(notifier);
    assertThat(result.wasSuccessful()).isTrue();
  }

  @Test
  public void failedTestResultNotMissed() throws Exception {
    Result result = new Result();
    notifier.addListener(result.createListener());
    new ConfigSuite(FailedConfigBasedTest.class).run(notifier);
    // 3 fails with 3 different configs
    assertThat(result.wasSuccessful()).isFalse();
    assertThat(result.getFailureCount()).isEqualTo(3);
  }

  interface ConfigBasedTestListener {
    void beforeClassExecuted();

    void afterClassExecuted();

    void classRuleExecuted();

    void configRuleExectued();

    void testExecuted(String testName, String testValue, String configName);

    void beforeConfigExecuted();

    void afterConfigExecuted();
  }

  public static class ConfigBasedTest {
    private static ConfigBasedTestListener listener;

    public static void setConfigBasedTestListener(ConfigBasedTestListener listener) {
      ConfigBasedTest.listener = listener;
    }

    @BeforeClass
    public static void beforeClass() {
      listener.beforeClassExecuted();
    }

    @AfterClass
    public static void afterClass() {
      listener.afterClassExecuted();
    }

    @ClassRule
    public static TestRule classRule =
        new TestRule() {
          @Override
          public Statement apply(Statement statement, Description description) {
            return new Statement() {
              @Override
              public void evaluate() throws Throwable {
                listener.classRuleExecuted();
                statement.evaluate();
              }
            };
          }
        };

    @ConfigRule
    public static TestRule configRule =
        new TestRule() {
          @Override
          public Statement apply(Statement statement, Description description) {
            return new Statement() {
              @Override
              public void evaluate() throws Throwable {
                listener.configRuleExectued();
                statement.evaluate();
              }
            };
          }
        };

    @BeforeConfig
    public static void beforeConfig() {
      listener.beforeConfigExecuted();
    }

    @AfterConfig
    public static void afterConfig() {
      listener.afterConfigExecuted();
    }

    @ConfigSuite.Config
    public static Config firstConfig() {
      Config cfg = new Config();
      cfg.setString("gerrit", null, "testValue", "firstValue");
      return cfg;
    }

    @ConfigSuite.Config
    public static Config secondConfig() {
      Config cfg = new Config();
      cfg.setString("gerrit", null, "testValue", "secondValue");
      return cfg;
    }

    @ConfigSuite.Parameter public Config config;
    @ConfigSuite.Name public String name;

    @Test
    public void test1() {
      String testValue = config.getString("gerrit", null, "testValue");
      listener.testExecuted("test1", testValue != null ? testValue : "default", name);
    }

    @Test
    public void test2() {
      String testValue = config.getString("gerrit", null, "testValue");
      listener.testExecuted("test2", testValue != null ? testValue : "default", name);
    }
  }

  public static class FailedConfigBasedTest extends ConfigBasedTest {
    @Test
    public void failedTest() {
      assertThat(true).isFalse();
    }
  }
}
