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

package com.google.gerrit.extensions.common;

import com.google.gerrit.extensions.client.SubmitType;
import java.util.List;

public class MergeableInfo {
  public SubmitType submitType;
  public String strategy;
  public boolean mergeable;
  public boolean commitMerged;
  public boolean contentMerged;
  public List<String> conflicts;
  public List<String> mergeableInto;
}
