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

package com.google.gerrit.httpd.auth;

public class DefaultAuthorizationPage implements AuthorizationPage {
  @Override
  public String getAuthName() {
    return "Default";
  }

  @Override
  public String getAuthPageContent() {
    return "<div>"
        + "<p><label for='username'>Username:</label>"
        + "<input name='username' id='username' style='float:right;margin:0 5px;'></input></p>"
        + "<p><label for='password'>Password:</label>"
        + "<input name='password' id='password' type='password' style='float:right;margin:0 5px;'></input></p>"
        + "<input type='hidden' name='redirect'></input>"
        + "</div>";
  }
}
