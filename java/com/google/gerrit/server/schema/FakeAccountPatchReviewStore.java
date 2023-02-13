package com.google.gerrit.server.schema;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.server.change.AccountPatchReviewStore;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.config.ThreadSettingsConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.inject.Singleton;
import org.eclipse.jgit.lib.Config;

/**
 * An implementation of the {@link AccountPatchReviewStore} that's only used in tests. This
 * implementation stores reviewed files in memory.
 */
@Singleton
public class FakeAccountPatchReviewStore extends JdbcAccountPatchReviewStore {

  protected FakeAccountPatchReviewStore(
      Config cfg, SitePaths sitePaths, ThreadSettingsConfig threadSettingsConfig) {
    super(cfg, sitePaths, threadSettingsConfig);
  }

  @AutoValue
  abstract static class Entity {
    abstract PatchSet.Id psId();

    abstract Account.Id accountId();

    abstract String path();

    static Entity create(PatchSet.Id psId, Account.Id accountId, String path) {
      return new AutoValue_FakeAccountPatchReviewStore_Entity(psId, accountId, path);
    }
  }

  private Set<Entity> store = new HashSet<>();

  @Override
  public boolean markReviewed(PatchSet.Id psId, Account.Id accountId, String path) {
    Entity entity = Entity.create(psId, accountId, path);
    if (store.contains(entity)) {
      return false;
    }
    store.add(entity);
    return true;
  }

  @Override
  public void markReviewed(PatchSet.Id psId, Account.Id accountId, Collection<String> paths) {
    paths.forEach(path -> markReviewed(psId, accountId, path));
  }

  @Override
  public void clearReviewed(PatchSet.Id psId, Account.Id accountId, String path) {
    store.remove(Entity.create(psId, accountId, path));
  }

  @Override
  public void clearReviewed(PatchSet.Id psId) {
    List<Entity> toRemove = new ArrayList<>();
    for (Entity entity : store) {
      if (entity.psId().equals(psId)) {
        toRemove.add(entity);
      }
    }
    store.removeAll(toRemove);
  }

  @Override
  public void clearReviewed(Change.Id changeId) {
    List<Entity> toRemove = new ArrayList<>();
    for (Entity entity : store) {
      if (entity.psId().changeId().equals(changeId)) {
        toRemove.add(entity);
      }
    }
    store.removeAll(toRemove);
  }

  @Override
  public Optional<PatchSetWithReviewedFiles> findReviewed(PatchSet.Id psId, Account.Id accountId) {
    int matchedPsNumber = -1;
    Optional<PatchSetWithReviewedFiles> result = Optional.empty();
    for (Entity entity : store) {
      if (entity.accountId() != accountId) {
        continue;
      }
      int entityPsNumber = Integer.parseInt(entity.psId().getId());
      if (entityPsNumber <= psId.get() && entityPsNumber > matchedPsNumber) {
        matchedPsNumber = entityPsNumber;
        result =
            Optional.of(
                PatchSetWithReviewedFiles.create(
                    PatchSet.id(psId.changeId(), matchedPsNumber), ImmutableSet.of(entity.path())));
      }
    }
    return result;
  }
}
