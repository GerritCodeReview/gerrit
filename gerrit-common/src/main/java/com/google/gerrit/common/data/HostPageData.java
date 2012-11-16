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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;

import java.util.List;

/** Data sent as part of the host page, to bootstrap the UI. */
public class HostPageData {
  public Account account;
  public AccountDiffPreference accountDiffPref;
  public String xGerritAuth;
  public GerritConfig config;
  public Theme theme;
  public List<String> plugins;
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
}
