package com.google.gerrit.acceptance;

import org.eclipse.jgit.lib.Config;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.reflect.Field;

public class GerritFixtureRule implements TestRule {

  public GerritFixtureRule(Object sut, GerritFixtureInit init) {
    this.sut = sut;
    this.init = init;
  }

  private final Object sut;
  private GerritFixtureInit init;
  private GerritServer server;

  public Statement apply(final Statement base, Description description) {
    GerritConfig config = description.getAnnotation(GerritConfig.class);
    final String configStr = config != null
        ? config.value()
        : null;
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        Config cfg = null;
        if (configStr != null) {
          cfg = new Config();
          cfg.fromText(configStr);
        }
        server = GerritServer.start(cfg);
        server.getTestInjector().injectMembers(sut);
        // Can we set server smarter?
        // Guice injection?
        Field field = sut.getClass().getDeclaredField("server");
        if (field != null) {
          field.setAccessible(true);
          field.set(sut, server);
        }
        init.setUp();
        try {
          base.evaluate();
        } finally {
          server.stop();
          init.tearDown();
        }
      }
    };
  }
}
