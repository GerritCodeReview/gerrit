package com.google.gerrit.server.project;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public abstract class ExtRefPatternMatcher {
  public static final String RefForPath = "refs/path/";
  public static final String Sp = "#";
  public static final Set<String> excRules = new HashSet<String>(Arrays.asList(
      "submit",
      "label-Code-Review",
      "label-Verified"));

  public static ExtRefPatternMatcher getMatcher(String pattern) {
    if ( pattern.startsWith(RefForPath)) {
      return new ExtRefPathMatcher(pattern);
    } else {
      return null;
    }
  }

  public abstract boolean match(String mactcher);

  /*
   * Extend reference with path.
   * Example:
   *   refs/path/$(project dir or file)#refs/heads/$(branch)
   */
  private static class ExtRefPathMatcher extends ExtRefPatternMatcher{
    private final String path;

    ExtRefPathMatcher(String pattern) {
      path = pattern.substring(RefForPath.length(), pattern.indexOf(Sp));
    }

    @Override
    public boolean match(String mactcher) {
      return mactcher.startsWith(path);
    }

  }
}
