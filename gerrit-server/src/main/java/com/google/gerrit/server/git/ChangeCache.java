package com.google.gerrit.server.git;

import com.google.gerrit.reviewdb.client.Change;

public interface ChangeCache {
  Change get(Change.Id id);

  void evict(Change.Id id);
}
