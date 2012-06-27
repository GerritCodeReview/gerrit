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

package com.google.gerrit.server.schema;

import com.google.gerrit.reviewdb.server.ReviewDb;

import java.io.InputStream;

public abstract class BaseDataSourceType implements DataSourceType {

  private final String driver;
  private final String indexScript;
  private final String nextValScript;

  protected BaseDataSourceType(String driver, String indexScript, String nextValScript) {
    this.driver = driver;
    this.indexScript = indexScript;
    this.nextValScript = nextValScript;
  }

  @Override
  public final String getDriver() {
    return driver;
  }

  @Override
  public boolean usePool() {
    return true;
  }

  @Override
  public final NamedInputStream getIndexScript() {
    return getScriptAsStream(indexScript);
  }

  @Override
  public final NamedInputStream getNextValScript() {
    return getScriptAsStream(nextValScript);
  }

  private static final NamedInputStream getScriptAsStream(String name) {
    if (name == null) {
      return null;
    }
    InputStream in =  ReviewDb.class.getResourceAsStream(name);
    if (in == null) {
      throw new IllegalStateException("SQL script " + name + " not found");
    }
    return new NamedInputStream(name, in);
  }
}
