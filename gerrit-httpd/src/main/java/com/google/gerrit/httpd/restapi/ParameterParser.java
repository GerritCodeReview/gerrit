// Copyright (C) 2012 The Android Open Source Project
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

import static com.google.gerrit.httpd.restapi.RestApiServlet.replyError;
import static com.google.gerrit.httpd.restapi.RestApiServlet.replyText;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.gerrit.server.util.Url;
import com.google.gerrit.util.cli.CmdLineParser;
import com.google.inject.Inject;

import org.kohsuke.args4j.CmdLineException;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Iterator;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

class ParameterParser {
  private static final ImmutableSet<String> RESERVED_KEYS = ImmutableSet.of(
      "pp", "prettyPrint", "strict", "callback", "alt", "fields");

  private final CmdLineParser.Factory parserFactory;

  @Inject
  ParameterParser(CmdLineParser.Factory pf) {
    this.parserFactory = pf;
  }

  <T> boolean parse(T param,
      Multimap<String, String> in,
      HttpServletRequest req,
      HttpServletResponse res)
      throws IOException {
    CmdLineParser clp = parserFactory.create(param);
    try {
      clp.parseOptionMap(in);
    } catch (CmdLineException e) {
      if (!clp.wasHelpRequestedByOption()) {
        replyError(res, SC_BAD_REQUEST, e.getMessage());
        return false;
      }
    }

    if (clp.wasHelpRequestedByOption()) {
      StringWriter msg = new StringWriter();
      clp.printQueryStringUsage(req.getRequestURI(), msg);
      msg.write('\n');
      msg.write('\n');
      clp.printUsage(msg, null);
      msg.write('\n');
      replyText(req, res, msg.toString());
      return false;
    }

    return true;
  }

  static void splitQueryString(String queryString,
      Multimap<String, String> config,
      Multimap<String, String> params) {
    if (!Strings.isNullOrEmpty(queryString)) {
      for (String kvPair : Splitter.on('&').split(queryString)) {
        Iterator<String> i = Splitter.on('=').limit(2).split(kvPair).iterator();
        String key = Url.decode(i.next());
        String val = i.hasNext() ? Url.decode(i.next()) : "";
        if (RESERVED_KEYS.contains(key)) {
          config.put(key, val);
        } else {
          params.put(key, val);
        }
      }
    }
  }
}
