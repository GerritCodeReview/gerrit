// Copyright (C) 2008 The Android Open Source Project
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

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.IntKey;

/**
 * An agreement {@link Account} must acknowledge to contribute changes.
 *
 * @see AccountAgreement
 */
public final class ContributorAgreement {
  public static class Id extends IntKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1, name = "cla_id")
    protected int id;

    protected Id() {
    }

    public Id(final int id) {
      this.id = id;
    }

    @Override
    public int get() {
      return id;
    }

    @Override
    protected void set(int newValue) {
      id = newValue;
    }
  }

  @Column(id = 1)
  protected Id id;

  /** Is this an active agreement contributors can use. */
  @Column(id = 2)
  protected boolean active;

  /** Does this agreement require the {@link Account} to have contact details? */
  @Column(id = 3)
  protected boolean requireContactInformation;

  /** Does this agreement automatically verify new accounts? */
  @Column(id = 4)
  protected boolean autoVerify;

  /** A short name for the agreement. */
  @Column(id = 5, length = 40)
  protected String shortName;

  /** A short one-line description text to appear next to the name. */
  @Column(id = 6, notNull = false)
  protected String shortDescription;

  /** Web address of the agreement documentation. */
  @Column(id = 7)
  protected String agreementUrl;

  protected ContributorAgreement() {
  }

  /**
   * Create a new agreement.
   *
   * @param newId unique id, see {@link ReviewDb#nextAccountId()}.
   * @param name a short title/name for the agreement.
   */
  public ContributorAgreement(final ContributorAgreement.Id newId,
      final String name) {
    id = newId;
    shortName = name;
  }

  public ContributorAgreement.Id getId() {
    return id;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(final boolean a) {
    active = a;
  }

  public boolean isAutoVerify() {
    return autoVerify;
  }

  public void setAutoVerify(final boolean g) {
    autoVerify = g;
  }

  public boolean isRequireContactInformation() {
    return requireContactInformation;
  }

  public void setRequireContactInformation(final boolean r) {
    requireContactInformation = r;
  }

  public String getShortName() {
    return shortName;
  }

  public String getShortDescription() {
    return shortDescription;
  }

  public void setShortDescription(final String d) {
    shortDescription = d;
  }

  public String getAgreementUrl() {
    return agreementUrl;
  }

  public void setAgreementUrl(final String h) {
    agreementUrl = h;
  }
}
