package com.google.gerrit.extensions.api.projects;

import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;

public class NotifyConfigUpdates implements Comparable<NotifyConfigUpdates> {
  public List<String> notifyConfigsRemovals;
  public List<NotifyConfigInput> notifyConfigsAdditions;

  @Override
  public int compareTo(NotifyConfigUpdates o) {
    if (notifyConfigsAdditions.equals(o.notifyConfigsAdditions)
        && notifyConfigsRemovals.equals(o.notifyConfigsRemovals)) {
      return 0;
    } else {
      return -1;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(notifyConfigsRemovals, notifyConfigsAdditions);
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
        .add("notify config removals", notifyConfigsRemovals)
        .add("notify config additions", notifyConfigsAdditions)
        .toString();
  }
}
