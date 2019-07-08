package com.google.gerrit.extensions.api.projects;

import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;

public class NotifyConfigUpdates {
  public List<String> notifyConfigRemovals;
  public List<NotifyConfigInput> notifyConfigAdditions;

  @Override
  public int hashCode() {
    return Objects.hash(notifyConfigRemovals, notifyConfigAdditions);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof NotifyConfigUpdates) {
      NotifyConfigUpdates other = (NotifyConfigUpdates) obj;
      return Objects.equals(notifyConfigRemovals, other.notifyConfigRemovals)
          && Objects.equals(notifyConfigAdditions, other.notifyConfigAdditions);
    }
    return false;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("notify config removals", notifyConfigRemovals)
        .add("notify config additions", notifyConfigAdditions)
        .toString();
  }
}
