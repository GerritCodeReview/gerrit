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

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.PatchSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommentDetail {
  protected List<Comment> a;
  protected List<Comment> b;
  protected AccountInfoCache accounts;

  private transient PatchSet.Id idA;
  private transient PatchSet.Id idB;
  private transient Map<Integer, List<Comment>> forA;
  private transient Map<Integer, List<Comment>> forB;

  public CommentDetail(PatchSet.Id idA, PatchSet.Id idB) {
    this.a = new ArrayList<>();
    this.b = new ArrayList<>();
    this.idA = idA;
    this.idB = idB;
  }

  protected CommentDetail() {}

  public boolean include(Change.Id changeId, Comment p) {
    PatchSet.Id psId = new PatchSet.Id(changeId, p.key.patchSetId);
    switch (p.side) {
      case 0:
        if (idA == null && idB.equals(psId)) {
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

  public List<Comment> getCommentsA() {
    return a;
  }

  public List<Comment> getCommentsB() {
    return b;
  }

  public boolean isEmpty() {
    return a.isEmpty() && b.isEmpty();
  }

  public List<Comment> getForA(int lineNbr) {
    if (forA == null) {
      forA = index(a);
    }
    return get(forA, lineNbr);
  }

  public List<Comment> getForB(int lineNbr) {
    if (forB == null) {
      forB = index(b);
    }
    return get(forB, lineNbr);
  }

  private static List<Comment> get(Map<Integer, List<Comment>> m, int i) {
    List<Comment> r = m.get(i);
    return r != null ? orderComments(r) : Collections.<Comment>emptyList();
  }

  /**
   * Order the comments based on their parent_uuid parent. It is possible to do this by iterating
   * over the list only once but it's probably overkill since the number of comments on a given line
   * will be small most of the time.
   *
   * @param comments The list of comments for a given line.
   * @return The comments sorted as they should appear in the UI
   */
  private static List<Comment> orderComments(List<Comment> comments) {
    // Map of comments keyed by their parent. The values are lists of comments since it is
    // possible for several comments to have the same parent (this can happen if two reviewers
    // click Reply on the same comment at the same time). Such comments will be displayed under
    // their correct parent in chronological order.
    Map<String, List<Comment>> parentMap = new HashMap<>();

    // It's possible to have more than one root comment if two reviewers create a comment on the
    // same line at the same time
    List<Comment> rootComments = new ArrayList<>();

    // Store all the comments in parentMap, keyed by their parent
    for (Comment c : comments) {
      String parentUuid = c.parentUuid;
      List<Comment> l = parentMap.get(parentUuid);
      if (l == null) {
        l = new ArrayList<>();
        parentMap.put(parentUuid, l);
      }
      l.add(c);
      if (parentUuid == null) {
        rootComments.add(c);
      }
    }

    // Add the comments in the list, starting with the head and then going through all the
    // comments that have it as a parent, and so on
    List<Comment> result = new ArrayList<>();
    addChildren(parentMap, rootComments, result);

    return result;
  }

  /** Add the comments to {@code outResult}, depth first */
  private static void addChildren(
      Map<String, List<Comment>> parentMap, List<Comment> children, List<Comment> outResult) {
    if (children != null) {
      for (Comment c : children) {
        outResult.add(c);
        addChildren(parentMap, parentMap.get(c.key.uuid), outResult);
      }
    }
  }

  private Map<Integer, List<Comment>> index(List<Comment> in) {
    HashMap<Integer, List<Comment>> r = new HashMap<>();
    for (Comment p : in) {
      List<Comment> l = r.get(p.lineNbr);
      if (l == null) {
        l = new ArrayList<>();
        r.put(p.lineNbr, l);
      }
      l.add(p);
    }
    return r;
  }
}
