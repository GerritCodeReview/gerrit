/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-dialog';
import {GrDialog} from './gr-dialog';
import {
  isHidden,
  pressKey,
  queryAndAssert,
  waitEventLoop,
} from '../../../test/test-utils';
import {fixture, html, assert} from '@open-wc/testing';
import {GrButton} from '../gr-button/gr-button';

suite('gr-dialog tests', () => {
  let element: GrDialog;

  setup(async () => {
    element = await fixture<GrDialog>(html` <gr-dialog></gr-dialog> `);
    await element.updateComplete;
  });

  test('renders', async () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `<div class="container">
        <header class="heading-3">
          <slot name="header"> </slot>
        </header>
        <main>
          <div class="overflow-container">
            <slot name="main"> </slot>
          </div>
        </main>
        <footer>
          <slot name="footer"></slot>
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
      </div> `
    );
  });

  test('renders with loading state', async () => {
    element.loading = true;
    element.loadingLabel = 'Loading!!';
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `<div class="container">
        <header class="heading-3">
          <slot name="header"> </slot>
        </header>
        <main>
          <div class="overflow-container">
            <slot name="main"> </slot>
          </div>
        </main>
        <footer>
          <span class="loadingSpin" aria-label="Loading!!" role="progressbar">
          </span>
          <span class="loadingLabel"> Loading!! </span>
          <slot name="footer"></slot>
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
      </div> `
    );
  });

  test('events', () => {
    const confirm = sinon.stub();
    const cancel = sinon.stub();
    element.addEventListener('confirm', confirm);
    element.addEventListener('cancel', cancel);

    queryAndAssert<GrButton>(element, 'gr-button[primary]').click();
    assert.equal(confirm.callCount, 1);

    queryAndAssert<GrButton>(element, 'gr-button:not([primary])').click();
    assert.equal(cancel.callCount, 1);
  });

  test('confirmOnEnter', async () => {
    element.confirmOnEnter = false;
    await element.updateComplete;
    const handleConfirmStub = sinon.stub(element, '_handleConfirm');
    const handleKeydownSpy = sinon.spy(element, '_handleKeydown');
    pressKey(queryAndAssert(element, 'main'), 'Enter');
    await waitEventLoop();

    assert.isTrue(handleKeydownSpy.called);
    assert.isFalse(handleConfirmStub.called);

    element.confirmOnEnter = true;
    await element.updateComplete;

    pressKey(queryAndAssert(element, 'main'), 'Enter');
    await waitEventLoop();

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
