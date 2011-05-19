// Copyright (C) 2010 The Android Open Source Project
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

import com.google.gerrit.server.query.change.QueryProcessor;
import com.google.gerrit.server.query.change.QueryProcessor.OutputFormat;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.io.IOException;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
public class ChangeQueryServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;
  private final Provider<QueryProcessor> processor;

  @Inject
  ChangeQueryServlet(Provider<QueryProcessor> processor) {
    this.processor = processor;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse rsp)
      throws IOException {
    rsp.setContentType("text/json");
    rsp.setCharacterEncoding("UTF-8");

    QueryProcessor p = processor.get();
    OutputFormat format = OutputFormat.JSON;
    try {
      format = OutputFormat.valueOf(get(req, "format", format.toString()));
    } catch (IllegalArgumentException err) {
      error(rsp, "invalid format");
      return;
    }

    switch (format) {
      case JSON:
        rsp.setContentType("text/json");
        rsp.setCharacterEncoding("UTF-8");
        break;

      case TEXT:
        rsp.setContentType("text/plain");
        rsp.setCharacterEncoding("UTF-8");
        break;

      default:
        error(rsp, "invalid format");
        return;
    }

    p.setIncludeCurrentPatchSet(get(req, "current-patch-set", false));
    p.setIncludePatchSets(get(req, "patch-sets", false));
    p.setIncludeApprovals(get(req, "all-approvals", false));
    p.setOutput(rsp.getOutputStream(), format);
    p.query(get(req, "q", "status:open"));
  }

  private static void error(HttpServletResponse rsp, String message)
      throws IOException {
    ErrorMessage em = new ErrorMessage();
    em.message = message;

    ServletOutputStream out = rsp.getOutputStream();
    try {
      out.write(new Gson().toJson(em).getBytes("UTF-8"));
      out.write('\n');
      out.flush();
    } finally {
      out.close();
    }
  }

  private static String get(HttpServletRequest req, String name, String val) {
    String v = req.getParameter(name);
    if (v == null || v.isEmpty()) {
      return val;
    }
    return v;
  }

  private static boolean get(HttpServletRequest req, String name, boolean val) {
    String v = req.getParameter(name);
    if (v == null || v.isEmpty()) {
      return val;
    }
    return "true".equalsIgnoreCase(v);
  }

  public static class ErrorMessage {
    public final String type = "error";
    public String message;
  }
}
