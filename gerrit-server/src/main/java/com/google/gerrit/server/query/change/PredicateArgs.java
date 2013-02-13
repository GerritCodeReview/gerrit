// Copyright (C) 2013 OASP (c)
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * This class is used to extract comma separated values in a predicate
 *
 * If tags for the values are present (e.g. "branch=jb_2.3,vote=approved") then
 * the args are placed in a map that maps tag to value (e.g., "branch" to "jb_2.3").
 * If no tag is present (e.g. "jb_2.3,approved") then the args are placed into a
 * positional list.  Args may be mixed so some may appear in the map and others
 * in the positional list (e.g. "vote=approved,jb_2.3).
 */
public class PredicateArgs {
  private static final Logger log =  LoggerFactory.getLogger(PredicateArgs.class);

  public List<String> positional;
  public Map<String, String> keyValue;

  /**
   * Parses query arguments into keyValue and/or positional values
   * labels for these arguments should be kept in ChangeQueryBuild
   * as ARG_ID_{argument name}.
   *
   * @param args - arguments to be parsed
   *
   * @return - the static values keyValue and positional will contain
   *           the parsed values.
   * @throws QueryParseException
   */
  PredicateArgs(String args) throws QueryParseException {
    log.debug("args={}", args);

    positional = new ArrayList<String>();
    keyValue = new HashMap<String, String>();

    String splitArgs[] = args.split(",");
    log.debug("splitArgs.length={}", splitArgs.length);

    for (String arg : splitArgs) {
      log.debug("arg={}", arg);

      String splitKeyValue[] = arg.split("=");
      log.debug("splitKeyValue.length={}", splitKeyValue.length);

      if (splitKeyValue.length == 1) {
        positional.add(splitKeyValue[0]);
        log.debug("add {} to positional", splitKeyValue[0]);
      } else if (splitKeyValue.length == 2) {
        if (!keyValue.containsKey(splitKeyValue[0])) {
          keyValue.put(splitKeyValue[0], splitKeyValue[1]);
        } else {
          throw new QueryParseException("Duplicate key " + splitKeyValue[0]);
        }

        log.debug("add {},{} to keyvalue", splitKeyValue[0], splitKeyValue[1]);
      } else {
        throw new QueryParseException("invalid arg " + arg);
      }
    }
  }
}
