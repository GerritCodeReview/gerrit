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
// limitations under the License.package com.google.gerrit.server.git;

package com.google.gerrit.server.git;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

import java.util.HashMap;
import java.util.List;


/**
 *
 * Interface for caching tags in a git repository. The data returned from a
 * cache lookup is a hashmap where each key is a tag with an array as the
 * value that is a list of refs from which the tag can be reached.
 *
 */

public interface GitTagCache {

 /**
  * evicts the cached data for a repository from the cache, forcing a
  * subsequent cache get to read the data from the actual git repository
  * again.
  *
  * @param gitRepo the repository whose cache data should be evicted.
  */
  public void evict(Repository gitRepo);

 /**
  * Returns the data structure described above for a given git repository.
  * Since the cache is self populating this call will also create the entry
  * if it does not exist in the cache.
  *
  * @param gitRepo the repository for which the tag data should be returned.
  * @return the gitRepo tag data in the format described above.
  */
  public HashMap<Ref, List<Ref>> get(Repository gitRepo);
}
