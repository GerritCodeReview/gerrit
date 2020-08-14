package com.google.gerrit.server.git.receive;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.submit.MergeOpRepoManager;
import com.google.gerrit.server.submit.SubmoduleOp;
import com.google.gerrit.server.submit.SubmoduleOp.Factory;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.Set;

public class SuperprojectsUpdaterImpl implements SuperprojectsUpdater {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Provider<MergeOpRepoManager> ormProvider;
  private final Factory subOpFactory;

  /** Module to bind the superproject updater. */
  public static class Module extends AbstractModule {
    @Override
    protected void configure() {
      bind(SuperprojectsUpdater.class).to(SuperprojectsUpdaterImpl.class);
      bind(SuperprojectsUpdaterImpl.class);
    }
  }

  @Inject
  SuperprojectsUpdaterImpl(
      Provider<MergeOpRepoManager> ormProvider, SubmoduleOp.Factory subOpFactory) {
    this.ormProvider = ormProvider;
    this.subOpFactory = subOpFactory;
  }

  @Override
  public void update(IdentifiedUser user, Set<BranchNameKey> branches) {
    if (branches.isEmpty()) {
      return;
    }

    try (MergeOpRepoManager orm = ormProvider.get()) {
      orm.setContext(TimeUtil.nowTs(), user, NotifyResolver.Result.none());
      SubmoduleOp op = subOpFactory.create(branches, orm);
      op.updateSuperProjects();
    } catch (RestApiException e) {
      logger.atWarning().withCause(e).log("Can't update the superprojects");
    }
  }
}
