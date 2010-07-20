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
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
public class RssServlet extends HttpServlet {
  private final Provider<QueryProcessor> processor;

  @Inject
  RssServlet(Provider<QueryProcessor> processor) {
    this.processor = processor;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse rsp)
      throws IOException {
    rsp.setContentType("application/rss+xml");
    rsp.setCharacterEncoding("UTF-8");

    QueryProcessor p = processor.get();
    p.setOutput(rsp.getOutputStream(), OutputFormat.RSS);
    p.setDefaultLimit(50);
    p.query(get(req, "q", "status:open"));
  }

  private static String get(HttpServletRequest req, String name, String val) {
    String v = req.getParameter(name);
    if (v == null || v.isEmpty()) {
      return val;
    }
    return v;
  }
}
