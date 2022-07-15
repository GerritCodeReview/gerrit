/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-create-change-help';
import {GrCreateChangeHelp} from './gr-create-change-help';
import {mockPromise, queryAndAssert} from '../../../test/test-utils';
import {GrButton} from '../../shared/gr-button/gr-button';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';

const basicFixture = fixtureFromElement('gr-create-change-help');

suite('gr-create-change-help tests', () => {
  let element: GrCreateChangeHelp;

  setup(async () => {
    element = basicFixture.instantiate();
    await flush();
  });

  test('Create change tap', async () => {
    const promise = mockPromise();
    element.addEventListener('create-tap', () => promise.resolve());
    MockInteractions.tap(queryAndAssert<GrButton>(element, 'gr-button'));
    await promise;
  });

  test('render', () => {
    expect(element).shadowDom.to.equal(/* HTML */ `
      <div id="graphic">
        <div id="circle">
          <iron-icon icon="gr-icons:zeroState" id="icon"> </iron-icon>
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
    `);
  });
});
