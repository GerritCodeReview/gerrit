// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.rpc;

import com.google.gerrit.client.ErrorDialog;
import com.google.gerrit.client.Gerrit;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.RemoteJsonException;
import com.google.gwtjsonrpc.client.ServerUnavailableException;

/** Abstract callback handling generic error conditions automatically */
public abstract class GerritCallback<T> implements AsyncCallback<T> {
  public void onFailure(final Throwable caught) {
    if (isNotSignedIn(caught)) {
      new ErrorDialog(RpcUtil.C.errorNotSignedIn()).center();

    } else if (isNoSuchEntity(caught)) {
      new ErrorDialog(Gerrit.C.notFoundBody()).center();

    } else if (caught instanceof ServerUnavailableException) {
      new ErrorDialog(RpcUtil.C.errorServerUnavailable()).center();

    } else {
      GWT.log(getClass().getName() + " caught " + caught, caught);
      new ErrorDialog(caught).center();
    }
  }

  public static boolean isNotSignedIn(final Throwable caught) {
    if (caught instanceof NotSignedInException) {
      return true;
    }
    return caught instanceof RemoteJsonException
        && caught.getMessage().equals(NotSignedInException.MESSAGE);
  }

  public static boolean isNoSuchEntity(final Throwable caught) {
    if (caught instanceof NoSuchEntityException) {
      return true;
    }
    return caught instanceof RemoteJsonException
        && caught.getMessage().equals(NoSuchEntityException.MESSAGE);
  }
}
