/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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

import {IronIconElement} from '@polymer/iron-icon';
import '../../../test/common-test-setup-karma';
import {queryAndAssert} from '../../../test/test-utils';
import {GrChangeStar} from './gr-change-star';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {createChange} from '../../../test/test-data-generators';

const basicFixture = fixtureFromElement('gr-change-star');

suite('gr-change-star tests', () => {
  let element: GrChangeStar;

  setup(async () => {
    element = basicFixture.instantiate();
    element.change = {
      ...createChange(),
      starred: true,
    };
    await element.updateComplete;
  });

  test('star visibility states', async () => {
    element.change!.starred = true;
    await element.updateComplete;
    let icon = queryAndAssert<IronIconElement>(element, 'iron-icon');
    assert.isTrue(icon.classList.contains('active'));
    assert.equal(icon.icon, 'gr-icons:star');

    element.change!.starred = false;
    element.requestUpdate('change');
    await element.updateComplete;
    icon = queryAndAssert<IronIconElement>(element, 'iron-icon');
    assert.isFalse(icon.classList.contains('active'));
    assert.equal(icon.icon, 'gr-icons:star-border');
  });

  test('starring', async () => {
    element.change!.starred = false;
    await element.updateComplete;
    assert.equal(element.change!.starred, false);

    MockInteractions.tap(queryAndAssert<HTMLButtonElement>(element, 'button'));
    await element.updateComplete;
    assert.equal(element.change!.starred, true);
  });

  test('unstarring', async () => {
    element.change!.starred = true;
    await element.updateComplete;
    assert.equal(element.change!.starred, true);

    MockInteractions.tap(queryAndAssert<HTMLButtonElement>(element, 'button'));
    await element.updateComplete;
    assert.equal(element.change!.starred, false);
  });
});
