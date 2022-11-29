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

package com.google.gerrit.server.query.account;

import static com.google.gerrit.server.index.account.AccountField.USERNAME_SPEC;

import com.google.common.base.Ascii;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Project;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.SchemaFieldDefs.SchemaField;
import com.google.gerrit.index.query.IndexPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryBuilder;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.index.account.AccountField;
import com.google.gerrit.server.notedb.ChangeNotes;
import java.util.List;

/** Utility class to create predicates for account index queries. */
public class AccountPredicates {
  public static boolean hasActive(Predicate<AccountState> p) {
    return QueryBuilder.find(p, AccountPredicate.class, AccountField.ACTIVE_FIELD_SPEC.getName())
        != null;
  }

  public static Predicate<AccountState> andActive(Predicate<AccountState> p) {
    return Predicate.and(p, isActive());
  }

  public static Predicate<AccountState> defaultPredicate(
      Schema<AccountState> schema, boolean canSeeSecondaryEmails, String query) {
    // Adapt the capacity of this list when adding more default predicates.
    List<Predicate<AccountState>> preds = Lists.newArrayListWithCapacity(3);
    Integer id = Ints.tryParse(query);
    if (id != null) {
      preds.add(id(schema, Account.id(id)));
    }
    if (canSeeSecondaryEmails) {
      preds.add(equalsNameIncludingSecondaryEmails(query));
    } else {
      if (schema.hasField(AccountField.NAME_PART_NO_SECONDARY_EMAIL_SPEC)) {
        preds.add(equalsName(query));
      } else {
        preds.add(AccountPredicates.fullName(query));
        if (schema.hasField(AccountField.PREFERRED_EMAIL_LOWER_CASE_SPEC)) {
          preds.add(AccountPredicates.preferredEmail(query));
        }
      }
    }
    if (schema.hasField(USERNAME_SPEC)) {
      preds.add(username(query));
    }
    // Adapt the capacity of the "predicates" list when adding more default
    // predicates.
    return Predicate.or(preds);
  }

  public static Predicate<AccountState> id(Schema<AccountState> schema, Account.Id accountId) {
    return new AccountPredicate(
        schema.hasField(AccountField.ID_FIELD_SPEC)
            ? AccountField.ID_FIELD_SPEC
            : AccountField.ID_STR_FIELD_SPEC,
        AccountQueryBuilder.FIELD_ACCOUNT,
        accountId.toString());
  }

  public static Predicate<AccountState> emailIncludingSecondaryEmails(String email) {
    return new AccountPredicate(
        AccountField.EMAIL_SPEC, AccountQueryBuilder.FIELD_EMAIL, Ascii.toLowerCase(email));
  }

  public static Predicate<AccountState> preferredEmail(String email) {
    return new AccountPredicate(
        AccountField.PREFERRED_EMAIL_LOWER_CASE_SPEC,
        AccountQueryBuilder.FIELD_PREFERRED_EMAIL,
        Ascii.toLowerCase(email));
  }

  public static Predicate<AccountState> preferredEmailExact(String email) {
    return new AccountPredicate(
        AccountField.PREFERRED_EMAIL_EXACT_SPEC,
        AccountQueryBuilder.FIELD_PREFERRED_EMAIL_EXACT,
        email);
  }

  public static Predicate<AccountState> equalsNameIncludingSecondaryEmails(String name) {
    return new AccountPredicate(
        AccountField.NAME_PART_SPEC, AccountQueryBuilder.FIELD_NAME, Ascii.toLowerCase(name));
  }

  public static Predicate<AccountState> equalsName(String name) {
    return new AccountPredicate(
        AccountField.NAME_PART_NO_SECONDARY_EMAIL_SPEC,
        AccountQueryBuilder.FIELD_NAME,
        Ascii.toLowerCase(name));
  }

  public static Predicate<AccountState> externalIdIncludingSecondaryEmails(String externalId) {
    return new AccountPredicate(AccountField.EXTERNAL_ID_FIELD_SPEC, externalId);
  }

  public static Predicate<AccountState> fullName(String fullName) {
    return new AccountPredicate(AccountField.FULL_NAME_SPEC, fullName);
  }

  public static Predicate<AccountState> isActive() {
    return new AccountPredicate(AccountField.ACTIVE_FIELD_SPEC, "1");
  }

  public static Predicate<AccountState> isNotActive() {
    return new AccountPredicate(AccountField.ACTIVE_FIELD_SPEC, "0");
  }

  public static Predicate<AccountState> username(String username) {
    return new AccountPredicate(
        USERNAME_SPEC, AccountQueryBuilder.FIELD_USERNAME, Ascii.toLowerCase(username));
  }

  public static Predicate<AccountState> watchedProject(Project.NameKey project) {
    return new AccountPredicate(AccountField.WATCHED_PROJECT_SPEC, project.get());
  }

  public static Predicate<AccountState> cansee(
      AccountQueryBuilder.Arguments args, ChangeNotes changeNotes) {
    return new CanSeeChangePredicate(args.permissionBackend, changeNotes);
  }

  /** Predicate that is mapped to a field in the account index. */
  static class AccountPredicate extends IndexPredicate<AccountState> {
    AccountPredicate(SchemaField<AccountState, ?> def, String value) {
      super(def, value);
    }

    AccountPredicate(SchemaField<AccountState, ?> def, String name, String value) {
      super(def, name, value);
    }
  }

  private AccountPredicates() {}
}
