package com.google.gerrit.server.patch;

import com.google.gerrit.entities.Patch.ChangeType;
import java.util.Optional;

/**
 * Adapter for old/new paths of the new diff cache to the old diff cache representation. This is
 * needed for backward compatibility with all old diff cache callers.
 *
 * <p>TODO(ghareeb): It's better to revisit this logic and update all diff cache callers to use the
 * new diff cache output directly.
 */
public class FilePathAdapter {
  private FilePathAdapter() {}

  /**
   * Converts the old file path of the new diff cache output to the old diff cache representation.
   */
  public static String getOldPath(Optional<String> oldName, ChangeType changeType) {
    switch (changeType) {
      case DELETED:
      case ADDED:
      case MODIFIED:
      case REWRITE:
        return null;
      case COPIED:
      case RENAMED:
        return oldName.get();
      default:
        throw new IllegalArgumentException("Unsupported type " + changeType);
    }
  }

  /**
   * Converts the new file path of the new diff cache output to the old diff cache representation.
   */
  public static String getNewPath(
      Optional<String> oldName, Optional<String> newName, ChangeType changeType) {
    switch (changeType) {
      case DELETED:
        return oldName.get();
      case ADDED:
      case MODIFIED:
      case REWRITE:
      case COPIED:
      case RENAMED:
        return newName.get();
      default:
        throw new IllegalArgumentException("Unsupported type " + changeType);
    }
  }
}
