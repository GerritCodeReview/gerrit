/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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

import '../../../test/common-test-setup-karma.js';
import './gr-agreements-list.js';

const basicFixture = fixtureFromElement('gr-agreements-list');

suite('gr-agreements-list tests', () => {
  let element;
  let agreements;

  setup(done => {
    agreements = [{
      url: 'some url',
      description: 'Agreements 1 description',
      name: 'Agreements 1',
    }];

    stub('gr-rest-api-interface', {
      getAccountAgreements() { return Promise.resolve(agreements); },
    });

    element = basicFixture.instantiate();

    element.loadData().then(() => { flush(done); });
  });

  test('renders', () => {
    const rows = element.root.querySelectorAll('tbody tr');

    assert.equal(rows.length, 1);

    const nameCells = Array.from(rows).map(row => { return row.querySelectorAll('td')[0].textContent.trim(); }
    );

    assert.equal(nameCells[0], 'Agreements 1');
  });
});

