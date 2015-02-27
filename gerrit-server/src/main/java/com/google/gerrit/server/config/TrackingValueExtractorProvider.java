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

package com.google.gerrit.server.config;

import com.google.gerrit.common.SiteLibraryLoaderUtil;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.nio.file.Path;

@Singleton
public class TrackingValueExtractorProvider
    implements Provider<TrackingValueExtractor> {
  private final Injector injector;
  private final Path libdir;

  @Inject
  protected TrackingValueExtractorProvider(Injector injector,
      SitePaths sitePaths) {
    this.injector = injector;
    this.libdir = sitePaths.lib_dir;
  }

  @Override
  public TrackingValueExtractor get() {
    return injector.getInstance(getImpl());
  }

  @SuppressWarnings("unchecked")
  private Class<? extends TrackingValueExtractor> getImpl() {
    SiteLibraryLoaderUtil.loadSiteLib(libdir);
    try {
      return (Class<? extends TrackingValueExtractor>)
          Class.forName("com.google.gerrit.CustomTrackingValueExtractor");
    } catch (ClassNotFoundException e) {
      return DefaultTrackingValueExtractor.class;
    }
  }
}
