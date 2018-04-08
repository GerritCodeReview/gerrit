// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.account;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AcceptanceTestRequestScope.Context;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.config.AnonymousCowardName;
import com.google.gerrit.config.CanonicalWebUrl;
import com.google.gerrit.config.DisableReverseDnsLookup;
import com.google.gerrit.extensions.api.accounts.EmailApi;
import com.google.gerrit.extensions.api.accounts.EmailInput;
import com.google.gerrit.extensions.common.EmailInfo;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.DefaultRealm;
import com.google.gerrit.server.account.EmailExpander;
import com.google.gerrit.server.account.Emails;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gson.reflect.TypeToken;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.Test;

public class EmailIT extends AbstractDaemonTest {
  @Inject private @ServerInitiated Provider<AccountsUpdate> accountsUpdateProvider;
  @Inject private ExternalIds externalIds;
  @Inject private SchemaFactory<ReviewDb> reviewDbProvider;
  @Inject private AuthConfig authConfig;
  @Inject private @AnonymousCowardName String anonymousCowardName;
  @Inject private @CanonicalWebUrl Provider<String> canonicalUrl;
  @Inject private @DisableReverseDnsLookup Boolean disableReverseDnsLookup;
  @Inject private EmailExpander emailExpander;
  @Inject private Provider<Emails> emails;

  @Test
  public void addEmail() throws Exception {
    String email = "foo.bar@example.com";
    assertThat(getEmails()).doesNotContain(email);

    createEmail(email);
    assertThat(getEmails()).contains(email);
  }

  @Test
  public void addUrlEncodedEmail() throws Exception {
    String email = "foo.bar2@example.com";
    assertThat(getEmails()).doesNotContain(email);

    createEmail(email.replace("@", "%40"));
    assertThat(getEmails()).contains(email);
  }

  @Test
  public void addEmailWithLeadingAndTrailingWhitespace() throws Exception {
    String email = "foo.bar3@example.com";
    assertThat(getEmails()).doesNotContain(email);

    createEmail(IdString.fromDecoded(" " + email + " ").encoded());
    assertThat(getEmails()).contains(email);
  }

  @Test
  public void deleteEmail() throws Exception {
    String email = "foo.baz@example.com";
    assertThat(getEmails()).doesNotContain(email);

    createEmail(email);
    assertThat(getEmails()).contains(email);

    RestResponse r = adminRestSession.delete("/accounts/self/emails/" + email);
    r.assertNoContent();
    assertThat(getEmails()).doesNotContain(email);
  }

  @Test
  public void deleteUrlEncodedEmail() throws Exception {
    String email = "foo.baz2@example.com";
    assertThat(getEmails()).doesNotContain(email);

    createEmail(email);
    assertThat(getEmails()).contains(email);

    RestResponse r = adminRestSession.delete("/accounts/self/emails/" + email.replace("@", "%40"));
    r.assertNoContent();
    assertThat(getEmails()).doesNotContain(email);
  }

  @Test
  public void setPreferredEmailToEmailOfMailToExternalId() throws Exception {
    String email = "foo@example.com";
    createEmail(email);
    assertThat(gApi.accounts().self().get().email).isNotEqualTo(email);

    resetCurrentApiUser();
    gApi.accounts().self().email(email).setPreferred();
    assertThat(gApi.accounts().self().get().email).isEqualTo(email);
  }

  @Test
  public void setPreferredEmailToEmailOfExternalExternalId() throws Exception {
    String email = "foo@example.com";
    accountsUpdateProvider
        .get()
        .update(
            "Add External ID",
            admin.id,
            u ->
                u.addExternalId(
                    ExternalId.createWithEmail(
                        ExternalId.SCHEME_EXTERNAL, "foo", admin.id, email)));
    assertThat(gApi.accounts().self().get().email).isNotEqualTo(email);

    resetCurrentApiUser();
    gApi.accounts().self().email(email).setPreferred();
    assertThat(gApi.accounts().self().get().email).isEqualTo(email);
  }

  @Test
  public void setPreferredEmailToNonExistingEmail() throws Exception {
    String email = "non-existing@example.com";
    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage("Not found: " + email);
    gApi.accounts().self().email(email).setPreferred();
  }

  @Test
  public void setPreferredEmailToEmailOfOtherAccount() throws Exception {
    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage("Not found: " + user.email);
    gApi.accounts().self().email(user.email).setPreferred();
  }

  @Test
  public void setPreferredEmailWithOtherCase() throws Exception {
    String email = "foo@example.com";
    createEmail(email);
    assertThat(gApi.accounts().self().get().email).isNotEqualTo(email);

    resetCurrentApiUser();
    String emailOtherCase = email.toUpperCase();
    gApi.accounts().self().email(emailOtherCase).setPreferred();
    assertThat(gApi.accounts().self().get().email).isEqualTo(email);
  }

