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
package com.google.gerrit.audit;

import com.google.common.base.Preconditions;
import com.google.common.collect.Multimap;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.CurrentUser;

import javax.servlet.http.HttpServletRequest;

/**
 * Extended audit event. Adds request, resource and view data to HttpAuditEvent.
 */
public class HttpExtendedAuditEvent extends HttpAuditEvent {
  public final HttpServletRequest req;
  public final RestResource rsrc;
  public final RestView<? extends RestResource> view;

  /**
   * Creates a new audit event with results
   *
   * @param sessionId session id the event belongs to
   * @param who principal that has generated the event
   * @param req the HttpServletRequest
   * @param when time-stamp of when the event started
   * @param params parameters of the event
   * @param result result of the event
   * @param rsrc REST resource data
   * @param view view rendering object
   */
  public HttpExtendedAuditEvent(String sessionId, CurrentUser who, HttpServletRequest req,
      long when, Multimap<String, ?> params, Object input, int status, Object result,
      RestResource rsrc, RestView<RestResource> view) {
    super(sessionId, who, req.getRequestURI(), when, params, req.getMethod(), input, status,
        result);
    this.req = Preconditions.checkNotNull(req);
    this.rsrc = rsrc;
    this.view = view;
  }
}
