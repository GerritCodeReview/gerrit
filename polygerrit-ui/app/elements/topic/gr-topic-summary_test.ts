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

import '../../test/common-test-setup-karma';
import {createChange} from '../../test/test-data-generators';
import {queryAll, stubRestApi} from '../../test/test-utils';
import './gr-topic-summary';
import {GrTopicSummary} from './gr-topic-summary';

const basicFixture = fixtureFromElement('gr-topic-summary');

suite('gr-topic-summary tests', () => {
  let element: GrTopicSummary;

  setup(async () => {
    stubRestApi('getChanges')
      .withArgs(undefined, 'topic:myTopic')
      .resolves([createChange(), createChange(), createChange()]);
    element = basicFixture.instantiate();
    element.topicName = 'myTopic';
    await element.updateComplete;
  });

  test('shows topic information', () => {
    const labels = queryAll<HTMLSpanElement>(element, 'span');
    assert.equal(labels[0].textContent, 'Topic: myTopic');
    assert.equal(labels[1].textContent, '3 changes');
  });
});
