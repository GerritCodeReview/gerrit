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

package com.google.gerrit.httpd;

import com.google.gerrit.util.cli.CmdLineParser;
import com.google.gwtjsonrpc.server.RPCServletUtils;
import com.google.inject.Inject;

import org.kohsuke.args4j.CmdLineException;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public abstract class RestApiServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  @Override
  protected void service(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    res.setHeader("Expires", "Fri, 01 Jan 1980 00:00:00 GMT");
    res.setHeader("Pragma", "no-cache");
    res.setHeader("Cache-Control", "no-cache, must-revalidate");
    res.setHeader("Content-Disposition", "attachment");
    super.service(req, res);
  }

  protected static void sendText(HttpServletRequest req,
      HttpServletResponse res, String data) throws IOException {
    res.setContentType("text/plain");
    res.setCharacterEncoding("UTF-8");
    send(req, res, data.getBytes("UTF-8"));
  }

  protected static void send(HttpServletRequest req, HttpServletResponse res,
      byte[] data) throws IOException {
    if (data.length > 256 && RPCServletUtils.acceptsGzipEncoding(req)) {
      res.setHeader("Content-Encoding", "gzip");
      data = HtmlDomUtil.compress(data);
    }
    res.setContentLength(data.length);
    OutputStream out = res.getOutputStream();
    try {
      out.write(data);
    } finally {
      out.close();
    }
  }

  public static class ParameterParser {
    private final CmdLineParser.Factory parserFactory;

    @Inject
    ParameterParser(CmdLineParser.Factory pf) {
      this.parserFactory = pf;
    }

    public <T> boolean parse(T param, HttpServletRequest req,
        HttpServletResponse res) throws IOException {
      CmdLineParser clp = parserFactory.create(param);
      try {
        @SuppressWarnings("unchecked")
        Map<String, String[]> parameterMap = req.getParameterMap();
        clp.parseOptionMap(parameterMap);
      } catch (CmdLineException e) {
        if (!clp.wasHelpRequestedByOption()) {
          res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          sendText(req, res, e.getMessage());
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
}
