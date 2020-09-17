package com.google.gerrit.server.submit;

import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import java.util.Set;
import org.eclipse.jgit.lib.Config;

class ConfigSubscriptionGraphFactory {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final SubscriptionGraph.Factory subscriptionGraphFactory;
  private final Config cfg;

  @Inject
  ConfigSubscriptionGraphFactory(
      SubscriptionGraph.Factory subscriptionGraphFactory, @GerritServerConfig Config cfg) {
    this.subscriptionGraphFactory = subscriptionGraphFactory;
    this.cfg = cfg;
  }

  public SubscriptionGraph create(Set<BranchNameKey> updatedBranches, MergeOpRepoManager orm)
      throws SubmoduleConflictException {
    SubscriptionGraph subscriptionGraph;
    if (cfg.getBoolean("submodule", "enableSuperProjectSubscriptions", true)) {
      subscriptionGraph = subscriptionGraphFactory.compute(updatedBranches, orm);
    } else {
      logger.atFine().log("Updating superprojects disabled");
      subscriptionGraph = SubscriptionGraph.createEmptyGraph(ImmutableSet.copyOf(updatedBranches));
    }
    return subscriptionGraph;
  }
}
