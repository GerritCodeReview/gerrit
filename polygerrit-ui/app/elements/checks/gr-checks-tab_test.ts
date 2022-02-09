/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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
import {html} from 'lit';
import './gr-checks-tab';
import {GrChecksTab} from './gr-checks-tab';
import {fixture} from '@open-wc/testing-helpers';
import {checksModelToken} from '../../models/checks/checks-model';
import {fakeRun4_3, setAllFakeRuns} from '../../models/checks/checks-fakes';
import {resolve} from '../../models/dependency';
import {Category} from '../../api/checks';

suite('gr-checks-tab test', () => {
  let element: GrChecksTab;

  setup(async () => {
    element = await fixture<GrChecksTab>(html`<gr-checks-tab></gr-checks-tab>`);
    const getChecksModel = resolve(element, checksModelToken);
    setAllFakeRuns(getChecksModel());
  });

  test('renders', async () => {
    await element.updateComplete;
    assert.equal(element.runs.length, 44);
    expect(element).shadowDom.to.equal(/* HTML */ `
      <div class="container">
        <gr-checks-runs class="runs" collapsed=""> </gr-checks-runs>
        <gr-checks-results class="results"> </gr-checks-results>
      </div>
    `);
  });

  test('select from tab state', async () => {
    element.tabState = {
      checksTab: {
        statusOrCategory: Category.ERROR,
        filter: 'elim',
        select: 'fake',
        attempt: 3,
      },
    };
    await element.updateComplete;
    assert.equal(element.selectedRuns.length, 39);
    assert.equal(element.selectedAttempts.get(fakeRun4_3.checkName), 3);
  });
});
