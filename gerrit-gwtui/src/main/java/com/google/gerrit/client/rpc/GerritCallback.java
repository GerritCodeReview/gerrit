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
import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.common.errors.NoSuchAccountException;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.common.errors.NotSignedInException;
import com.google.gwt.user.client.rpc.InvocationException;
import com.google.gwtjsonrpc.client.RemoteJsonException;
import com.google.gwtjsonrpc.client.ServerUnavailableException;
import com.google.gwtjsonrpc.common.JsonConstants;

/** Abstract callback handling generic error conditions automatically */
public abstract class GerritCallback<T>
    implements com.google.gwtjsonrpc.common.AsyncCallback<T>,
        com.google.gwt.user.client.rpc.AsyncCallback<T> {
  @Override
  public void onFailure(Throwable caught) {
    showFailure(caught);
  }

  public static void showFailure(Throwable caught) {
    if (isSigninFailure(caught)) {
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
      final String msg = caught.getMessage();
      final String alreadyUsedName = msg.substring(NameAlreadyUsedException.MESSAGE.length());
      new ErrorDialog(Gerrit.M.nameAlreadyUsedBody(alreadyUsedName)).center();

    } else if (isNoSuchGroup(caught)) {
      final String msg = caught.getMessage();
      final String group = msg.substring(NoSuchGroupException.MESSAGE.length());
      final ErrorDialog d = new ErrorDialog(Gerrit.M.noSuchGroupMessage(group));
      d.setText(Gerrit.C.noSuchGroupTitle());
      d.center();

    } else if (caught instanceof ServerUnavailableException) {
      new ErrorDialog(RpcConstants.C.errorServerUnavailable()).center();

    } else {
      new ErrorDialog(caught).center();
    }
  }

  public static boolean isSigninFailure(Throwable caught) {
    if (isNotSignedIn(caught)
        || isInvalidXSRF(caught)
        || (isNoSuchEntity(caught) && !Gerrit.isSignedIn())) {
      return true;
    }
    return false;
  }

  protected static boolean isInvalidXSRF(Throwable caught) {
    return caught instanceof InvocationException
        && caught.getMessage().equals(JsonConstants.ERROR_INVALID_XSRF);
  }

  protected static boolean isNotSignedIn(Throwable caught) {
    return RestApi.isNotSignedIn(caught)
        || (caught instanceof RemoteJsonException
            && caught.getMessage().equals(NotSignedInException.MESSAGE));
  }

  protected static boolean isNoSuchEntity(Throwable caught) {
    return RestApi.isNotFound(caught)
        || (caught instanceof RemoteJsonException
            && caught.getMessage().equals(NoSuchEntityException.MESSAGE));
  }

  protected static boolean isNoSuchAccount(Throwable caught) {
    return caught instanceof RemoteJsonException
        && caught.getMessage().startsWith(NoSuchAccountException.MESSAGE);
  }

  protected static boolean isNameAlreadyUsed(Throwable caught) {
    return caught instanceof RemoteJsonException
        && caught.getMessage().startsWith(NameAlreadyUsedException.MESSAGE);
  }

  protected static boolean isNoSuchGroup(Throwable caught) {
    return caught instanceof RemoteJsonException
        && caught.getMessage().startsWith(NoSuchGroupException.MESSAGE);
  }
}
