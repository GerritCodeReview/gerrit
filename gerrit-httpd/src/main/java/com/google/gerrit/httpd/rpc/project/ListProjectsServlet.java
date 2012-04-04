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

package com.google.gerrit.httpd.rpc.project;

import com.google.gerrit.httpd.HtmlDomUtil;
import com.google.gerrit.server.project.ListProjects;
import com.google.gerrit.util.cli.CmdLineParser;
import com.google.gwt.user.server.rpc.RPCServletUtils;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.kohsuke.args4j.CmdLineException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
public class ListProjectsServlet extends HttpServlet {
  private final CmdLineParser.Factory parser;
  private final Provider<ListProjects> factory;

  @Inject
  ListProjectsServlet(CmdLineParser.Factory clp, Provider<ListProjects> ls) {
    this.parser = clp;
    this.factory = ls;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    res.setHeader("Expires", "Fri, 01 Jan 1980 00:00:00 GMT");
    res.setHeader("Pragma", "no-cache");
    res.setHeader("Cache-Control", "no-cache, must-revalidate");
    res.setContentType("text/plain");
    res.setCharacterEncoding("UTF-8");

    ListProjects impl = factory.get();
    CmdLineParser clp = parser.create(impl);
    try {

      @SuppressWarnings("unchecked")
      Map<String, String[]> parameterMap = req.getParameterMap();
      clp.parseOptionMap(parameterMap);
    } catch (CmdLineException e) {
      if (!clp.wasHelpRequestedByOption()) {
        res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        send(res, e.getMessage().getBytes("UTF-8"));
        return;
      }
    }

    byte[] data;
    if (clp.wasHelpRequestedByOption()) {
      StringWriter msg = new StringWriter();
      clp.printDetailedUsage(req.getRequestURI(), msg);
      data = msg.toString().getBytes("UTF-8");
    } else {
      ByteArrayOutputStream buf = new ByteArrayOutputStream();
      impl.display(buf);
      data = buf.toByteArray();;
    }

    if (RPCServletUtils.acceptsGzipEncoding(req)) {
      res.setHeader("Content-Encoding", "gzip");
      data = HtmlDomUtil.compress(data);
    }
    send(res, data);
  }

  private void send(HttpServletResponse res, byte[] data) throws IOException {
    res.setContentLength(data.length);
    OutputStream out = res.getOutputStream();
    try {
      out.write(data);
    } finally {
      out.close();
    }
  }
}
