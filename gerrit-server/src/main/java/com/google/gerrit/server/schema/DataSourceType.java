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


/** Abstraction of a supported database platform */
public interface DataSourceType {

  public String getDriver();

  public String getUrl();

  public boolean usePool();

  /**
   * Return an input stream to read the index script from or <code>null</code>
   * if index script is not defined for this data source type
   */
  public NamedInputStream getIndexScript();

  /**
   * Return an input stream to read the nextVal script from or <code>null</code>
   * if nextVal script is not defined for this data source type
   */
  public NamedInputStream getNextValScript();
}
