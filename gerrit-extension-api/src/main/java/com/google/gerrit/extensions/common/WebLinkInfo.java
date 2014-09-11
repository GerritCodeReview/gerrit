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

package com.google.gerrit.extensions.common;

public class WebLinkInfo {
  public String name;
  public String imageUrl;
  public String url;
  public String target;

  public WebLinkInfo(String name, String imageUrl, String url, String target) {
    this.name = name;
    this.imageUrl = imageUrl;
    this.url = url;
    this.target = target;
  }
}
