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

package com.google.gerrit.client.info;

import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.NativeString;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DateFormat;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DiffView;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DownloadCommand;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.EmailStrategy;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.ReviewCategoryStrategy;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.TimeFormat;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AccountPreferencesInfo extends JavaScriptObject {
  public static AccountPreferencesInfo create() {
    return createObject().cast();
  }

  public static AccountPreferencesInfo createDefault() {
    AccountGeneralPreferences defaultPrefs =
        AccountGeneralPreferences.createDefault();
    AccountPreferencesInfo p = createObject().cast();
    p.changesPerPage(defaultPrefs.getMaximumPageSize());
    p.showSiteHeader(defaultPrefs.isShowSiteHeader());
    p.useFlashClipboard(defaultPrefs.isUseFlashClipboard());
    p.downloadScheme(defaultPrefs.getDownloadUrl());
    p.downloadCommand(defaultPrefs.getDownloadCommand());
    p.dateFormat(defaultPrefs.getDateFormat());
    p.timeFormat(defaultPrefs.getTimeFormat());
    p.relativeDateInChangeTable(defaultPrefs.isRelativeDateInChangeTable());
    p.sizeBarInChangeTable(defaultPrefs.isSizeBarInChangeTable());
    p.legacycidInChangeTable(defaultPrefs.isLegacycidInChangeTable());
    p.muteCommonPathPrefixes(defaultPrefs.isMuteCommonPathPrefixes());
    p.reviewCategoryStrategy(defaultPrefs.getReviewCategoryStrategy());
    p.diffView(defaultPrefs.getDiffView());
    p.emailStrategy(defaultPrefs.getEmailStrategy());
    return p;
  }

  public final short changesPerPage() {
    short changesPerPage =
        get("changes_per_page", AccountGeneralPreferences.DEFAULT_PAGESIZE);
    return 0 < changesPerPage
        ? changesPerPage
        : AccountGeneralPreferences.DEFAULT_PAGESIZE;
  }
  private final native short get(String n, int d)
  /*-{ return this.hasOwnProperty(n) ? this[n] : d }-*/;

  public final native boolean showSiteHeader()
  /*-{ return this.show_site_header || false }-*/;

  public final native boolean useFlashClipboard()
  /*-{ return this.use_flash_clipboard || false }-*/;

  public final native String downloadScheme()
  /*-{ return this.download_scheme }-*/;

  public final DownloadCommand downloadCommand() {
    String s = downloadCommandRaw();
    return s != null ? DownloadCommand.valueOf(s) : null;
  }
  private final native String downloadCommandRaw()
  /*-{ return this.download_command }-*/;

  public final DateFormat dateFormat() {
    String s = dateFormatRaw();
    return s != null ? DateFormat.valueOf(s) : null;
  }
  private final native String dateFormatRaw()
  /*-{ return this.date_format }-*/;

  public final TimeFormat timeFormat() {
    String s = timeFormatRaw();
    return s != null ? TimeFormat.valueOf(s) : null;
  }
  private final native String timeFormatRaw()
  /*-{ return this.time_format }-*/;

  public final native boolean relativeDateInChangeTable()
  /*-{ return this.relative_date_in_change_table || false }-*/;

  public final native boolean sizeBarInChangeTable()
  /*-{ return this.size_bar_in_change_table || false }-*/;

  public final native boolean legacycidInChangeTable()
  /*-{ return this.legacycid_in_change_table || false }-*/;

  public final native boolean muteCommonPathPrefixes()
  /*-{ return this.mute_common_path_prefixes || false }-*/;

  public final ReviewCategoryStrategy reviewCategoryStrategy() {
    String s = reviewCategeoryStrategyRaw();
    return s != null ? ReviewCategoryStrategy.valueOf(s) : ReviewCategoryStrategy.NONE;
  }
  private final native String reviewCategeoryStrategyRaw()
  /*-{ return this.review_category_strategy }-*/;

  public final DiffView diffView() {
    String s = diffViewRaw();
    return s != null ? DiffView.valueOf(s) : null;
  }
  private final native String diffViewRaw()
  /*-{ return this.diff_view }-*/;

  public final EmailStrategy emailStrategy() {
    String s = emailStrategyRaw();
    return s != null ? EmailStrategy.valueOf(s) : null;
  }

  private final native String emailStrategyRaw()
  /*-{ return this.email_strategy }-*/;

  public final native JsArray<TopMenuItem> my()
  /*-{ return this.my; }-*/;

  public final native void changesPerPage(short n)
  /*-{ this.changes_per_page = n }-*/;

  public final native void showSiteHeader(boolean s)
  /*-{ this.show_site_header = s }-*/;

  public final native void useFlashClipboard(boolean u)
  /*-{ this.use_flash_clipboard = u }-*/;

  public final native void downloadScheme(String d)
  /*-{ this.download_scheme = d }-*/;

  public final void downloadCommand(DownloadCommand d) {
    downloadCommandRaw(d != null ? d.toString() : null);
  }
  public final native void downloadCommandRaw(String d)
  /*-{ this.download_command = d }-*/;

  public final void dateFormat(DateFormat f) {
    dateFormatRaw(f != null ? f.toString() : null);
  }
  private final native void dateFormatRaw(String f)
  /*-{ this.date_format = f }-*/;

  public final void timeFormat(TimeFormat f) {
    timeFormatRaw(f != null ? f.toString() : null);
  }
  private final native void timeFormatRaw(String f)
  /*-{ this.time_format = f }-*/;

  public final native void relativeDateInChangeTable(boolean d)
  /*-{ this.relative_date_in_change_table = d }-*/;

  public final native void sizeBarInChangeTable(boolean s)
  /*-{ this.size_bar_in_change_table = s }-*/;

  public final native void legacycidInChangeTable(boolean s)
  /*-{ this.legacycid_in_change_table = s }-*/;

  public final native void muteCommonPathPrefixes(boolean s)
  /*-{ this.mute_common_path_prefixes = s }-*/;

  public final void reviewCategoryStrategy(ReviewCategoryStrategy s) {
    reviewCategoryStrategyRaw(s != null ? s.toString() : null);
  }
  private final native void reviewCategoryStrategyRaw(String s)
  /*-{ this.review_category_strategy = s }-*/;

  public final void diffView(DiffView d) {
    diffViewRaw(d != null ? d.toString() : null);
  }
  private final native void diffViewRaw(String d)
  /*-{ this.diff_view = d }-*/;

  public final void emailStrategy(EmailStrategy s) {
    emailStrategyRaw(s != null ? s.toString() : null);
  }
  private final native void emailStrategyRaw(String s)
  /*-{ this.email_strategy = s }-*/;

  public final void setMyMenus(List<TopMenuItem> myMenus) {
    initMy();
    for (TopMenuItem n : myMenus) {
      addMy(n);
    }
  }
  final native void initMy() /*-{ this.my = []; }-*/;
  final native void addMy(TopMenuItem m) /*-{ this.my.push(m); }-*/;

  public final Map<String, String> urlAliases() {
    Map<String, String> urlAliases = new HashMap<>();
    for (String k : Natives.keys(_urlAliases())) {
      urlAliases.put(k, urlAliasToken(k));
    }
    return urlAliases;
  }

  private final native String urlAliasToken(String m) /*-{ return this.url_aliases[m]; }-*/;
  private final native NativeMap<NativeString> _urlAliases() /*-{ return this.url_aliases; }-*/;

  public final void setUrlAliases(Map<String, String> urlAliases) {
    initUrlAliases();
    for (Map.Entry<String, String> e : urlAliases.entrySet()) {
      putUrlAlias(e.getKey(), e.getValue());
    }
  }
  private final native void putUrlAlias(String m, String t) /*-{ this.url_aliases[m] = t; }-*/;
  private final native void initUrlAliases() /*-{ this.url_aliases = {}; }-*/;

  protected AccountPreferencesInfo() {
  }
}
