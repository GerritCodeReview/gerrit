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

package com.google.gerrit.common.data;

import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import java.util.Date;
import java.util.List;

/** Data sent as part of the host page, to bootstrap the UI. */
public class HostPageData {
  /**
   * Name of the cookie in which the XSRF token is sent from the server to the client during host
   * page bootstrapping.
   */
  public static final String XSRF_COOKIE_NAME = "XSRF_TOKEN";

  /**
   * Name of the HTTP header in which the client must send the XSRF token to the server on each
   * request.
   */
  public static final String XSRF_HEADER_NAME = "X-Gerrit-Auth";

  public String version;
  public DiffPreferencesInfo accountDiffPref;
  public Theme theme;
  public List<String> plugins;
  public List<Message> messages;
  public Integer pluginsLoadTimeout;
  public boolean isNoteDbEnabled;
  public boolean canLoadInIFrame;
  public List<String> authPages;

  public static class Theme {
    public String backgroundColor;
    public String topMenuColor;
    public String textColor;
    public String trimColor;
    public String selectionColor;
    public String changeTableOutdatedColor;
    public String tableOddRowColor;
    public String tableEvenRowColor;
  }

  public static class Message {
    public String id;
    public Date redisplay;
    public String html;
  }
}
