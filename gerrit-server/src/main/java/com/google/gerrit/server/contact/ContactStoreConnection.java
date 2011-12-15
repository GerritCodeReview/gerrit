// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.server.contact;

import java.io.IOException;
import java.net.URL;

/** Single connection to a {@link ContactStore}. */
public interface ContactStoreConnection {
  public static interface Factory {
    /**
     * Open a new connection to a {@link ContactStore}.
     *
     * @param url contact store URL.
     * @return a new connection to the store.
     *
     * @throws IOException the URL couldn't be opened.
     */
    ContactStoreConnection open(URL url) throws IOException;
  }

  /**
   * Store a blob of contact data in the store.
   *
   * @param body protocol-specific body data.
   *
   * @throws IOException an error occurred storing the contact data.
   */
  public void store(byte[] body) throws IOException;
}
