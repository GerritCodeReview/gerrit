package com.google.gerrit.server.config;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Config;

@Singleton
public class GerritInstanceIdProvider implements Provider<String> {
  public static final String SECTION = "gerrit";
  public static final String KEY = "instanceId";

  private String instanceId;

  @Inject
  public GerritInstanceIdProvider(@GerritServerConfig Config cfg) {
    instanceId = cfg.getString(SECTION, null, KEY);
  }

  @Override
  public String get() {
    return instanceId;
  }
}
