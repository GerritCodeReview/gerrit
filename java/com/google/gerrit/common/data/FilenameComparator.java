// Copyright (C) 2016 The Android Open Source Project
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

import com.google.gerrit.reviewdb.client.Patch;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public class FilenameComparator implements Comparator<String> {
  public static final FilenameComparator INSTANCE = new FilenameComparator();

  private static final Set<String> cppHeaderSuffixes =
      new HashSet<>(Arrays.asList(".h", ".hxx", ".hpp"));

  private FilenameComparator() {}

  @Override
  public int compare(String path1, String path2) {
    if (Patch.COMMIT_MSG.equals(path1) && Patch.COMMIT_MSG.equals(path2)) {
      return 0;
    } else if (Patch.COMMIT_MSG.equals(path1)) {
      return -1;
    } else if (Patch.COMMIT_MSG.equals(path2)) {
      return 1;
    }
    if (Patch.MERGE_LIST.equals(path1) && Patch.MERGE_LIST.equals(path2)) {
      return 0;
    } else if (Patch.MERGE_LIST.equals(path1)) {
      return -1;
    } else if (Patch.MERGE_LIST.equals(path2)) {
      return 1;
    }

    int s1 = path1.lastIndexOf('.');
    int s2 = path2.lastIndexOf('.');
    if (s1 > 0 && s2 > 0 && path1.substring(0, s1).equals(path2.substring(0, s2))) {
      String suffixA = path1.substring(s1);
      String suffixB = path2.substring(s2);
      // C++ and C: give priority to header files (.h/.hpp/...)
      if (cppHeaderSuffixes.contains(suffixA)) {
        return -1;
      } else if (cppHeaderSuffixes.contains(suffixB)) {
        return 1;
      }
    }
    return path1.compareTo(path2);
  }
}
