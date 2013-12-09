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

package com.google.gerrit.client.api;

import com.google.gerrit.client.ErrorDialog;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.Natives;
import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.CodeDownloadException;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.ScriptInjector;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwtexpui.progress.client.ProgressBar;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loads JavaScript plugins with a progress meter visible. */
public class PluginLoader extends DialogBox {
  private static final int MAX_STARTUP_WAIT = 5000; // milliseconds
  private static PluginLoader self;

  public static void load(List<String> plugins, final String token) {
    if (plugins == null || plugins.isEmpty()) {
      Gerrit.display(token);
    } else {
      self = new PluginLoader(token);
      self.load(plugins);
      self.center();
    }
  }

  static void loaded(PluginInstance p) {
    self.done(p.url(), p.success());
  }

  private final String token;
  private ProgressBar progress;
  private int total;
  private Set<String> pending;
  private Map<String, Exception> failures;
  private boolean visible;
  private Timer show;
  private Timer update;
  private Timer timeout;

  private PluginLoader(String token) {
    super(/* auto hide */false, /* modal */true);
    this.token = token;
    progress = new ProgressBar(Gerrit.C.loadingPlugins());

    setStyleName(Gerrit.RESOURCES.css().errorDialog());
    addStyleName(Gerrit.RESOURCES.css().loadingPluginsDialog());
  }

  private void load(List<String> plugins) {
    total = plugins.size();
    pending = new LinkedHashSet<String>(plugins.size());
    failures = new HashMap<String, Exception>();

    String serverUrl = GWT.getHostPageBaseURL();
    for (String url : plugins) {
      if (!url.startsWith("http:") && !url.startsWith("https:")) {
        url = serverUrl + url;
      }
      ScriptInjector.fromUrl(url)
        .setWindow(ScriptInjector.TOP_WINDOW)
        .setCallback(new LoadCallback(url))
        .inject();
      pending.add(url);
    }

    show = new Timer() {
      @Override
      public void run() {
        setText(Window.getTitle());
        setWidget(progress);
        setGlassEnabled(true);
        getGlassElement().addClassName(Gerrit.RESOURCES.css().errorDialogGlass());
        hide(true);
        center();
        visible = true;
      }
    };
    show.schedule(500);

    update = new Timer() {
      private int cycle;

      @Override
      public void run() {
        progress.setValue(100 * ++cycle * 250 / MAX_STARTUP_WAIT);
      }
    };
    update.scheduleRepeating(250);

    timeout = new Timer() {
      @Override
      public void run() {
        finish();
      }
    };
    timeout.schedule(MAX_STARTUP_WAIT);
  }

  private void done(String url, boolean success) {
    pending.remove(url);

    if (!success && !failures.containsKey(url)) {
      failures.put(url, new Exception());
    }
    if (pending.isEmpty()) {
      finish();
    }
  }

  private void finish() {
    show.cancel();
    update.cancel();
    timeout.cancel();
    self = null;

    if (!processFailures()) {
      if (visible) {
        progress.setValue(100);
        new Timer() {
          @Override
          public void run() {
            hide(true);
          }
        }.schedule(250);
      } else {
        hide(true);
      }
    }

    Gerrit.display(token);
  }

  private boolean processFailures() {
    Map<String, PluginInstance> byUrl = new HashMap<String, PluginInstance>();
    for (PluginInstance p : Natives.asList(plugins())) {
      if (!p.success()) {
        byUrl.put(p.url(), p);
      }
    }

    boolean failed = false;
    if (!pending.isEmpty() || !byUrl.isEmpty()) {
      hide(true);
      failed = true;
    }

    for (String url : pending) {
      PluginInstance p = byUrl.remove(url);
      if (p != null) {
        new ErrorDialog(Gerrit.M.pluginFailed(p.name())).center();
        continue;
      }

      Exception e = failures.get(url);
      if (e instanceof CodeDownloadException) {
        new ErrorDialog(Gerrit.M.cannotDownloadPlugin(url)).center();
        continue;
      }

      String name = PluginName.fromUrlOrNull(url);
      if (name != null) {
        new ErrorDialog(Gerrit.M.pluginFailed(name)).center();
      } else {
        new ErrorDialog(Gerrit.M.cannotDownloadPlugin(url)).center();
      }
    }

    for (PluginInstance p : byUrl.values()) {
      new ErrorDialog(Gerrit.M.pluginFailed(p.name())).center();
    }
    return failed;
  }

  private static native JsArray<PluginInstance> plugins()
  /*-{ return $wnd.Gerrit.plugins }-*/;

  private class LoadCallback implements Callback<Void, Exception> {
    private final String url;

    LoadCallback(String url) {
      this.url = url;
    }

    @Override
    public void onSuccess(Void result) {
    }

    @Override
    public void onFailure(Exception reason) {
      failures.put(url, reason);
      done(url, false);
    }
  }
}
