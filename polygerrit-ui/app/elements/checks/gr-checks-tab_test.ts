/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {html} from 'lit';
import './gr-checks-tab';
import {GrChecksTab} from './gr-checks-tab';
import {assert, fixture} from '@open-wc/testing';
import {checksModelToken} from '../../models/checks/checks-model';
import {setAllFakeRuns} from '../../models/checks/checks-fakes';
import {resolve} from '../../models/dependency';

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
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="container">
          <gr-checks-runs class="runs" collapsed=""> </gr-checks-runs>
          <gr-checks-results class="results"> </gr-checks-results>
        </div>
      `
    );
  });
});
