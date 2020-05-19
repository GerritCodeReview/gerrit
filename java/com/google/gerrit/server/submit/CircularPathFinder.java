package com.google.gerrit.server.submit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

class CircularPathFinder {
  private CircularPathFinder() {}

  /**
   * Prints a circular path according to the nodes goes through in {@code p} and the start point
   * {@code target}.
   */
  public static <T> String printCircularPath(Collection<T> p, T target) {
    StringBuilder sb = new StringBuilder();
    sb.append(target);
    ArrayList<T> reverseP = new ArrayList<>(p);
    Collections.reverse(reverseP);
    for (T t : reverseP) {
      sb.append("->");
      sb.append(t);
      if (t.equals(target)) {
        break;
      }
    }
    return sb.toString();
  }
}
