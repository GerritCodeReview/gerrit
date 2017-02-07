// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.extensions.api.accounts;

import com.google.gerrit.extensions.restapi.DefaultInput;

/** This entity contains information for registering a new email address. */
public class EmailInput {
  /* The email address. If provided, must match the email address from the URL. */
  @DefaultInput public String email;

  /* Whether the new email address should become the preferred email address of
   * the user. Only supported if {@link #noConfirmation} is set or if the
   * authentication type is DEVELOPMENT_BECOME_ANY_ACCOUNT.*/
  public boolean preferred;

  /* Whether the email address should be added without confirmation. In this
   * case no verification email is sent to the user. Only Gerrit administrators
   * are allowed to add email addresses without confirmation. */
  public boolean noConfirmation;
}
