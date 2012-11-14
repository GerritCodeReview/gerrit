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

import static com.google.gerrit.httpd.restapi.RestApiServlet.sendError;
import static com.google.gerrit.httpd.restapi.RestApiServlet.sendText;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.gerrit.util.cli.CmdLineParser;
import com.google.inject.Inject;

import org.kohsuke.args4j.CmdLineException;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

class ParameterParser {
  private static final ImmutableSet<String> RESERVED_KEYS = ImmutableSet.of(
      "pp", "prettyPrint", "strict", "callback", "alt");
  private final CmdLineParser.Factory parserFactory;

  @Inject
  ParameterParser(CmdLineParser.Factory pf) {
    this.parserFactory = pf;
  }

  <T> boolean parse(T param, HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    CmdLineParser clp = parserFactory.create(param);
    try {
      @SuppressWarnings("unchecked")
      Map<String, String[]> params = Maps.newHashMap(req.getParameterMap());
      params.keySet().removeAll(RESERVED_KEYS);
      clp.parseOptionMap(params, Collections.<String> emptySet());
    } catch (CmdLineException e) {
      if (!clp.wasHelpRequestedByOption()) {
        sendError(res, SC_BAD_REQUEST, e.getMessage());
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
      sendText(req, res, msg.toString());
      return false;
    }

    return true;
  }
}