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
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.util.cli.CmdLineParser;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Inject;

import org.kohsuke.args4j.CmdLineException;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Iterator;
import java.util.Map;

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
      Multimap<String, String> params)
      throws UnsupportedEncodingException {
    if (!Strings.isNullOrEmpty(queryString)) {
      for (String kvPair : Splitter.on('&').split(queryString)) {
        Iterator<String> i = Splitter.on('=').limit(2).split(kvPair).iterator();
        String key = decode(i.next());
        String val = i.hasNext() ? decode(i.next()) : "";
        if (RESERVED_KEYS.contains(key)) {
          config.put(key, val);
        } else {
          params.put(key, val);
        }
      }
    }
  }

  private static String decode(String value) throws UnsupportedEncodingException {
    return URLDecoder.decode(value, "UTF-8");
  }

  static JsonObject formToJson(HttpServletRequest req)
      throws BadRequestException {
    @SuppressWarnings("unchecked")
    Map<String, String[]> map = req.getParameterMap();
    JsonObject obj = new JsonObject();
    for (Map.Entry<String, String[]> ent : map.entrySet()) {
      String key = ent.getKey();
      String[] values = ent.getValue();
      int dot = key.indexOf('.');
      if (dot < 0) {
        for (String v : values) {
          obj.addProperty(key, v);
        }
        continue;
      }

      String name = key.substring(0, dot);
      JsonElement m = obj.get(name);
      JsonObject o;
      if (m == null) {
        o = new JsonObject();
        obj.add(name, o);
      } else if (m.isJsonObject()) {
        o = m.getAsJsonObject();
      } else {
        throw new BadRequestException(String.format(
            "key %s conflicts with %s", key, name));
      }

      String n = key.substring(dot + 1);
      for (String v : values) {
        o.addProperty(n, v);
      }
    }
    return obj;
  }
}
