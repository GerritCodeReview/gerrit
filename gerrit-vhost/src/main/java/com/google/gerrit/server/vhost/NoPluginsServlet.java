// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.vhost;

import com.google.gwtexpui.server.CacheHeaders;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Constants;

import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Returns empty JSON object to mock {@code /plugins/} URL. */
@Singleton
public class NoPluginsServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;
  private final byte[] response = Constants.encode(")]}'\n{}\n");

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
    CacheHeaders.setNotCacheable(res);
    res.setContentType("application/json; charset=utf-8");
    res.setContentLength(response.length);
    OutputStream out = res.getOutputStream();
    out.write(response);
    out.close();
  }
}
