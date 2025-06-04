// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.restapi.account.emails;

import static com.google.gerrit.server.account.AccountResource.ACCOUNT_KIND;
import static com.google.gerrit.server.account.AccountResource.EMAIL_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;

/** Guice module that binds all REST endpoints for {@code /accounts/<account-id>/emails}. */
public class AccountEmailsRestApiModule extends RestApiModule {
  @Override
  protected void configure() {
    DynamicMap.mapOf(binder(), EMAIL_KIND);

    /** List account emails {@code GET /accounts/<account-id>/emails}. */
    child(ACCOUNT_KIND, "emails").to(EmailsCollection.class);

    /** Register account email {@code PUT /accounts/<account-id>/emails/<email-id>}. */
    create(EMAIL_KIND).to(CreateEmail.class);
    /** Delete account email {@code DELETE /accounts/<account-id>/emails/<email-id>}. */
    delete(EMAIL_KIND).to(DeleteEmail.class);
    /** Get account email {@code GET /accounts/<account-id>/emails/<email-id>}. */
    get(EMAIL_KIND).to(GetEmail.class);
    /** Update existing account email {@code PUT /accounts/<account-id>/emails/<email-id>}. */
    put(EMAIL_KIND).to(PutEmail.class);

    /**
     * Set preferred account email {@code DELETE
     * /accounts/<account-id>/emails/<email-id>/preferred}.
     */
    put(EMAIL_KIND, "preferred").to(PutPreferred.class);
  }
}
