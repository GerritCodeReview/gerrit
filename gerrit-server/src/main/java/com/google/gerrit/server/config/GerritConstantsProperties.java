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

package com.google.gerrit.server.config;

import com.google.common.base.Objects;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Singleton
public class GerritConstantsProperties extends Properties {
  private static final long serialVersionUID = -2184835383055733223L;
  private static final String GERRIT_CONSTANTS_PROPERTIES =
      "/com/google/gerrit/client/GerritConstants.properties";
  private static final Logger log = LoggerFactory
      .getLogger(GerritConstantsProperties.class);

  @Inject
  public GerritConstantsProperties() throws IOException {
    InputStream constantsStream =
        getClass().getResourceAsStream(GERRIT_CONSTANTS_PROPERTIES);
    if (constantsStream != null) {
      try {
        load(constantsStream);
      } finally {
        constantsStream.close();
      }
    } else {
      log.error("Cannot locate Gerrit constants {} from classpath",
          GERRIT_CONSTANTS_PROPERTIES);
    }
  }

  @Override
  public synchronized Object get(Object key) {
    return Objects.firstNonNull(super.get(key), key);
  }

  @Override
  public String getProperty(String key) {
    return Objects.firstNonNull(super.getProperty(key), key);
  }
}
