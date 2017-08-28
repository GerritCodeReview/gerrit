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

package com.google.gerrit.extensions.api.projects;

public class ThemeInfo {
  public static final ThemeInfo INHERIT = new ThemeInfo(null, null, null);

  public final String css;
  public final String header;
  public final String footer;

  public ThemeInfo(String css, String header, String footer) {
    this.css = css;
    this.header = header;
    this.footer = footer;
  }
}
