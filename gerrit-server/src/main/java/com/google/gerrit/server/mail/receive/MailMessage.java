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

package com.google.gerrit.server.mail.receive;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

import org.joda.time.DateTime;

/**
 * MailMessage is a simplified representation of an RFC 2045-2047 mime email
 * message used for representing received emails inside Gerrit. It is populated
 * by the MailParser after MailReceiver has received a message. Transformations
 * done by the parser include stitching mime parts together, transforming all
 * content to UTF-16 and removing attachments.
 */
@AutoValue
public abstract class MailMessage {
  // Unique Identifier
  public abstract String id();
  // Envelope Information
  public abstract String from();
  public abstract ImmutableList<String> to();
  public abstract ImmutableList<String> cc();
  // Metadata
  public abstract DateTime dateReceived();
  public abstract ImmutableList<String> additionalHeaders();
  // Content
  public abstract String subject();
  public abstract String textContent();
  public abstract String htmlContent();

  static Builder builder() {
    return new AutoValue_MailMessage.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder id(String val);
    abstract Builder from(String val);
    abstract ImmutableList.Builder<String> toBuilder();

    public Builder addTo(String val) {
      toBuilder().add(val);
      return this;
    }

    abstract ImmutableList.Builder<String> ccBuilder();

    public Builder addCc(String val) {
      ccBuilder().add(val);
      return this;
    }

    abstract Builder dateReceived(DateTime val);
    abstract ImmutableList.Builder<String> additionalHeadersBuilder();

    public Builder addAdditionalHeader(String val) {
      additionalHeadersBuilder().add(val);
      return this;
    }

    abstract Builder subject(String val);
    abstract Builder textContent(String val);
    abstract Builder htmlContent(String val);

    abstract MailMessage build();
  }
}
