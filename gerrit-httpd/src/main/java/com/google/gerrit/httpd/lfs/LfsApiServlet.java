// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.httpd.lfs;

import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lfs.lib.LargeFileRepository;
import org.eclipse.jgit.lfs.server.LfsProtocolServlet;

@Singleton
public class LfsApiServlet extends LfsProtocolServlet {
  private static final long serialVersionUID = 1L;

  public static final String URL_REGEX =
      "^(?:/a)?(?:/p/|/)(.*/(?:info/lfs/objects/batch))$";

  private final DynamicItem<LargeFileRepository> largeFileRepository;

  @Inject
  LfsApiServlet(DynamicItem<LargeFileRepository> largeFileRepository) {
    this.largeFileRepository = largeFileRepository;
  }

  @Override
  protected LargeFileRepository getLargeFileRepository() {
    return largeFileRepository.get();
  }
}
