// Copyright (C) 2016 The Android Open Source Project
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

/**
 * Representation of a contributor agreement in the REST API.
 *
 * <p>This class determines the JSON format of a contributor agreement in the REST API.
 */
public class AgreementInfo {
  /** The unique name of the contributor agreement. */
  public String name;

  /** The description of the contributor agreement. */
  public String description;

  /** The URL of the contributor agreement. */
  public String url;

  /**
   * Group to which a user that signs the contributor agreement online is added automatically.
   *
   * <p>May be {@code null}. In this case users cannot sign the contributor agreement online.
   */
  public GroupInfo autoVerifyGroup;
}
