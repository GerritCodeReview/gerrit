/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-create-change-help';
import {GrCreateChangeHelp} from './gr-create-change-help';
import {mockPromise, queryAndAssert} from '../../../test/test-utils';
import {GrButton} from '../../shared/gr-button/gr-button';
import {assert, fixture, html} from '@open-wc/testing';

suite('gr-create-change-help tests', () => {
  let element: GrCreateChangeHelp;

  setup(async () => {
    element = await fixture(
      html`<gr-create-change-help></gr-create-change-help>`
    );
  });

  test('Create change tap', async () => {
    const promise = mockPromise();
    element.addEventListener('create-tap', () => promise.resolve());
    queryAndAssert<GrButton>(element, 'gr-button').click();
    await promise;
  });

  test('render', () => {
    assert.shadowDom.equal(
      element,
      /* prettier-ignore */ /* HTML */ `
        <div id="graphic">
          <div id="circle">
            <gr-icon icon="empty_dashboard" id="icon"> </gr-icon>
          </div>
          <p>No outgoing changes yet</p>
        </div>
        <div id="help">
          <h2 class="heading-3">Push your first change for code review</h2>
          <p>
            Pushing a change for review is easy, but a little different from other
          git code review tools. Click on the \`Create Change' button and follow
          the step by step instructions.
          </p>
          <gr-button aria-disabled="false" role="button" tabindex="0">
            Create Change
          </gr-button>
        </div>
      `
    );
  });
});
