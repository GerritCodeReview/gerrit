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
import com.google.gerrit.client.VoidResult;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.Natives;
import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.CodeDownloadException;
import com.google.gwt.core.client.ScriptInjector;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwtexpui.progress.client.ProgressBar;
import java.util.List;

/** Loads JavaScript plugins with a progress meter visible. */
public class PluginLoader extends DialogBox {
  private static PluginLoader self;

  public static void load(
      List<String> plugins, int loadTimeout, AsyncCallback<VoidResult> callback) {
    if (plugins == null || plugins.isEmpty()) {
      callback.onSuccess(VoidResult.create());
    } else {
      self = new PluginLoader(loadTimeout, callback);
      self.load(plugins);
      self.startTimers();
      self.center();
    }
  }

  static void loaded() {
    self.loadedOne();
  }

  private final int loadTimeout;
  private final AsyncCallback<VoidResult> callback;
  private ProgressBar progress;
  private Timer show;
  private Timer update;
  private Timer timeout;
  private boolean visible;

  private PluginLoader(int loadTimeout, AsyncCallback<VoidResult> cb) {
    super(/* auto hide */ false, /* modal */ true);
    callback = cb;
    this.loadTimeout = loadTimeout;
    progress = new ProgressBar(Gerrit.C.loadingPlugins());

    setStyleName(Gerrit.RESOURCES.css().errorDialog());
    addStyleName(Gerrit.RESOURCES.css().loadingPluginsDialog());
  }

  private void load(List<String> pluginUrls) {
    for (String url : pluginUrls) {
      Plugin plugin = Plugin.create(url);
      plugins().put(url, plugin);
      ScriptInjector.fromUrl(url)
          .setWindow(ScriptInjector.TOP_WINDOW)
          .setCallback(new LoadCallback(plugin))
          .inject();
    }
  }

  private void startTimers() {
    show =
        new Timer() {
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

    update =
        new Timer() {
          private int cycle;

          @Override
          public void run() {
            progress.setValue(100 * ++cycle * 250 / loadTimeout);
          }
        };
    update.scheduleRepeating(250);

    timeout =
        new Timer() {
          @Override
          public void run() {
            finish();
          }
        };
    timeout.schedule(loadTimeout);
  }

  private void loadedOne() {
    boolean done = true;
    for (Plugin plugin : Natives.asList(plugins().values())) {
      done &= plugin.loaded();
    }
    if (done) {
      finish();
    }
  }

  private void finish() {
    show.cancel();
    update.cancel();
    timeout.cancel();
    self = null;

    if (!hadFailures()) {
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

    callback.onSuccess(VoidResult.create());
  }

  private boolean hadFailures() {
    boolean failed = false;
    for (Plugin plugin : Natives.asList(plugins().values())) {
      if (!plugin.success()) {
        failed = true;

        Exception e = plugin.failure();
        String msg;
        if (e != null && e instanceof CodeDownloadException) {
          msg = Gerrit.M.cannotDownloadPlugin(plugin.url());
        } else {
          msg = Gerrit.M.pluginFailed(plugin.name());
        }
        hide(true);
        new ErrorDialog(msg).center();
      }
    }
    return failed;
  }

  private static native NativeMap<Plugin> plugins() /*-{ return $wnd.Gerrit.plugins }-*/;

  private class LoadCallback implements Callback<Void, Exception> {
    private final Plugin plugin;

    LoadCallback(Plugin plugin) {
      this.plugin = plugin;
    }

    @Override
    public void onSuccess(Void result) {}

    @Override
    public void onFailure(Exception reason) {
      plugin.failure(reason);
      loadedOne();
    }
  }
}
