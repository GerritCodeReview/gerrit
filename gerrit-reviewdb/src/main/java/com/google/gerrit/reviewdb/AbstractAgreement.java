// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.reviewdb;

import java.sql.Timestamp;
import java.util.Comparator;

/** Base for {@link AccountAgreement} or {@link AccountGroupAgreement}. */
public interface AbstractAgreement {
  public static final Comparator<AbstractAgreement> SORT =
      new Comparator<AbstractAgreement>() {
        @Override
        public int compare(AbstractAgreement a, AbstractAgreement b) {
          return b.getAcceptedOn().compareTo(a.getAcceptedOn());
        }
      };

  public static enum Status {
    NEW('n'),

    VERIFIED('V'),

    REJECTED('R');

    private final char code;

    private Status(final char c) {
      code = c;
    }

    public char getCode() {
      return code;
    }

    public static Status forCode(final char c) {
      for (final Status s : Status.values()) {
        if (s.code == c) {
          return s;
        }
      }
      return null;
    }
  }

  public ContributorAgreement.Id getAgreementId();

  public Timestamp getAcceptedOn();

  public Status getStatus();

  public Timestamp getReviewedOn();

  public Account.Id getReviewedBy();

  public String getReviewComments();
}
