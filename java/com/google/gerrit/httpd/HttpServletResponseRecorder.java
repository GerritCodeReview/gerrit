// Copyright (C) 2016 The Android Open Source Project
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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HttpServletResponse wrapper to allow response status code override.
 *
 * <p>Differently from the normal HttpServletResponse, this class allows multiple filters to
 * override the response http status code.
 */
public class HttpServletResponseRecorder extends HttpServletResponseWrapper {
  private static final Logger log = LoggerFactory.getLogger(HttpServletResponseWrapper.class);
  private static final String LOCATION_HEADER = "Location";

  private int status;
  private String statusMsg = "";
  private Map<String, String> headers = new HashMap<>();

  /**
   * Constructs a response recorder wrapping the given response.
   *
   * @param response the response to be wrapped
   */
  public HttpServletResponseRecorder(HttpServletResponse response) {
    super(response);
  }

  @Override
  public void sendError(int sc) throws IOException {
    this.status = sc;
  }

  @Override
  public void sendError(int sc, String msg) throws IOException {
    this.status = sc;
    this.statusMsg = msg;
  }

  @Override
  public void sendRedirect(String location) throws IOException {
    this.status = SC_MOVED_TEMPORARILY;
    setHeader(LOCATION_HEADER, location);
  }

  @Override
  public void setHeader(String name, String value) {
    super.setHeader(name, value);
    headers.put(name, value);
  }

  @SuppressWarnings("all")
  // @Override is omitted for backwards compatibility with servlet-api 2.5
  // TODO: Remove @SuppressWarnings and add @Override when Google upgrades
  //       to servlet-api 3.1
  public int getStatus() {
    return status;
  }

  void play() throws IOException {
    if (status != 0) {
      log.debug("Replaying {} {}", status, statusMsg);

      if (status == SC_MOVED_TEMPORARILY) {
        super.sendRedirect(headers.get(LOCATION_HEADER));
      } else {
        super.sendError(status, statusMsg);
      }
    }
  }
}
