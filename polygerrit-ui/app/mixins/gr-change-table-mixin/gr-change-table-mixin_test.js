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
import {ChangeTableMixin} from './gr-change-table-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';

// This avoids JSC_DYNAMIC_EXTENDS_WITHOUT_JSDOC closure compiler error.
const base = ChangeTableMixin(PolymerElement);

class GrChangeTableMixinTestElement extends base {
  static get is() { return 'gr-change-table-mixin-test-element'; }
}

customElements.define(GrChangeTableMixinTestElement.is,
    GrChangeTableMixinTestElement);

const basicFixture = fixtureFromElement(
    'gr-change-table-mixin-test-element');

suite('gr-change-table-mixin tests', () => {
  let element;

  setup(() => {
    element = basicFixture.instantiate();
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

  test('renameProjectToRepoColumn maps Project to Repo', () => {
    const columns = [
      'Subject',
      'Status',
      'Owner',
    ];
    assert.deepEqual(element.renameProjectToRepoColumn(columns),
        columns.slice(0));
    assert.deepEqual(
        element.renameProjectToRepoColumn(columns.concat(['Project'])),
        columns.slice(0).concat(['Repo']));
  });
});

