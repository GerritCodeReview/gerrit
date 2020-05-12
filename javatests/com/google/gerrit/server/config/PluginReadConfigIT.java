package com.google.gerrit.server.config;

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import org.junit.Test;

@TestPlugin(
    name = "read-config",
    sysModule = "com.google.gerrit.server.config.PluginReadConfigIT$Module")
public class PluginReadConfigIT extends LightweightPluginDaemonTest {

  public static class Module extends AbstractModule {

    @Override
    protected void configure() {
      bind(TestConfigLoader.class).in(Scopes.SINGLETON);
    }
  }

  @Test
  public void gerritInstanceIdShouldBeAvailable() {
  }
}
