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

package com.google.gerrit.server.cache.serialize.entities;

/**
 * Persisted caches which define a maximum weight also need to define a weigher, which is used to
 * specify the size of the cache key and value in bytes. Entity classes which need to provide a
 * weight should implement this interface.
 */
public interface Weighable {

  /** The size of the entity in bytes. */
  int weight();
}
