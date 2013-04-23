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

package com.google.gerrit.client;

import com.google.gerrit.client.rpc.RestApi;
import com.google.gwt.event.dom.client.ErrorEvent;
import com.google.gwt.event.dom.client.ErrorHandler;
import com.google.gwt.user.client.ui.Image;

public class AvatarImage extends Image {

  /** A default sized avatar image. */
  public AvatarImage(String email) {
    this(email, 0);
  }

  /**
   * An avatar image for the given account using the requested size.
   *
   * @param email The email address of the account in which we are interested
   * @param size A requested size. Note that the size can be ignored depending
   *        on the avatar provider. A size <= 0 indicates to let the provider
   *        decide a default size.
   */
  public AvatarImage(String email, int size) {
    super(url(email, size));

    if (size > 0) {
      // If the provider does not resize the image, force it in the browser.
      setSize(size + "px", size + "px");
    }

    addErrorHandler(new ErrorHandler() {
      @Override
      public void onError(ErrorEvent event) {
        // We got a 404, don't bother showing the image. Either the user doesn't
        // have an avatar or there is no avatar provider plugin installed.
        setVisible(false);
      }
    });
  }

  private static String url(String email, int size) {
    if (email == null) {
      return "";
    }
    String u;
    if (Gerrit.isSignedIn() && email.equals(Gerrit.getUserAccount().getPreferredEmail())) {
      u = "self";
    } else {
      u = email;
    }
    RestApi api = new RestApi("/accounts/").id(u).view("avatar");
    if (size > 0) {
      api.addParameter("s", size);
    }
    return api.url();
  }
}
