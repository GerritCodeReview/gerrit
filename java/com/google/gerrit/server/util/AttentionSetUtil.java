package com.google.gerrit.server.util;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.AttentionSetUpdate.Operation;
import java.util.Collection;

/** Common helpers for dealing with attention set data structures. */
public class AttentionSetUtil {
  // ö Should we use set more?
  public static ImmutableSet<AttentionSetUpdate> includedIn(
      Collection<AttentionSetUpdate> updates) {
    return updates.stream()
        .filter(u -> u.operation() == Operation.ADD)
        .collect(ImmutableSet.toImmutableSet());
  }

  private AttentionSetUtil() {}
}
