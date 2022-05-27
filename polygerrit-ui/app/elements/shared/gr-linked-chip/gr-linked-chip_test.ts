/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../../test/common-test-setup-karma';
import './gr-linked-chip';
import {GrLinkedChip} from './gr-linked-chip';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {queryAndAssert} from '../../../test/test-utils';

const basicFixture = fixtureFromElement('gr-linked-chip');

suite('gr-linked-chip tests', () => {
  let element: GrLinkedChip;

  setup(async () => {
    element = basicFixture.instantiate();
    await flush();
  });

  test('renders', () => {
    expect(element).shadowDom.to.equal(/* HTML */ `<div class="container">
      <a href=""> <gr-limited-text> </gr-limited-text> </a>
      <gr-button
        aria-disabled="false"
        class="remove"
        hidden=""
        id="remove"
        link=""
        role="button"
        tabindex="0"
      >
        <iron-icon icon="gr-icons:close"> </iron-icon>
      </gr-button>
    </div>`);
  });

  test('remove fired', async () => {
    const spy = sinon.spy();
    element.addEventListener('remove', spy);
    await flush();
    MockInteractions.tap(queryAndAssert(element, '#remove'));
    assert.isTrue(spy.called);
  });
});
