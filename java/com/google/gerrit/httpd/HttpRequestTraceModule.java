// Copyright (C) 2024 The Android Open Source Project
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

import static com.google.gerrit.httpd.EnableTracingFilter.REQUEST_TRACE_CONTEXT;

import com.google.gerrit.server.logging.TraceContext;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import com.google.inject.servlet.RequestScoped;
import com.google.inject.servlet.ServletModule;
import javax.servlet.http.HttpServletRequest;

public class HttpRequestTraceModule extends ServletModule {

  @Provides
  @RequestScoped
  @Named(REQUEST_TRACE_CONTEXT)
  public TraceContext provideTraceContext(HttpServletRequest req) {
    return (TraceContext) req.getAttribute(REQUEST_TRACE_CONTEXT);
  }

  @Override
  protected void configureServlets() {
    filter("/*").through(EnableTracingFilter.class);
  }
}
