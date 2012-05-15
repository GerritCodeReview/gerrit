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

import com.google.common.base.Strings;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.util.cli.CmdLineParser;
import com.google.gwtjsonrpc.common.JsonConstants;
import com.google.gwtjsonrpc.server.RPCServletUtils;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.kohsuke.args4j.CmdLineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public abstract class RestApiServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;
  private static final Logger log =
      LoggerFactory.getLogger(RestApiServlet.class);

  private final Provider<CurrentUser> currentUser;

  /** MIME type used for a JSON response body. */
  protected static final String JSON_TYPE = JsonConstants.JSON_TYPE;

  /**
   * Garbage prefix inserted before JSON output to prevent XSSI.
   * <p>
   * This prefix is ")]}'\n" and is designed to prevent a web browser from
   * executing the response body if the resource URI were to be referenced using
   * a &lt;script src="...&gt; HTML tag from another web site. Clients using the
   * HTTP interface will need to always strip the first line of response data to
   * remove this magic header.
   */
  protected static final byte[] JSON_MAGIC;

  static {
    try {
      JSON_MAGIC = ")]}'\n".getBytes("UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("UTF-8 not supported", e);
    }
  }

  @Inject
  protected RestApiServlet(final Provider<CurrentUser> currentUser) {
    this.currentUser = currentUser;
  }

  @Override
  protected void service(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    noCache(res);
    try {
      checkRequiresCapability();
      super.service(req, res);
    } catch (Error err) {
      handleError(err, req, res);
    } catch (RuntimeException err) {
      handleError(err, req, res);
    }
  }

  private void checkRequiresCapability() throws RuntimeException {
    RequiresCapability rc = getClass().getAnnotation(RequiresCapability.class);
    if (rc != null) {
      CurrentUser user = currentUser.get();
      CapabilityControl ctl = user.getCapabilities();
      if (!ctl.canPerform(rc.value()) && !ctl.canAdministrateServer()) {
        String msg = String.format(
            "fatal: %s does not have \"%s\" capability.",
            user.getUserName(), rc.value());
        throw new RuntimeException(msg);
      }
    }
  }

  private static void noCache(HttpServletResponse res) {
    res.setHeader("Expires", "Fri, 01 Jan 1980 00:00:00 GMT");
    res.setHeader("Pragma", "no-cache");
    res.setHeader("Cache-Control", "no-cache, must-revalidate");
    res.setHeader("Content-Disposition", "attachment");
  }

  private static void handleError(
      Throwable err, HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    String uri = req.getRequestURI();
    if (!Strings.isNullOrEmpty(req.getQueryString())) {
      uri += "?" + req.getQueryString();
    }
    log.error(String.format("Error in %s %s", req.getMethod(), uri), err);

    if (!res.isCommitted()) {
      res.reset();
      res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      noCache(res);
      sendText(req, res, "Internal Server Error");
    }
  }

  protected static boolean acceptsJson(HttpServletRequest req) {
    String accept = req.getHeader("Accept");
    if (accept == null) {
      return false;
    } else if (JSON_TYPE.equals(accept)) {
      return true;
    } else if (accept.startsWith(JSON_TYPE + ",")) {
      return true;
    }
    for (String p : accept.split("[ ,;][ ,;]*")) {
      if (JSON_TYPE.equals(p)) {
        return true;
      }
    }
    return false;
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
