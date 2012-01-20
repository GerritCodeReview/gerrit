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

package com.google.gerrit.httpd.auth.openid;

import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.io.IOException;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
class XrdsServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;
  private static final String ENC = "UTF-8";
  static final String LOCATION = "OpenID.XRDS";

  private final Provider<String> url;

  @Inject
  XrdsServlet(@CanonicalWebUrl final Provider<String> url) {
    this.url = url;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse rsp)
      throws IOException {
    final StringBuilder r = new StringBuilder();
    r.append("<?xml version=\"1.0\" encoding=\"" + ENC + "\"?>");
    r.append("<xrds:XRDS");
    r.append(" xmlns:xrds=\"xri://$xrds\"");
    r.append(" xmlns:openid=\"http://openid.net/xmlns/1.0\"");
    r.append(" xmlns=\"xri://$xrd*($v*2.0)\">");
    r.append("<XRD>");
    r.append("<Service priority=\"1\">");
    r.append("<Type>http://specs.openid.net/auth/2.0/return_to</Type>");
    r.append("<URI>" + url.get() + OpenIdServiceImpl.RETURN_URL + "</URI>");
    r.append("</Service>");
    r.append("</XRD>");
    r.append("</xrds:XRDS>");
    r.append("\n");

    final byte[] raw = r.toString().getBytes(ENC);
    rsp.setContentLength(raw.length);
    rsp.setContentType("application/xrds+xml");
    rsp.setCharacterEncoding(ENC);

    final ServletOutputStream out = rsp.getOutputStream();
    try {
      out.write(raw);
    } finally {
      out.close();
    }
  }
}
