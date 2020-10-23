//  Copyright (C) 2020 The Android Open Source Project
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

package com.google.gerrit.server.patch.diff;

import com.google.common.cache.Weigher;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.server.patch.gitdiff.ModifiedFile;

public class ModifiedFilesWeigher
    implements Weigher<ModifiedFilesCacheKey, ImmutableList<ModifiedFile>> {
  @Override
  public int weigh(ModifiedFilesCacheKey key, ImmutableList<ModifiedFile> modifiedFiles) {
    int weight = key.weight();
    for (ModifiedFile modifiedFile : modifiedFiles) {
      weight += modifiedFile.weight();
    }
    return weight;
  }
}
