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

package com.google.gerrit.client.patches;

import com.google.gerrit.common.data.PatchDetailService;
import com.google.gwt.core.client.GWT;
import com.google.gwtjsonrpc.client.JsonUtil;

public class PatchUtil {
  public static final PatchConstants C = GWT.create(PatchConstants.class);
  public static final PatchMessages M = GWT.create(PatchMessages.class);
  public static final PatchDetailService DETAIL_SVC;

  static {
    DETAIL_SVC = GWT.create(PatchDetailService.class);
    JsonUtil.bind(DETAIL_SVC, "rpc/PatchDetailService");
  }
}
