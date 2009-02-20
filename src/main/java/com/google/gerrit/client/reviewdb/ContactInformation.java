// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.reviewdb;

import com.google.gwtorm.client.Column;

/** Non-Internet contact details, such as a postal address and telephone. */
public final class ContactInformation {
  @Column(length = Integer.MAX_VALUE, notNull = false)
  protected String address;

  @Column(notNull = false, length = 40)
  protected String country;

  @Column(notNull = false, length = 30)
  protected String phoneNbr;

  @Column(notNull = false, length = 30)
  protected String faxNbr;

  public ContactInformation() {
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(final String a) {
    address = a;
  }

  public String getCountry() {
    return country;
  }

  public void setCountry(final String c) {
    country = c;
  }

  public String getPhoneNumber() {
    return phoneNbr;
  }

  public void setPhoneNumber(final String p) {
    phoneNbr = p;
  }

  public String getFaxNumber() {
    return faxNbr;
  }

  public void setFaxNumber(final String f) {
    faxNbr = f;
  }

  public static boolean hasData(final ContactInformation contactInformation) {
    if (contactInformation == null) {
      return false;
    }
    return hasData(contactInformation.address)
        || hasData(contactInformation.country)
        || hasData(contactInformation.phoneNbr)
        || hasData(contactInformation.faxNbr);
  }

  public static boolean hasAddress(final ContactInformation contactInformation) {
    return contactInformation != null && hasData(contactInformation.address);
  }

  private static boolean hasData(final String s) {
    return s != null && s.trim().length() > 0;
  }
}
