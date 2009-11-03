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

package com.google.gwtexpui.safehtml.client;

/** A Find/Replace pair used against the SafeHtml block of text */
public class RegexFindReplace {
  private String find;
  private String replace;

  protected RegexFindReplace() {
  }

  public RegexFindReplace(final String find, final String replace) {
    this.find = find;
    this.replace = replace;
  }

  public String find() {
    return find;
  }

  public String replace() {
    return replace;
  }

  @Override
  public String toString() {
    return "find = " + find + ", replace = " + replace;
  }
}
