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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gwt.event.dom.client.ErrorEvent;
import com.google.gwt.event.dom.client.ErrorHandler;
import com.google.gwt.user.client.ui.Image;

public class AvatarImage extends Image {

  /**
   * An avatar image for the given account using a default size decided by the
   * avatar provider
   */
  public AvatarImage(Account account) {
    this(account, 0);
  }

  /**
   * An avatar image for the given account using the requested size.
   *
   * @param account The account in which we are interested
   * @param size A requested size. Note that the size can be ignored depending
   *        on the avatar provider. A size <= 0 indicates to let the provider
   *        decide a default size.
   */
  public AvatarImage(Account account, int size) {
    super("/avatar/" + account.getId() + (size > 0 ? ("?size=" + size) : ""));

    addErrorHandler(new ErrorHandler() {
      @Override
      public void onError(ErrorEvent event) {
        // We got a 404, don't bother showing the image. Either the user doesn't
        // have an avatar or there is no avatar provider plugin installed.
        setVisible(false);
      }
    });
  }
}
