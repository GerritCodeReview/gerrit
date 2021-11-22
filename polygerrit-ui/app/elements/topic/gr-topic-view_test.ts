/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {KnownExperimentId} from '../../services/flags/flags';
import '../../test/common-test-setup-karma';
import {createGenerateUrlTopicViewParams} from '../../test/test-data-generators';
import {stubFlags} from '../../test/test-utils';
import {GerritNav} from '../core/gr-navigation/gr-navigation';
import './gr-topic-view';
import {GrTopicView} from './gr-topic-view';

const basicFixture = fixtureFromElement('gr-topic-view');

suite('gr-topic-view tests', () => {
  let element: GrTopicView;
  let redirectStub: sinon.SinonStub;

  async function commonSetup(experimentEnabled: boolean) {
    redirectStub = sinon.stub(GerritNav, 'navigateToSearchQuery');
    stubFlags('isEnabled')
      .withArgs(KnownExperimentId.TOPICS_PAGE)
      .returns(experimentEnabled);
    element = basicFixture.instantiate();
    element.params = createGenerateUrlTopicViewParams();
    await element.updateComplete;
  }

  suite('experiment enabled', () => {
    setup(async () => {
      await commonSetup(true);
    });
    test('does not redirect to search results page if experiment is enabled', () => {
      assert.isFalse(redirectStub.notCalled);
    });
  });

  suite('experiment disabled', () => {
    setup(async () => {
      await commonSetup(false);
    });
    test('redirects to search results page if experiment is disabled', () => {
      assert.isTrue(redirectStub.calledWith('topic:myTopic'));
    });
  });
});
