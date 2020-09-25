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
import './gr-user-header.js';

const basicFixture = fixtureFromElement('gr-user-header');

suite('gr-user-header tests', () => {
  let element;

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('loads and clears account info', done => {
    sinon.stub(element.$.restAPI, 'getAccountDetails')
        .returns(Promise.resolve({
          name: 'foo',
          email: 'bar',
          status: 'OOO',
          registered_on: '2015-03-12 18:32:08.000000000',
        }));

    element.userId = 'foo.bar@baz';
    flush(() => {
      assert.isOk(element._accountDetails);
      assert.isOk(element._status);

      element.userId = null;
      flush(() => {
        flush();
        assert.isNull(element._accountDetails);
        assert.equal(element._status, '');

        done();
      });
    });
  });

  test('_computeDashboardLinkClass', () => {
    assert.include(element._computeDashboardLinkClass(false, false), 'hide');
    assert.include(element._computeDashboardLinkClass(true, false), 'hide');
    assert.include(element._computeDashboardLinkClass(false, true), 'hide');
    assert.notInclude(element._computeDashboardLinkClass(true, true), 'hide');
  });
});

