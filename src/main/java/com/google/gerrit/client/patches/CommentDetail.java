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

package com.google.gerrit.client.patches;

import com.google.gerrit.client.data.AccountInfoCache;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchLineComment;
import com.google.gerrit.client.reviewdb.PatchSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommentDetail {
  protected List<PatchLineComment> commentsA;
  protected List<PatchLineComment> commentsB;
  protected List<Patch> history;
  protected AccountInfoCache accounts;

  private transient PatchSet.Id idA;
  private transient PatchSet.Id idB;
  private transient Map<Integer, List<PatchLineComment>> forA;
  private transient Map<Integer, List<PatchLineComment>> forB;

  public CommentDetail(final PatchSet.Id a, final PatchSet.Id b) {
    commentsA = new ArrayList<PatchLineComment>();
    commentsB = new ArrayList<PatchLineComment>();

    idA = a;
    idB = b;
  }

  protected CommentDetail() {
  }

  public boolean include(final PatchLineComment p) {
    final PatchSet.Id psId = p.getKey().getParentKey().getParentKey();
    switch (p.getSide()) {
      case 0:
        if (idA == null && idB.equals(psId)) {
          commentsA.add(p);
          return true;
        }
        break;

      case 1:
        if (idA != null && idA.equals(psId)) {
          commentsA.add(p);
          return true;
        }

        if (idB.equals(psId)) {
          commentsB.add(p);
          return true;
        }
        break;
    }
    return false;
  }

  public void setAccountInfoCache(final AccountInfoCache a) {
    accounts = a;
  }

  public void setHistory(final List<Patch> h) {
    history = h;
  }

  public AccountInfoCache getAccounts() {
    return accounts;
  }

  public List<Patch> getHistory() {
    return history;
  }

  public List<PatchLineComment> getCommentsA() {
    return commentsA;
  }

  public List<PatchLineComment> getCommentsB() {
    return commentsB;
  }

  public boolean isEmpty() {
    return commentsA.isEmpty() && commentsB.isEmpty();
  }

  public List<PatchLineComment> getForA(final int lineNbr) {
    if (lineNbr == 0) {
      return Collections.emptyList();
    }
    if (forA == null) {
      forA = index(commentsA);
    }
    return get(forA, lineNbr);
  }

  public List<PatchLineComment> getForB(final int lineNbr) {
    if (lineNbr == 0) {
      return Collections.emptyList();
    }
    if (forB == null) {
      forB = index(commentsB);
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
    Map<String, PatchLineComment> parentMap = new HashMap<String, PatchLineComment>();
    PatchLineComment firstComment = null;

    // Store all the comments, keyed by their parent
    for (PatchLineComment c : comments) {
      parentMap.put(c.getParentUuid(), c);
      if ("".equals(c.getParentUuid())) firstComment = c;
    }

    // Add the comments in the list, starting with the head and then going through the parents
    // chain
    List<PatchLineComment> result = new ArrayList<PatchLineComment>();
    result.add(firstComment);
    PatchLineComment current = firstComment;
    do {
      PatchLineComment parent = parentMap.get(current.getKey().get());
      if (parent != null) {
          result.add(parent);
      }
      current = parent;
    } while (current != null);

    return result;
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
