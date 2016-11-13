// Copyright (C) 2013 The Android Open Source Project
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

import com.google.gerrit.server.query.QueryParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class is used to extract comma separated values in a predicate.
 *
 * <p>If tags for the values are present (e.g. "branch=jb_2.3,vote=approved") then the args are
 * placed in a map that maps tag to value (e.g., "branch" to "jb_2.3"). If no tag is present (e.g.
 * "jb_2.3,approved") then the args are placed into a positional list. Args may be mixed so some may
 * appear in the map and others in the positional list (e.g. "vote=approved,jb_2.3).
 */
public class PredicateArgs {
  public List<String> positional;
  public Map<String, String> keyValue;

  /**
   * Parses query arguments into {@link #keyValue} and/or {@link #positional}..
   *
   * <p>Labels for these arguments should be kept in ChangeQueryBuilder as {@code ARG_ID_[argument
   * name]}.
   *
   * @param args arguments to be parsed
   * @throws QueryParseException
   */
  PredicateArgs(String args) throws QueryParseException {
    positional = new ArrayList<>();
    keyValue = new HashMap<>();

    String[] splitArgs = args.split(",");

    for (String arg : splitArgs) {
      String[] splitKeyValue = arg.split("=");

      if (splitKeyValue.length == 1) {
        positional.add(splitKeyValue[0]);
      } else if (splitKeyValue.length == 2) {
        if (!keyValue.containsKey(splitKeyValue[0])) {
          keyValue.put(splitKeyValue[0], splitKeyValue[1]);
        } else {
          throw new QueryParseException("Duplicate key " + splitKeyValue[0]);
        }
      } else {
        throw new QueryParseException("invalid arg " + arg);
      }
    }
  }
}
