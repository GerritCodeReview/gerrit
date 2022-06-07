/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
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
