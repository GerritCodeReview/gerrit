/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-alert';
import {GrAlert} from './gr-alert';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';

suite('gr-alert tests', () => {
  let element: GrAlert;

  setup(() => {
    // The gr-alert element attaches itself to the root element on .show(),
    // rather than existing under a fixture parent.
    element = document.createElement('gr-alert');
  });

  teardown(() => {
    if (element.parentNode) {
      element.parentNode.removeChild(element);
    }
  });

  test('render', async () => {
    element.show('Alert text');
    await element.updateComplete;

    expect(element).shadowDom.to.equal(/* HTML */ `
      <div class="content-wrapper">
        <span class="text"> Alert text </span>
        <gr-button
          aria-disabled="false"
          class="action"
          hidden=""
          link=""
          role="button"
          tabindex="0"
        >
        </gr-button>
      </div>
    `);
  });

  test('show/hide', async () => {
    assert.isNull(element.parentNode);
    element.show('Alert text');
    // wait for element to be rendered after being attached to DOM
    await flush();
    assert.equal(element.parentNode, document.body);
    element.style.setProperty('--gr-alert-transition-duration', '0ms');
    element.hide();
    assert.isNull(element.parentNode);
  });

  test('action event', async () => {
    const spy = sinon.spy();
    element.show('Alert text');
    await flush();
    element._actionCallback = spy;
    assert.isFalse(spy.called);
    MockInteractions.tap(element.shadowRoot!.querySelector('.action')!);
    assert.isTrue(spy.called);
  });
});
