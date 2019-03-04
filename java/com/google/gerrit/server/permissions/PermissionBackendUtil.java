package com.google.gerrit.server.permissions;

import com.google.common.collect.Sets;
import com.google.gerrit.extensions.api.access.GerritPermission;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/** Utilities for {@code PermissionBackend}. */
public class PermissionBackendUtil {

  public static <T extends GerritPermission> Set<T> newSet(Collection<T> permSet) {
    if (permSet instanceof EnumSet) {
      @SuppressWarnings({"unchecked", "rawtypes"})
      Set<T> s = ((EnumSet) permSet).clone();
      s.clear();
      return s;
    }
    return Sets.newHashSetWithExpectedSize(permSet.size());
  }

  private PermissionBackendUtil() {}
}
