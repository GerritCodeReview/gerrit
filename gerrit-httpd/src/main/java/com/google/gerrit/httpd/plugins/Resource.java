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

package com.google.gerrit.httpd.plugins;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

abstract class Resource {
  static final Resource NOT_FOUND = new Resource() {
    @Override
    int weigh() {
      return 0;
    }

    @Override
    void send(HttpServletRequest req, HttpServletResponse res)
        throws IOException {
      HttpPluginServlet.noCache(res);
      res.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
  };

  abstract int weigh();
  abstract void send(HttpServletRequest req, HttpServletResponse res)
      throws IOException;
}