// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.mail;

import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.server.mail.send.AbandonedSender;
import com.google.gerrit.server.mail.send.AddKeySender;
import com.google.gerrit.server.mail.send.AddReviewerSender;
import com.google.gerrit.server.mail.send.AddToAttentionSetSender;
import com.google.gerrit.server.mail.send.CommentSender;
import com.google.gerrit.server.mail.send.CreateChangeSender;
import com.google.gerrit.server.mail.send.DeleteKeySender;
import com.google.gerrit.server.mail.send.DeleteReviewerSender;
import com.google.gerrit.server.mail.send.DeleteVoteSender;
import com.google.gerrit.server.mail.send.HttpPasswordUpdateSender;
import com.google.gerrit.server.mail.send.MergedSender;
import com.google.gerrit.server.mail.send.RegisterNewEmailSender;
import com.google.gerrit.server.mail.send.RemoveFromAttentionSetSender;
import com.google.gerrit.server.mail.send.ReplacePatchSetSender;
import com.google.gerrit.server.mail.send.RestoredSender;
import com.google.gerrit.server.mail.send.RevertedSender;
import com.google.gerrit.server.mail.send.SetAssigneeSender;

public class EmailModule extends FactoryModule {
  @Override
  protected void configure() {
    factory(AbandonedSender.Factory.class);
    factory(AddKeySender.Factory.class);
    factory(AddReviewerSender.Factory.class);
    factory(CommentSender.Factory.class);
    factory(CreateChangeSender.Factory.class);
    factory(DeleteKeySender.Factory.class);
    factory(DeleteReviewerSender.Factory.class);
    factory(DeleteVoteSender.Factory.class);
    factory(HttpPasswordUpdateSender.Factory.class);
    factory(MergedSender.Factory.class);
    factory(RegisterNewEmailSender.Factory.class);
    factory(ReplacePatchSetSender.Factory.class);
    factory(RestoredSender.Factory.class);
    factory(RevertedSender.Factory.class);
    factory(SetAssigneeSender.Factory.class);
    factory(AddToAttentionSetSender.Factory.class);
    factory(RemoveFromAttentionSetSender.Factory.class);
  }
}
