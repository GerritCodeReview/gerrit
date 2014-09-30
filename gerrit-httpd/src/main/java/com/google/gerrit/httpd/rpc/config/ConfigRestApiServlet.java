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

package com.google.gerrit.httpd.rpc.config;

import com.google.gerrit.httpd.restapi.RestApiServlet;
import com.google.gerrit.server.config.ConfigCollection;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class ConfigRestApiServlet extends RestApiServlet {
  private static final long serialVersionUID = 1L;

  @Inject
  ConfigRestApiServlet(RestApiServlet.Globals globals,
      Provider<ConfigCollection> configCollection) {
    super(globals, configCollection);
  }
}
