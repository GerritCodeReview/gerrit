// Copyright (C) 2014 The Android Open Source Project
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
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.net.MalformedURLException;
import java.net.URL;
import org.eclipse.jgit.lib.Config;

@Singleton
class ProxyPropertiesProvider implements Provider<ProxyProperties> {

  private URL proxyUrl;
  private String proxyUser;
  private String proxyPassword;

  @Inject
  ProxyPropertiesProvider(@GerritServerConfig Config config) throws MalformedURLException {
    String proxyUrlStr = config.getString("http", null, "proxy");
    if (!Strings.isNullOrEmpty(proxyUrlStr)) {
      proxyUrl = new URL(proxyUrlStr);
      proxyUser = config.getString("http", null, "proxyUsername");
      proxyPassword = config.getString("http", null, "proxyPassword");
      String userInfo = proxyUrl.getUserInfo();
      if (userInfo != null) {
        int c = userInfo.indexOf(':');
        if (0 < c) {
          proxyUser = userInfo.substring(0, c);
          proxyPassword = userInfo.substring(c + 1);
        } else {
          proxyUser = userInfo;
        }
      }
    }
  }

  @Override
  public ProxyProperties get() {
    return new ProxyProperties() {
      @Override
      public URL getProxyUrl() {
        return proxyUrl;
      }

      @Override
      public String getUsername() {
        return proxyUser;
      }

      @Override
      public String getPassword() {
        return proxyPassword;
      }
    };
  }
}
