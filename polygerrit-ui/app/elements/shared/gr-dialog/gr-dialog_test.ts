/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import '../../../test/common-test-setup-karma';
import './gr-dialog';
import {GrDialog} from './gr-dialog';
import {isHidden, queryAndAssert} from '../../../test/test-utils';
import {fixture, html} from '@open-wc/testing-helpers';

suite('gr-dialog tests', () => {
  let element: GrDialog;

  setup(async () => {
    element = await fixture<GrDialog>(html` <gr-dialog></gr-dialog> `);
    await element.updateComplete;
  });

  test('renders', async () => {
    expect(element).shadowDom.to.equal(/* HTML */ `<div class="container">
      <header class="heading-3">
        <slot name="header"> </slot>
      </header>
      <main>
        <div class="overflow-container">
          <slot name="main"> </slot>
        </div>
      </main>
      <footer>
        <div class="flex-space"></div>
        <gr-button
          aria-disabled="false"
          id="cancel"
          link=""
          role="button"
          tabindex="0"
        >
          Cancel
        </gr-button>
        <gr-button
          aria-disabled="false"
          id="confirm"
          link=""
          primary=""
          role="button"
          tabindex="0"
          title=""
        >
          Confirm
        </gr-button>
      </footer>
    </div> `);
  });

  test('renders with loading state', async () => {
    element.loading = true;
    element.loadingLabel = 'Loading!!';
    await element.updateComplete;
    expect(element).shadowDom.to.equal(/* HTML */ `<div class="container">
      <header class="heading-3">
        <slot name="header"> </slot>
      </header>
      <main>
        <div class="overflow-container">
          <slot name="main"> </slot>
        </div>
      </main>
      <footer>
        <span class="loadingSpin"> </span>
        <span class="loadingLabel"> Loading!! </span>
        <div class="flex-space"></div>
        <gr-button
          aria-disabled="false"
          id="cancel"
          link=""
          role="button"
          tabindex="0"
        >
          Cancel
        </gr-button>
        <gr-button
          aria-disabled="false"
          id="confirm"
          link=""
          primary=""
          role="button"
          tabindex="0"
          title=""
        >
          Confirm
        </gr-button>
      </footer>
    </div> `);
  });

  test('events', () => {
    const confirm = sinon.stub();
    const cancel = sinon.stub();
    element.addEventListener('confirm', confirm);
    element.addEventListener('cancel', cancel);

    MockInteractions.tap(queryAndAssert(element, 'gr-button[primary]'));
    assert.equal(confirm.callCount, 1);

    MockInteractions.tap(queryAndAssert(element, 'gr-button:not([primary])'));
    assert.equal(cancel.callCount, 1);
  });

  test('confirmOnEnter', async () => {
    element.confirmOnEnter = false;
    await element.updateComplete;
    const handleConfirmStub = sinon.stub(element, '_handleConfirm');
    const handleKeydownSpy = sinon.spy(element, '_handleKeydown');
    MockInteractions.keyDownOn(
      queryAndAssert(element, 'main'),
      13,
      null,
      'enter'
    );
    await flush();

    assert.isTrue(handleKeydownSpy.called);
    assert.isFalse(handleConfirmStub.called);

    element.confirmOnEnter = true;
    await element.updateComplete;

    MockInteractions.keyDownOn(
      queryAndAssert(element, 'main'),
      13,
      null,
      'enter'
    );
    await flush();

    assert.isTrue(handleConfirmStub.called);
  });

  test('resetFocus', () => {
    const focusStub = sinon.stub(element.confirmButton!, 'focus');
    element.resetFocus();
    assert.isTrue(focusStub.calledOnce);
  });

  suite('tooltip', () => {
    test('tooltip not added by default', () => {
      assert.isNull(element.confirmButton!.getAttribute('has-tooltip'));
    });

    test('tooltip added if confirm tooltip is passed', async () => {
      element.confirmTooltip = 'confirm tooltip';
      await element.updateComplete;
      assert(element.confirmButton!.getAttribute('has-tooltip'));
    });
  });

  test('empty cancel label hides cancel btn', async () => {
    const cancelButton = queryAndAssert(element, '#cancel');
    assert.isFalse(isHidden(cancelButton));
    element.cancelLabel = '';
    await element.updateComplete;

    assert.isTrue(isHidden(cancelButton));
  });
});
