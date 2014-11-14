package com.google.gerrit.extensions.common;

public enum Theme {
  // Light themes
  DEFAULT,
  ECLIPSE,
  ELEGANT,
  NEAT,
  // Dark themes
  MIDNIGHT,
  NIGHT,
  TWILIGHT;

  public boolean isDark() {
    switch (this) {
      case MIDNIGHT:
      case NIGHT:
      case TWILIGHT:
        return true;
      default:
        return false;
    }
  }
}