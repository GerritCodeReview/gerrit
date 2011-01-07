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

package com.google.gerrit.httpd.gitweb;

import com.google.inject.servlet.ServletModule;

public class GitWebModule extends ServletModule {
  @Override
  protected void configureServlets() {
    serve("/gitweb").with(GitWebServlet.class);
    serve("/gitweb/*").with(GitWebServlet.class);
    serve("/gitweb-logo.png").with(GitLogoServlet.class);
    serve("/gitweb.js").with(GitWebJavaScriptServlet.class);
    serve("/gitweb-default.css").with(GitWebCssServlet.Default.class);
    serve("/gitweb-site.css").with(GitWebCssServlet.Site.class);
  }
}
