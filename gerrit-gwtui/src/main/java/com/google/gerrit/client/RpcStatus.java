// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.client;

import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwtjsonrpc.client.event.RpcCompleteEvent;
import com.google.gwtjsonrpc.client.event.RpcCompleteHandler;
import com.google.gwtjsonrpc.client.event.RpcStartEvent;
import com.google.gwtjsonrpc.client.event.RpcStartHandler;

public class RpcStatus implements RpcStartHandler, RpcCompleteHandler {
  public static RpcStatus INSTANCE;

  private static int hideDepth;

  /** Execute code, hiding the RPCs they execute from being shown visually. */
  public static void hide(Runnable run) {
    try {
      hideDepth++;
      run.run();
    } finally {
      hideDepth--;
    }
  }

  private final Label loading;
  private int activeCalls;

  RpcStatus() {
    loading = new InlineLabel();
    loading.setText(Gerrit.C.rpcStatusWorking());
    loading.setStyleName(Gerrit.RESOURCES.css().rpcStatus());
    loading.setVisible(false);
    RootPanel.get().add(loading);
  }

  @Override
  public void onRpcStart(RpcStartEvent event) {
    onRpcStart();
  }

  public void onRpcStart() {
    if (++activeCalls == 1) {
      if (hideDepth == 0) {
        loading.setVisible(true);
      }
    }
  }

  @Override
  public void onRpcComplete(RpcCompleteEvent event) {
    onRpcComplete();
  }

  public void onRpcComplete() {
    if (--activeCalls == 0) {
      loading.setVisible(false);
    }
  }
}
