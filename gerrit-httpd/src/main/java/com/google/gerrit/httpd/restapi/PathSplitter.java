// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.httpd.restapi;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.primitives.Ints;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.restapi.IdString;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PathSplitter {
  /** Delimits the project from the numeric change ID */
  private static final String PROJECT_CHANGE_DELIMITER = "/+/";

  /**
   * @param path
   * @return
   */
  public static List<IdString> split(@Nullable String path) {
    if (Strings.isNullOrEmpty(path)) {
      return Collections.emptyList();
    }

    List<IdString> out = new ArrayList<>();

    // Check if the URL contains a project/changeID delimiter. We try to be as narrow as possible
    // in this part of the parser. Starting from the front, we search for /+/ immediately followed
    // by a number. For example, we accept foo/+/123/get where foo/+/123 is identified as a valid
    // IdString and foo/+/bar/+/123/get, where foo/+/bar/+/123 is identified as a valid IdString.
    for (int delimiterIndex = path.indexOf(PROJECT_CHANGE_DELIMITER);
        delimiterIndex >= 0;
        delimiterIndex = path.indexOf(PROJECT_CHANGE_DELIMITER, delimiterIndex + 1)) {
      int nextSlashIndex = delimiterIndex + PROJECT_CHANGE_DELIMITER.length();
      while (nextSlashIndex < path.length() && path.charAt(nextSlashIndex) != '/') {
        nextSlashIndex++;
      }
      // Only add this if the next part is numeric
      if (Ints.tryParse(
              path.substring(delimiterIndex + PROJECT_CHANGE_DELIMITER.length(), nextSlashIndex))
          != null) {
        out.add(IdString.fromUrl(path.substring(0, nextSlashIndex)));
        path = path.substring(nextSlashIndex + 1);
        break;
      }
    }

    // Split the rest of the URL
    for (String p : Splitter.on('/').split(path)) {
      out.add(IdString.fromUrl(p));
    }
    if (out.size() > 0 && out.get(out.size() - 1).isEmpty()) {
      out.remove(out.size() - 1);
    }
    return out;
  }
}
