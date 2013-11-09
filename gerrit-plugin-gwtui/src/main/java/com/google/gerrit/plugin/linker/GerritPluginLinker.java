// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.plugin.linker;

import com.google.gwt.core.ext.LinkerContext;
import com.google.gwt.core.linker.CrossSiteIframeLinker;

/** Finalizes the module manifest file with the selection script. */
public final class GerritPluginLinker extends CrossSiteIframeLinker {
  @Override
  public String getDescription() {
    return "Gerrit GWT UI plugin";
  }

  @Override
  protected String getJsComputeUrlForResource(LinkerContext context) {
    return "com/google/gerrit/linker/computeUrlForPluginResource.js";
  }
}
