// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.common.changes;

import org.eclipse.jgit.revwalk.FooterKey;

public class FooterConstants {
  public static final FooterKey CHANGE_ID = new FooterKey("Change-Id");
  public static final FooterKey REVIEWED_ON = new FooterKey("Reviewed-on");
  public static final FooterKey TESTED_BY = new FooterKey("Tested-by");
  public static final FooterKey REVIEWED_BY = new FooterKey("Reviewed-By");
}