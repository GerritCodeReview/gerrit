// Copyright (c) 2013, The Linux Foundation. All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//    * Redistributions of source code must retain the above copyright
//      notice, this list of conditions and the following disclaimer.
//    * Redistributions in binary form must reproduce the above
//      copyright notice, this list of conditions and the following
//      disclaimer in the documentation and/or other materials provided
//      with the distribution.
//    * Neither the name of The Linux Foundation nor the names of its
//      contributors may be used to endorse or promote products derived
//      from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
// WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
// ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
// BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
// BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
// WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
// OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
// IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

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
