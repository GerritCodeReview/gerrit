// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.extensions.common;

public class PluginInfo {
  public String id;
  public String version;
  public String apiVersion;
  public String indexUrl;
  public String filename;
  public Boolean disabled;

  public PluginInfo(
      String id,
      String version,
      String apiVersion,
      String indexUrl,
      String filename,
      Boolean disabled) {
    this.id = id;
    this.version = version;
    this.apiVersion = apiVersion;
    this.indexUrl = indexUrl;
    this.filename = filename;
    this.disabled = disabled;
  }

  public PluginInfo() {}
}
