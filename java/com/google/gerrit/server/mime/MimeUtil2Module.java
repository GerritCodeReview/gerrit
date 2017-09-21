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

package com.google.gerrit.server.mime;

import com.google.gerrit.server.util.HostPlatform;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import eu.medsea.mimeutil.MimeUtil2;
import eu.medsea.mimeutil.detector.ExtensionMimeDetector;
import eu.medsea.mimeutil.detector.MagicMimeMimeDetector;

public class MimeUtil2Module extends AbstractModule {
  @Override
  protected void configure() {}

  @Provides
  @Singleton
  MimeUtil2 provideMimeUtil2() {
    MimeUtil2 m = new MimeUtil2();
    m.registerMimeDetector(ExtensionMimeDetector.class.getName());
    m.registerMimeDetector(MagicMimeMimeDetector.class.getName());
    if (HostPlatform.isWin32()) {
      m.registerMimeDetector("eu.medsea.mimeutil.detector.WindowsRegistryMimeDetector");
    }
    m.registerMimeDetector(DefaultFileExtensionRegistry.class.getName());
    return m;
  }
}
