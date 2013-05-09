// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

class AuditedHttpServletResponse
    extends HttpServletResponseWrapper
    implements HttpServletResponse {
  private int status;

  AuditedHttpServletResponse(HttpServletResponse response) {
    super(response);
  }

  public int getStatus() {
    return status;
  }

  @Override
  public void setStatus(int sc) {
    super.setStatus(sc);
    this.status = sc;
  }

  @SuppressWarnings("deprecation")
  @Override
  public void setStatus(int sc, String sm) {
    super.setStatus(sc, sm);
    this.status = sc;
  }

  @Override
  public void sendError(int sc) throws IOException {
    super.sendError(sc);
    this.status = sc;
  }

  @Override
  public void sendError(int sc, String msg) throws IOException {
    super.sendError(sc, msg);
    this.status = sc;
  }

  @Override
  public void sendRedirect(String location) throws IOException {
    super.sendRedirect(location);
    this.status = SC_MOVED_TEMPORARILY;
  }
}
