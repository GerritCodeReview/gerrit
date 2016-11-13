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
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.DateFormat;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.DefaultBase;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.DiffView;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.DownloadCommand;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.ReviewCategoryStrategy;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.TimeFormat;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GeneralPreferences extends JavaScriptObject {
  public static GeneralPreferences create() {
    return createObject().cast();
  }

  public static GeneralPreferences createDefault() {
    GeneralPreferencesInfo d = GeneralPreferencesInfo.defaults();
    GeneralPreferences p = createObject().cast();
    p.changesPerPage(d.changesPerPage);
    p.showSiteHeader(d.showSiteHeader);
    p.useFlashClipboard(d.useFlashClipboard);
    p.downloadScheme(d.downloadScheme);
    p.downloadCommand(d.downloadCommand);
    p.dateFormat(d.getDateFormat());
    p.timeFormat(d.getTimeFormat());
    p.highlightAssigneeInChangeTable(d.highlightAssigneeInChangeTable);
    p.relativeDateInChangeTable(d.relativeDateInChangeTable);
    p.sizeBarInChangeTable(d.sizeBarInChangeTable);
    p.legacycidInChangeTable(d.legacycidInChangeTable);
    p.muteCommonPathPrefixes(d.muteCommonPathPrefixes);
    p.signedOffBy(d.signedOffBy);
    p.reviewCategoryStrategy(d.getReviewCategoryStrategy());
    p.diffView(d.getDiffView());
    p.emailStrategy(d.emailStrategy);
    p.defaultBaseForMerges(d.defaultBaseForMerges);
    return p;
  }

  public final int changesPerPage() {
    int changesPerPage = get("changes_per_page", GeneralPreferencesInfo.DEFAULT_PAGESIZE);
    return 0 < changesPerPage ? changesPerPage : GeneralPreferencesInfo.DEFAULT_PAGESIZE;
  }

  private native short get(String n, int d)/*-{ return this.hasOwnProperty(n) ? this[n] : d }-*/ ;

  public final native boolean showSiteHeader()/*-{ return this.show_site_header || false }-*/ ;

  public final native boolean useFlashClipboard()
      /*-{ return this.use_flash_clipboard || false }-*/ ;

  public final native String downloadScheme()/*-{ return this.download_scheme }-*/ ;

  public final DownloadCommand downloadCommand() {
    String s = downloadCommandRaw();
    return s != null ? DownloadCommand.valueOf(s) : null;
  }

  private native String downloadCommandRaw()/*-{ return this.download_command }-*/ ;

  public final DateFormat dateFormat() {
    String s = dateFormatRaw();
    return s != null ? DateFormat.valueOf(s) : null;
  }

  private native String dateFormatRaw()/*-{ return this.date_format }-*/ ;

  public final TimeFormat timeFormat() {
    String s = timeFormatRaw();
    return s != null ? TimeFormat.valueOf(s) : null;
  }

  private native String timeFormatRaw()/*-{ return this.time_format }-*/ ;

  public final native boolean highlightAssigneeInChangeTable()
      /*-{ return this.highlight_assignee_in_change_table || false }-*/ ;

  public final native boolean relativeDateInChangeTable()
      /*-{ return this.relative_date_in_change_table || false }-*/ ;

  public final native boolean sizeBarInChangeTable()
      /*-{ return this.size_bar_in_change_table || false }-*/ ;

  public final native boolean legacycidInChangeTable()
      /*-{ return this.legacycid_in_change_table || false }-*/ ;

  public final native boolean muteCommonPathPrefixes()
      /*-{ return this.mute_common_path_prefixes || false }-*/ ;

  public final native boolean signedOffBy()/*-{ return this.signed_off_by || false }-*/ ;

  public final ReviewCategoryStrategy reviewCategoryStrategy() {
    String s = reviewCategeoryStrategyRaw();
    return s != null ? ReviewCategoryStrategy.valueOf(s) : ReviewCategoryStrategy.NONE;
  }

  private native String reviewCategeoryStrategyRaw()/*-{ return this.review_category_strategy }-*/ ;

  public final DiffView diffView() {
    String s = diffViewRaw();
    return s != null ? DiffView.valueOf(s) : null;
  }

  private native String diffViewRaw()/*-{ return this.diff_view }-*/ ;

  public final EmailStrategy emailStrategy() {
    String s = emailStrategyRaw();
    return s != null ? EmailStrategy.valueOf(s) : null;
  }

  private native String emailStrategyRaw()/*-{ return this.email_strategy }-*/ ;

  public final DefaultBase defaultBaseForMerges() {
    String s = defaultBaseForMergesRaw();
    return s != null ? DefaultBase.valueOf(s) : null;
  }

  private native String defaultBaseForMergesRaw()/*-{ return this.default_base_for_merges }-*/ ;

  public final native JsArray<TopMenuItem> my()/*-{ return this.my; }-*/ ;

  public final native void changesPerPage(int n)/*-{ this.changes_per_page = n }-*/ ;

  public final native void showSiteHeader(boolean s)/*-{ this.show_site_header = s }-*/ ;

  public final native void useFlashClipboard(boolean u)/*-{ this.use_flash_clipboard = u }-*/ ;

  public final native void downloadScheme(String d)/*-{ this.download_scheme = d }-*/ ;

  public final void downloadCommand(DownloadCommand d) {
    downloadCommandRaw(d != null ? d.toString() : null);
  }

  public final native void downloadCommandRaw(String d)/*-{ this.download_command = d }-*/ ;

  public final void dateFormat(DateFormat f) {
    dateFormatRaw(f != null ? f.toString() : null);
  }

  private native void dateFormatRaw(String f)/*-{ this.date_format = f }-*/ ;

  public final void timeFormat(TimeFormat f) {
    timeFormatRaw(f != null ? f.toString() : null);
  }

  private native void timeFormatRaw(String f)/*-{ this.time_format = f }-*/ ;

  public final native void highlightAssigneeInChangeTable(boolean d)
      /*-{ this.highlight_assignee_in_change_table = d }-*/ ;

  public final native void relativeDateInChangeTable(boolean d)
      /*-{ this.relative_date_in_change_table = d }-*/ ;

  public final native void sizeBarInChangeTable(boolean s)
      /*-{ this.size_bar_in_change_table = s }-*/ ;

  public final native void legacycidInChangeTable(boolean s)
      /*-{ this.legacycid_in_change_table = s }-*/ ;

  public final native void muteCommonPathPrefixes(boolean s)
      /*-{ this.mute_common_path_prefixes = s }-*/ ;

  public final native void signedOffBy(boolean s)/*-{ this.signed_off_by = s }-*/ ;

  public final void reviewCategoryStrategy(ReviewCategoryStrategy s) {
    reviewCategoryStrategyRaw(s != null ? s.toString() : null);
  }

  private native void reviewCategoryStrategyRaw(String s)
      /*-{ this.review_category_strategy = s }-*/ ;

  public final void diffView(DiffView d) {
    diffViewRaw(d != null ? d.toString() : null);
  }

  private native void diffViewRaw(String d)/*-{ this.diff_view = d }-*/ ;

  public final void emailStrategy(EmailStrategy s) {
    emailStrategyRaw(s != null ? s.toString() : null);
  }

  private native void emailStrategyRaw(String s)/*-{ this.email_strategy = s }-*/ ;

  public final void defaultBaseForMerges(DefaultBase b) {
    defaultBaseForMergesRaw(b != null ? b.toString() : null);
  }

  private native void defaultBaseForMergesRaw(String b)/*-{ this.default_base_for_merges = b }-*/ ;

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

  private native String urlAliasToken(String m) /*-{ return this.url_aliases[m]; }-*/;

  private native NativeMap<NativeString> _urlAliases() /*-{ return this.url_aliases; }-*/;

  public final void setUrlAliases(Map<String, String> urlAliases) {
    initUrlAliases();
    for (Map.Entry<String, String> e : urlAliases.entrySet()) {
      putUrlAlias(e.getKey(), e.getValue());
    }
  }

  private native void putUrlAlias(String m, String t) /*-{ this.url_aliases[m] = t; }-*/;

  private native void initUrlAliases() /*-{ this.url_aliases = {}; }-*/;

  protected GeneralPreferences() {}
}
