// Copyright (C) 2018 The Android Open Source Project
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

// WARNING: NoteDbUpdateManager cares about the package name RestApiServlet lives in.

package com.google.gerrit.httpd.restapi;

import static com.google.gerrit.httpd.restapi.RestApiServlet.XD_AUTHORIZATION;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.extensions.restapi.Url;
import java.util.Iterator;

public class LogRedactUtil {
  private static final ImmutableSet<String> REDACT_PARAM = ImmutableSet.of(XD_AUTHORIZATION);

  private LogRedactUtil() {}

  /**
   * Redacts sensitive information such as an access token from the query string to make it suitable
   * for logging.
   */
  @VisibleForTesting
  public static String redactQueryString(String qs) {
    StringBuilder b = new StringBuilder();
    for (String kvPair : Splitter.on('&').split(qs)) {
      Iterator<String> i = Splitter.on('=').limit(2).split(kvPair).iterator();
      String key = i.next();
      if (b.length() > 0) {
        b.append('&');
      }
      b.append(key);
      if (i.hasNext()) {
        b.append('=');
        if (REDACT_PARAM.contains(Url.decode(key))) {
          b.append('*');
        } else {
          b.append(i.next());
        }
      }
    }
    return b.toString();
  }
}
