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

import {ChangeInfo, ChangeStatus, TopicName} from '../../api/rest-api';
import '../../test/common-test-setup-karma';
import {
  createAccountWithIdNameAndEmail,
  createChange,
  TEST_BRANCH_ID,
  TEST_SUBJECT,
} from '../../test/test-data-generators';
import {queryAll} from '../../test/test-utils';
import './gr-topic-tree-row';
import {GrTopicTreeRow} from './gr-topic-tree-row';

const basicFixture = fixtureFromElement('gr-topic-tree-row');

suite('gr-topic-tree-row tests', () => {
  let element: GrTopicTreeRow;
  const owner = createAccountWithIdNameAndEmail();
  const change: ChangeInfo = {
    ...createChange(),
    insertions: 50,
    topic: 'myTopic' as TopicName,
    owner,
  };

  setup(async () => {
    element = basicFixture.instantiate();
    element.change = change;
    await element.updateComplete;
  });

  test('shows columns of change information', () => {
    const columns = queryAll<HTMLTableCellElement>(element, 'td');
    assert.equal(columns[0].textContent, 'M');
    assert.equal(columns[1].textContent, TEST_SUBJECT);
    assert.equal(columns[2].textContent, 'myTopic');
    assert.equal(columns[3].textContent, TEST_BRANCH_ID);
    assert.equal(columns[4].textContent, owner.name);
    assert.equal(columns[5].textContent, ChangeStatus.NEW);
  });

  test('shows unknown size', async () => {
    element.change = {...change, insertions: 0, deletions: 0};
    await element.updateComplete;

    const columns = queryAll<HTMLTableCellElement>(element, 'td');
    assert.equal(columns[0].textContent, '');
  });

  test('shows XS size', async () => {
    element.change = {...change, insertions: 3, deletions: 6};
    await element.updateComplete;

    const columns = queryAll<HTMLTableCellElement>(element, 'td');
    assert.equal(columns[0].textContent, 'XS');
  });

  test('shows S size', async () => {
    element.change = {...change, insertions: 9, deletions: 40};
    await element.updateComplete;

    const columns = queryAll<HTMLTableCellElement>(element, 'td');
    assert.equal(columns[0].textContent, 'S');
  });

  test('shows M size', async () => {
    element.change = {...change, insertions: 249, deletions: 0};
    await element.updateComplete;

    const columns = queryAll<HTMLSpanElement>(element, 'td');
    assert.equal(columns[0].textContent, 'M');
  });

  test('shows L size', async () => {
    element.change = {...change, insertions: 499, deletions: 500};
    await element.updateComplete;

    const columns = queryAll<HTMLTableCellElement>(element, 'td');
    assert.equal(columns[0].textContent, 'L');
  });

  test('shows XL size', async () => {
    element.change = {...change, insertions: 1000, deletions: 1};
    await element.updateComplete;

    const columns = queryAll<HTMLTableCellElement>(element, 'td');
    assert.equal(columns[0].textContent, 'XL');
  });
});
