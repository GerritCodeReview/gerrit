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
package com.google.gerrit.common;

public class RevisionUtil {

  public static boolean isParentCommitRevision(String revision) {
    if (revision != null && revision.length() > 1
        && revision.charAt(0) == '-') {
      for (int i = 1; i < revision.length(); i++) {
        if (!Character.isDigit(revision.charAt(i))) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  public static int toParentNumber(int revisionNo) {
    if (revisionNo < 0) {
      return -revisionNo - 1;
    }
    return revisionNo;
  }

  private RevisionUtil() {
  }
}
