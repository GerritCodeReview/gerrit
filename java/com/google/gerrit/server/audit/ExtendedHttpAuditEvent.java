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

package com.google.gerrit.server.audit;

import com.google.common.collect.ListMultimap;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.CurrentUser;

/** Extended audit event. Adds request, resource and view data to HttpAuditEvent. */
public class ExtendedHttpAuditEvent extends HttpAuditEvent {
  public final RestResource resource;
  public final RestView<? extends RestResource> view;

  /**
   * Creates a new audit event with results
   *
   * @param sessionId session id the event belongs to
   * @param who principal that has generated the event
   * @param requestUri request URI
   * @param httpMethod HTTP method
   * @param when time-stamp of when the event started
   * @param params parameters of the event
   * @param input input
   * @param status HTTP status
   * @param result result of the event
   * @param resource REST resource data
   * @param view view rendering object
   */
  public ExtendedHttpAuditEvent(
      String sessionId,
      CurrentUser who,
      String requestUri,
      String httpMethod,
      long when,
      ListMultimap<String, ?> params,
      Object input,
      int status,
      Object result,
      RestResource resource,
      RestView<RestResource> view) {
    super(sessionId, who, requestUri, when, params, httpMethod, input, status, result);
    this.resource = resource;
    this.view = view;
  }
}
