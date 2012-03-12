// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.query.OperatorPredicate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.RegExp;
import dk.brics.automaton.RunAutomaton;

import java.util.Arrays;

class RegexFilePredicate extends OperatorPredicate<ChangeData, PatchSet> {
  private final Provider<ReviewDb> db;
  private final PatchListCache cache;
  private final RunAutomaton pattern;

  private final String prefixBegin;
  private final String prefixEnd;
  private final int prefixLen;
  private final boolean prefixOnly;

  RegexFilePredicate(Provider<ReviewDb> db, PatchListCache plc, String re) {
    super(ChangeQueryBuilder.FIELD_FILE, re);
    this.db = db;
    this.cache = plc;

    if (re.startsWith("^")) {
      re = re.substring(1);
    }

    if (re.endsWith("$") && !re.endsWith("\\$")) {
      re = re.substring(0, re.length() - 1);
    }

    Automaton automaton = new RegExp(re).toAutomaton();
    prefixBegin = automaton.getCommonPrefix();
    prefixLen = prefixBegin.length();

    if (0 < prefixLen) {
      char max = (char) (prefixBegin.charAt(prefixLen - 1) + 1);
      prefixEnd = prefixBegin.substring(0, prefixLen - 1) + max;
      prefixOnly = re.equals(prefixBegin + ".*");
    } else {
      prefixEnd = "";
      prefixOnly = false;
    }

    pattern = prefixOnly ? null : new RunAutomaton(automaton);
  }

  @Override
  public boolean match(ChangeData object) throws OrmException {
    String[] files = object.currentFilePaths(db, cache);
    if (files != null) {
      int begin, end;

      if (0 < prefixLen) {
        begin = find(files, prefixBegin);
        end = find(files, prefixEnd);
      } else {
        begin = 0;
        end = files.length;
      }

      if (prefixOnly) {
        return begin < end;
      }

      while (begin < end) {
        if (pattern.run(files[begin++])) {
          return true;
        }
      }

      return false;

    } else {
      // The ChangeData can't do expensive lookups right now. Bypass
      // them and include the result anyway. We might be able to do
      // a narrow later on to a smaller set.
      //
      return true;
    }
  }

  private static int find(String[] files, String p) {
    int r = Arrays.binarySearch(files, p);
    return r < 0 ? -(r + 1) : r;
  }

  @Override
  public int getCost() {
    return 1;
  }
}
