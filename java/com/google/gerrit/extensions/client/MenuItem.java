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

package com.google.gerrit.extensions.client;

public class MenuItem {
  public final String url;
  public final String name;
  public final String target;
  public final String id;

  // Needed for GWT
  public MenuItem() {
    this(null, null, null, null);
  }

  public MenuItem(String name, String url) {
    this(name, url, "_blank");
  }

  public MenuItem(String name, String url, String target) {
    this(name, url, target, null);
  }

  public MenuItem(String name, String url, String target, String id) {
    this.url = url;
    this.name = name;
    this.target = target;
    this.id = id;
  }
}
