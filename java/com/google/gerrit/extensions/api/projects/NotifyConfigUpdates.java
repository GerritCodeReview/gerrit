package com.google.gerrit.extensions.api.projects;

import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;

public class NotifyConfigUpdates implements Comparable<NotifyConfigUpdates> {
  public List<String> notifyConfigRemovals;
  public List<NotifyConfigInput> notifyConfigAdditions;

  @Override
  public int compareTo(NotifyConfigUpdates o) {
    if (notifyConfigAdditions.equals(o.notifyConfigAdditions)
        && notifyConfigRemovals.equals(o.notifyConfigRemovals)) {
      return 0;
    } else {
      return -1;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(notifyConfigRemovals, notifyConfigAdditions);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof NotifyConfigUpdates) {
      return compareTo((NotifyConfigUpdates) obj) == 0;
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
