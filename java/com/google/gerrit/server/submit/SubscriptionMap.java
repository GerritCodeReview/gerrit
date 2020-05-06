package com.google.gerrit.server.submit;

import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.SubmoduleSubscription;
import java.util.Collection;

public interface SubscriptionMap {
  public interface Factory {
    SubscriptionMap create(BranchNameKey project, MergeOpRepoManager m);
  }

  Collection<SubmoduleSubscription> subscribedTo(BranchNameKey src);
}
