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

package com.google.gerrit.extensions.events;

import com.google.gerrit.extensions.annotations.ExtensionPoint;

/**
 * Notified whenever the garbage collector has run successfully on a project.
 */
@ExtensionPoint
public interface GarbageCollectorListener {

  public interface Event {
    String getProjectName();

    /**
     * The number of objects stored as loose objects.
     */
    long getNumberOfLooseObjects();

    /**
     * The number of loose refs.
     */
    long getNumberOfLooseRefs();

    /**
     * The number of objects stored in pack files. If the same object is
     * stored in multiple pack files then it is counted as often as it
     * occurs in pack files.
     */
    long getNumberOfPackedObjects();

    /**
     * The number of refs stored in pack files.
     */
    long getNumberOfPackedRefs();

    /**
     * The number of pack files.
     */
    long getNumberOfPackFiles();

    /**
     * The sum of the sizes of all files used to persist loose objects.
     */
    long getSizeOfLooseObjects();

    /**
     * The sum of the sizes of all pack files.
     */
    long getSizeOfPackedObjects();
  }

  void onGarbageCollected(Event event);
}
