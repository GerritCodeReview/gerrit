// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.httpd.raw;

import com.google.common.escape.Escaper;
import com.google.common.html.HtmlEscapers;
import com.google.gwtexpui.server.CacheHeaders;

import org.eclipse.jgit.util.RawParseUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;

import javax.servlet.http.HttpServletResponse;

import static java.nio.charset.StandardCharsets.UTF_8;

public interface BuildSystem {

  // Represents a label in either buck or bazel.
  class Label {
    public final String pkg;
    public final String name;

    // regrettably, buck confounds rule names and artifact names,
    // and so we have to lug this along.
    public final String artifact;


    public String fullName() {
      return  "//" + pkg + ":" + name;
    }


    @Override
    public String toString() {
      String s = fullName();
      if (!name.equals(artifact)) {
        s += "(" + artifact + ")";
      }
      return s;
    }

    // Label in Buck style.
    Label(String pkg, String name, String artifact) {
      this.name = name;
      this.pkg = pkg;
      this.artifact = artifact;
    }

    // Label in Bazel style.
    Label(String pkg, String name) {
      this(pkg, name, name);
    }
  }

  class BuildFailureException extends Exception {
    private static final long serialVersionUID = 1L;

    final byte[] why;

    BuildFailureException(byte[] why) {
      this.why = why;
    }

    public void display(String rule, HttpServletResponse res)
        throws IOException {
      res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      res.setContentType("text/html");
      res.setCharacterEncoding(UTF_8.name());
      CacheHeaders.setNotCacheable(res);

      Escaper html = HtmlEscapers.htmlEscaper();
      try (PrintWriter w = res.getWriter()) {
        w.write("<html><title>BUILD FAILED</title><body>");
        w.format("<h1>%s FAILED</h1>", html.escape(rule));
        w.write("<pre>");
        w.write(html.escape(RawParseUtils.decode(why)));
        w.write("</pre>");
        w.write("</body></html>");
      }
    }
  }

  /** returns the command to build given target */
  String buildCommand(Label l);

  /** builds the given label. */
  void build(Label l) throws IOException, BuildFailureException;

  /** returns the root relative path to the artifact for the given label */
  Path targetPath(Label l);
}
