// Copyright (C) 2009 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.common.data;

import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommentDetail {
  protected List<PatchLineComment> a;
  protected List<PatchLineComment> b;
  protected AccountInfoCache accounts;

  private transient PatchSet.Id idA;
  private transient PatchSet.Id idB;
  private transient Map<Integer, List<PatchLineComment>> forA;
  private transient Map<Integer, List<PatchLineComment>> forB;

  public CommentDetail(final PatchSet.Id idA, final PatchSet.Id idB) {
    this.a = new ArrayList<PatchLineComment>();
    this.b = new ArrayList<PatchLineComment>();
    this.idA = idA;
    this.idB = idB;
  }

  protected CommentDetail() {
  }

  public boolean include(final PatchLineComment p) {
    final PatchSet.Id psId = p.getKey().getParentKey().getParentKey();
    switch (p.getSide()) {
      case 0:
        if ((idA == null || idA.get() == 0) && idB.equals(psId)) {
          a.add(p);
          return true;
        }
        break;

      case 1:
        if (idA != null && idA.equals(psId)) {
          a.add(p);
          return true;
        }

        if (idB.equals(psId)) {
          b.add(p);
          return true;
        }
        break;
    }
    return false;
  }

  public void setAccountInfoCache(final AccountInfoCache a) {
    accounts = a;
  }

  public AccountInfoCache getAccounts() {
    return accounts;
  }

  public List<PatchLineComment> getCommentsA() {
    return a;
  }

  public List<PatchLineComment> getCommentsB() {
    return b;
  }

  public boolean isEmpty() {
    return a.isEmpty() && b.isEmpty();
  }

  public List<PatchLineComment> getForA(final int lineNbr) {
    if (lineNbr == 0) {
      return Collections.emptyList();
    }
    if (forA == null) {
      forA = index(a);
    }
    return get(forA, lineNbr);
  }

  public List<PatchLineComment> getForB(final int lineNbr) {
    if (lineNbr == 0) {
      return Collections.emptyList();
    }
    if (forB == null) {
      forB = index(b);
    }
    return get(forB, lineNbr);
  }

  private static List<PatchLineComment> get(
      final Map<Integer, List<PatchLineComment>> m, final int i) {
    final List<PatchLineComment> r = m.get(i);
    return r != null ? orderComments(r) : Collections.<PatchLineComment> emptyList();
  }

  /**
   * Order the comments based on their parent_uuid parent.  It is possible to do this by
   * iterating over the list only once but it's probably overkill since the number of comments
   * on a given line will be small most of the time.
   *
   * @param comments The list of comments for a given line.
   * @return The comments sorted as they should appear in the UI
   */
  private static List<PatchLineComment> orderComments(List<PatchLineComment> comments) {
    // Map of comments keyed by their parent. The values are lists of comments since it is
    // possible for several comments to have the same parent (this can happen if two reviewers
    // click Reply on the same comment at the same time). Such comments will be displayed under
    // their correct parent in chronological order.
    Map<String, List<PatchLineComment>> parentMap = new HashMap<String, List<PatchLineComment>>();

    // It's possible to have more than one root comment if two reviewers create a comment on the
    // same line at the same time
    List<PatchLineComment> rootComments = new ArrayList<PatchLineComment>();

    // Store all the comments in parentMap, keyed by their parent
    for (PatchLineComment c : comments) {
      String parentUuid = c.getParentUuid();
      List<PatchLineComment> l = parentMap.get(parentUuid);
      if (l == null) {
        l = new ArrayList<PatchLineComment>();
        parentMap.put(parentUuid, l);
      }
      l.add(c);
      if (parentUuid == null) rootComments.add(c);
    }

    // Add the comments in the list, starting with the head and then going through all the
    // comments that have it as a parent, and so on
    List<PatchLineComment> result = new ArrayList<PatchLineComment>();
    addChildren(parentMap, rootComments, result);

    return result;
  }

  /**
   * Add the comments to <code>outResult</code>, depth first
   */
  private static void addChildren(Map<String, List<PatchLineComment>> parentMap,
      List<PatchLineComment> children, List<PatchLineComment> outResult) {
    if (children != null) {
      for (PatchLineComment c : children) {
        outResult.add(c);
        addChildren(parentMap, parentMap.get(c.getKey().get()), outResult);
      }
    }
  }

  private Map<Integer, List<PatchLineComment>> index(
      final List<PatchLineComment> in) {
    final HashMap<Integer, List<PatchLineComment>> r;

    r = new HashMap<Integer, List<PatchLineComment>>();
    for (final PatchLineComment p : in) {
      List<PatchLineComment> l = r.get(p.getLine());
      if (l == null) {
        l = new ArrayList<PatchLineComment>();
        r.put(p.getLine(), l);
      }
      l.add(p);
    }
    return r;
  }
}
