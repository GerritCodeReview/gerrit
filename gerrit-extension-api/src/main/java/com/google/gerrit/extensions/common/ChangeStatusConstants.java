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

public class ChangeStatusConstants {

  /** Minimum database status constant for an open change. */
  static final char MIN_OPEN = 'a';
  /** Database constant for {@link ChangeStatus#NEW}. */
  public static final char STATUS_NEW = 'n';
  /** Database constant for {@link ChangeStatus#SUBMITTED}. */
  public static final char STATUS_SUBMITTED = 's';
  /** Database constant for {@link ChangeStatus#DRAFT}. */
  public static final char STATUS_DRAFT = 'd';
  /** Maximum database status constant for an open change. */
  static final char MAX_OPEN = 'z';

  /** Database constant for {@link ChangeStatus#MERGED}. */
  public static final char STATUS_MERGED = 'M';
}
