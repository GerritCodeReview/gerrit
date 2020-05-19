package com.google.gerrit.server.submit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;

public class SubmoduleUtils {
  static <T> String printCircularPath(LinkedHashSet<T> p, T target) {
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

  static <T> void reverse(LinkedHashSet<T> set) {
    if (set == null) {
      return;
    }

    Deque<T> q = new ArrayDeque<>(set);
    set.clear();

    while (!q.isEmpty()) {
      set.add(q.removeLast());
    }
  }
}
