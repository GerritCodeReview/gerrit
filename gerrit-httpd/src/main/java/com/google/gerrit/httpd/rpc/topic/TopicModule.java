// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc.topic;

import com.google.gerrit.httpd.rpc.RpcServletModule;
import com.google.gerrit.httpd.rpc.UiRpcModule;
import com.google.gerrit.server.config.FactoryModule;

public class TopicModule extends RpcServletModule {
  public TopicModule() {
    super(UiRpcModule.PREFIX);
  }

  @Override
  protected void configureServlets() {
    install(new FactoryModule() {
      @Override
      protected void configure() {
        factory(AbandonTopic.Factory.class);
        factory(RestoreTopic.Factory.class);
        factory(RevertTopic.Factory.class);
        factory(AddTopicReviewerHandler.Factory.class);
        factory(ChangeSetDetailFactory.Factory.class);
        factory(ChangeSetPublishDetailFactory.Factory.class);
        factory(ChangeSetInfoFactory.Factory.class);
        factory(RemoveTopicReviewerHandler.Factory.class);
        factory(TopicDetailFactory.Factory.class);
        factory(SubmitAction.Factory.class);
        factory(IncludedInDetailHandler.Factory.class);
      }
    });
    rpc(TopicDetailServiceImpl.class);
    rpc(TopicManageServiceImpl.class);
  }
}
