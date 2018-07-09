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

package com.google.gerrit.mail;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import java.time.Instant;

/**
 * A simplified representation of an RFC 2045-2047 mime email message used for representing received
 * emails inside Gerrit. It is populated by the MailParser after MailReceiver has received a
 * message. Transformations done by the parser include stitching mime parts together, transforming
 * all content to UTF-16 and removing attachments.
 *
 * <p>A valid {@link MailMessage} contains at least the following fields: id, from, to, subject and
 * dateReceived.
 */
@AutoValue
public abstract class MailMessage {
  // Unique Identifier
  public abstract String id();
  // Envelop Information
  public abstract Address from();

  public abstract ImmutableList<Address> to();

  public abstract ImmutableList<Address> cc();
  // Metadata
  public abstract Instant dateReceived();

  public abstract ImmutableList<String> additionalHeaders();
  // Content
  public abstract String subject();

  @Nullable
  public abstract String textContent();

  @Nullable
  public abstract String htmlContent();
  // Raw content as received over the wire
  @Nullable
  public abstract ImmutableList<Integer> rawContent();

  @Nullable
  public abstract String rawContentUTF();

  public static Builder builder() {
    return new AutoValue_MailMessage.Builder();
  }

  public abstract Builder toBuilder();

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder id(String val);

    public abstract Builder from(Address val);

    public abstract ImmutableList.Builder<Address> toBuilder();

    public Builder addTo(Address val) {
      toBuilder().add(val);
      return this;
    }

    public abstract ImmutableList.Builder<Address> ccBuilder();

    public Builder addCc(Address val) {
      ccBuilder().add(val);
      return this;
    }

    public abstract Builder dateReceived(Instant instant);

    public abstract ImmutableList.Builder<String> additionalHeadersBuilder();

    public Builder addAdditionalHeader(String val) {
      additionalHeadersBuilder().add(val);
      return this;
    }

    public abstract Builder subject(String val);

    public abstract Builder textContent(String val);

    public abstract Builder htmlContent(String val);

    public abstract Builder rawContent(ImmutableList<Integer> val);

    public abstract Builder rawContentUTF(String val);

    public abstract MailMessage build();
  }
}
