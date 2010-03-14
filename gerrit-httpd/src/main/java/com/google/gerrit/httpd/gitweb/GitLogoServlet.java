// Copyright (C) 2009 The Android Open Source Project
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

// CGI environment and execution management portions are:
//
// ========================================================================
// Copyright (c) 2006-2009 Mort Bay Consulting Pty. Ltd.
// ------------------------------------------------------------------------
// All rights reserved. This program and the accompanying materials
// are made available under the terms of the Eclipse Public License v1.0
// and Apache License v2.0 which accompanies this distribution.
// The Eclipse Public License is available at
// http://www.eclipse.org/legal/epl-v10.html
// The Apache License v2.0 is available at
// http://www.opensource.org/licenses/apache2.0.php
// You may elect to redistribute this code under either of these licenses.
// ========================================================================

package com.google.gerrit.httpd.gitweb;

import com.google.gerrit.httpd.GitWebConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.util.IO;

import java.io.FileNotFoundException;
import java.io.IOException;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("serial")
@Singleton
class GitLogoServlet extends HttpServlet {
  private final byte[] raw;

  @Inject
  GitLogoServlet(final GitWebConfig gitWebConfig) throws IOException {
    byte[] png;
    if (gitWebConfig.getGitLogoPNG() != null) {
      try {
        png = IO.readFully(gitWebConfig.getGitLogoPNG());
      } catch (FileNotFoundException e) {
        png = null;
      }
    } else {
      png = null;
    }
    raw = png;
  }

  @Override
  protected void doGet(final HttpServletRequest req,
      final HttpServletResponse rsp) throws IOException {
    if (raw != null) {
      rsp.setContentType("image/png");
      rsp.setContentLength(raw.length);

      final ServletOutputStream os = rsp.getOutputStream();
      try {
        os.write(raw);
      } finally {
        os.close();
      }
    } else {
      rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
  }
}
