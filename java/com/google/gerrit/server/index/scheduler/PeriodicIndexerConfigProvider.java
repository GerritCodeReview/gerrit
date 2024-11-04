package com.google.gerrit.server.index.scheduler;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.index.IndexDefinition;
import com.google.gerrit.server.config.GerritIsReplica;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.ScheduleConfig;
import com.google.gerrit.server.config.ScheduleConfig.Schedule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;

public class PeriodicIndexerConfigProvider implements Provider<Map<String, PeriodicIndexerConfig>> {

  public static final Schedule DEFAULT_SCHEDULE =
      Schedule.createOrFail(TimeUnit.MINUTES.toMillis(5), "00:00");

  private static final String ENABLED = "enabled";
  private static final String INDEX = "index";
  private static final String RUN_ON_STARTUP = "runOnStartup";
  private static final String SCHEDULED_INDEXER = "scheduledIndexer";

  private final Config cfg;
  private final Collection<IndexDefinition<?, ?, ?>> defs;
  private final boolean isReplica;

  @Inject
  PeriodicIndexerConfigProvider(
      @GerritServerConfig Config cfg,
      Collection<IndexDefinition<?, ?, ?>> defs,
      @GerritIsReplica boolean isReplica) {
    this.cfg = cfg;
    this.defs = defs;
    this.isReplica = isReplica;
  }

  @Override
  public Map<String, PeriodicIndexerConfig> get() {
    ImmutableMap.Builder<String, PeriodicIndexerConfig> builder =
        ImmutableMap.<String, PeriodicIndexerConfig>builder();
    Set<String> scheduledIndexerSubsections = cfg.getSubsections(SCHEDULED_INDEXER);

    for (IndexDefinition<?, ?, ?> def : defs) {
      String indexName = def.getName();
      if (scheduledIndexerSubsections.contains(indexName)) {
        builder.put(indexName, parse(SCHEDULED_INDEXER, indexName, false, false));
      } else if ("groups".equals(indexName) && isReplica) {
        builder.put(indexName, parse(INDEX, SCHEDULED_INDEXER, true, true));
      }
    }

    return builder.build();
  }

  private PeriodicIndexerConfig parse(
      String section, String subsection, boolean defaultRunOnStartup, boolean defaultEnabled) {
    boolean runOnStartup = cfg.getBoolean(section, subsection, RUN_ON_STARTUP, defaultRunOnStartup);
    boolean enabled = cfg.getBoolean(section, subsection, ENABLED, defaultEnabled);
    Schedule schedule =
        ScheduleConfig.builder(cfg, section)
            .setSubsection(subsection)
            .buildSchedule()
            .orElse(DEFAULT_SCHEDULE);
    return PeriodicIndexerConfig.create(runOnStartup, enabled, schedule);
  }
}
