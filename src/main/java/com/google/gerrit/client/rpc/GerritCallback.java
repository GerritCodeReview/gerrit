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

package com.google.gerrit.client.rpc;

import com.google.gerrit.client.ErrorDialog;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.NotSignedInDialog;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.rpc.InvocationException;
import com.google.gwtjsonrpc.client.JsonUtil;
import com.google.gwtjsonrpc.client.RemoteJsonException;
import com.google.gwtjsonrpc.client.ServerUnavailableException;

/** Abstract callback handling generic error conditions automatically */
public abstract class GerritCallback<T> implements AsyncCallback<T> {
  public void onFailure(final Throwable caught) {
    if (isNotSignedIn(caught) || isInvalidXSRF(caught)) {
      new NotSignedInDialog().center();

    } else if (isNoSuchEntity(caught)) {
      new ErrorDialog(Gerrit.C.notFoundBody()).center();

    } else if (isNoSuchAccount(caught)) {
      final String msg = caught.getMessage();
      final String who = msg.substring(NoSuchAccountException.MESSAGE.length());
      final ErrorDialog d = new ErrorDialog(Gerrit.M.noSuchAccountMessage(who));
      d.setText(Gerrit.C.noSuchAccountTitle());
      d.center();

    } else if (isNameAlreadyUsed(caught)) {
      new ErrorDialog(Gerrit.C.nameAlreadyUsedBody()).center();

    } else if (caught instanceof ServerUnavailableException) {
      new ErrorDialog(RpcConstants.C.errorServerUnavailable()).center();

    } else {
      GWT.log(getClass().getName() + " caught " + caught, caught);
      new ErrorDialog(caught).center();
    }
  }

  private static boolean isInvalidXSRF(final Throwable caught) {
    return caught instanceof InvocationException
        && caught.getMessage().equals(JsonUtil.ERROR_INVALID_XSRF);
  }

  private static boolean isNotSignedIn(final Throwable caught) {
    return caught instanceof RemoteJsonException
        && caught.getMessage().equals(NotSignedInException.MESSAGE);
  }

  protected static boolean isNoSuchEntity(final Throwable caught) {
    return caught instanceof RemoteJsonException
        && caught.getMessage().equals(NoSuchEntityException.MESSAGE);
  }

  private static boolean isNoSuchAccount(final Throwable caught) {
    return caught instanceof RemoteJsonException
        && caught.getMessage().startsWith(NoSuchAccountException.MESSAGE);
  }

  private static boolean isNameAlreadyUsed(final Throwable caught) {
    return caught instanceof RemoteJsonException
        && caught.getMessage().equals(NameAlreadyUsedException.MESSAGE);
  }
}
