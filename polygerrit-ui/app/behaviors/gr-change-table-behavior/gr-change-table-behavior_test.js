/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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

import '../../test/common-test-setup-karma.js';
import {Polymer} from '@polymer/polymer/lib/legacy/polymer-fn.js';
import {ChangeTableBehavior} from './gr-change-table-behavior.js';
import {html} from '@polymer/polymer/lib/utils/html-tag.js';

const basicFixture = fixtureFromElement('test-element');

const withinOverlayFixture = fixtureFromTemplate(html`
  <gr-overlay>
    <test-element></test-element>
  </gr-overlay>
`);

suite('gr-change-table-behavior tests', () => {
  let element;
  // eslint-disable-next-line no-unused-vars
  let overlay;

  suiteSetup(() => {
    // Define a Polymer element that uses this behavior.
    Polymer({
      is: 'test-element',
      behaviors: [ChangeTableBehavior],
    });
  });

  setup(() => {
    element = basicFixture.instantiate();
    overlay = withinOverlayFixture.instantiate();
  });

  test('getComplementColumns', () => {
    let columns = [
      'Subject',
      'Status',
      'Owner',
      'Assignee',
      'Reviewers',
      'Comments',
      'Repo',
      'Branch',
      'Updated',
      'Size',
    ];
    assert.deepEqual(element.getComplementColumns(columns), []);

    columns = [
      'Subject',
      'Status',
      'Assignee',
      'Reviewers',
      'Comments',
      'Repo',
      'Branch',
      'Size',
    ];
    assert.deepEqual(element.getComplementColumns(columns),
        ['Owner', 'Updated']);
  });

  test('isColumnHidden', () => {
    const columnToCheck = 'Repo';
    let columnsToDisplay = [
      'Subject',
      'Status',
      'Owner',
      'Assignee',
      'Repo',
      'Branch',
      'Updated',
      'Size',
    ];
    assert.isFalse(element.isColumnHidden(columnToCheck, columnsToDisplay));

    columnsToDisplay = [
      'Subject',
      'Status',
      'Owner',
      'Assignee',
      'Branch',
      'Updated',
      'Size',
    ];
    assert.isTrue(element.isColumnHidden(columnToCheck, columnsToDisplay));
  });

  test('getVisibleColumns maps Project to Repo', () => {
    const columns = [
      'Subject',
      'Status',
      'Owner',
    ];
    assert.deepEqual(element.getVisibleColumns(columns), columns.slice(0));
    assert.deepEqual(
        element.getVisibleColumns(columns.concat(['Project'])),
        columns.slice(0).concat(['Repo']));
  });
});