  @Test
  public void setPreferredEmailToEmailFromCustomRealmThatDoesntExistAsExternalId()
      throws Exception {
    String email = "foo@example.com";
    ExternalId.Key mailtoExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_MAILTO, email);
    assertThat(externalIds.get(mailtoExtIdKey)).isEmpty();
    assertThat(gApi.accounts().self().get().email).isNotEqualTo(email);

    Context oldCtx = createContextWithCustomRealm(new RealmWithAdditionalEmails(admin.id, email));
    try {
      gApi.accounts().self().email(email).setPreferred();
      Optional<ExternalId> mailtoExtId = externalIds.get(mailtoExtIdKey);
      assertThat(mailtoExtId).isPresent();
      assertThat(mailtoExtId.get().accountId()).isEqualTo(admin.id);
      assertThat(gApi.accounts().self().get().email).isEqualTo(email);
    } finally {
      atrScope.set(oldCtx);
    }
  }

  @Test
  public void setPreferredEmailToEmailFromCustomRealmThatBelongsToOtherAccount() throws Exception {
    ExternalId mailToExtId = ExternalId.createEmail(user.id, user.email);
    assertThat(externalIds.get(mailToExtId.key())).isPresent();

    Context oldCtx =
        createContextWithCustomRealm(new RealmWithAdditionalEmails(admin.id, user.email));
    try {
      exception.expect(ResourceConflictException.class);
      exception.expectMessage("email in use by another account");
      gApi.accounts().self().email(user.email).setPreferred();
    } finally {
      atrScope.set(oldCtx);
    }
  }

  @Test
  public void emailApi() throws Exception {
    String email = "foo@example.com";
    assertThat(getEmails()).doesNotContain(email);

    // Create email
    EmailInput emailInput = new EmailInput();
    emailInput.email = email;
    emailInput.noConfirmation = true;
    gApi.accounts().self().createEmail(emailInput);
    assertThat(getEmails()).contains(email);
    assertThat(gApi.accounts().self().get().email).isNotEqualTo(email);

    // Get email
    resetCurrentApiUser();
    EmailApi emailApi = gApi.accounts().self().email(email);
    EmailInfo emailInfo = emailApi.get();
    assertThat(emailInfo.email).isEqualTo(email);
    assertThat(emailInfo.preferred).isNull();
    assertThat(emailInfo.pendingConfirmation).isNull();

    // Set as preferred email
    emailApi.setPreferred();
    assertThat(gApi.accounts().self().get().email).isEqualTo(email);

    // Get email again (now it's the preferred email)
    resetCurrentApiUser();
    emailApi = gApi.accounts().self().email(email);
    emailInfo = emailApi.get();
    assertThat(emailInfo.email).isEqualTo(email);
    assertThat(emailInfo.preferred).isTrue();
    assertThat(emailInfo.pendingConfirmation).isNull();

    // Delete email
    emailApi.delete();
    assertThat(getEmails()).doesNotContain(email);

    // Now the email is no longer found
    resetCurrentApiUser();
    emailApi = gApi.accounts().self().email(email);
    exception.expect(ResourceNotFoundException.class);
    emailApi.get();
  }

  private Set<String> getEmails() throws Exception {
    RestResponse r = adminRestSession.get("/accounts/self/emails");
    r.assertOK();
    List<EmailInfo> emails =
        newGson().fromJson(r.getReader(), new TypeToken<List<EmailInfo>>() {}.getType());
    return emails.stream().map(e -> e.email).collect(toSet());
  }

  private void createEmail(String email) throws Exception {
    EmailInput input = new EmailInput();
    input.noConfirmation = true;
    RestResponse r = adminRestSession.put("/accounts/self/emails/" + email, input);
    r.assertCreated();
  }

  private Context createContextWithCustomRealm(Realm realm) {
    IdentifiedUser.GenericFactory userFactory =
        new IdentifiedUser.GenericFactory(
            authConfig,
            realm,
            anonymousCowardName,
            canonicalUrl,
            disableReverseDnsLookup,
            accountCache,
            groupBackend);
    return atrScope.set(atrScope.newContext(reviewDbProvider, null, userFactory.create(admin.id)));
  }

  private class RealmWithAdditionalEmails extends DefaultRealm {
    private final Multimap<Account.Id, String> additionalEmails;

    public RealmWithAdditionalEmails(Account.Id accountId, String email) {
      this(ImmutableMultimap.of(accountId, email));
    }

    public RealmWithAdditionalEmails(Multimap<Account.Id, String> additionalEmails) {
      super(emailExpander, emails, authConfig);
      this.additionalEmails = additionalEmails;
    }

    @Override
    public boolean hasEmailAddress(IdentifiedUser user, String email) {
      if (additionalEmails.containsEntry(user.getAccountId(), email)) {
        return true;
      }
      return super.hasEmailAddress(user, email);
    }
  }
}
