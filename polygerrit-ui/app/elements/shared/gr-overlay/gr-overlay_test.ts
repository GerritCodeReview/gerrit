/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-overlay';
import {html} from '@polymer/polymer/lib/utils/html-tag';
import {GrOverlay} from './gr-overlay';

const basicFixture = fixtureFromTemplate(html`
  <gr-overlay>
    <div>content</div>
  </gr-overlay>
`);

suite('gr-overlay tests', () => {
  let element: GrOverlay;

  setup(() => {
    element = basicFixture.instantiate() as GrOverlay;
  });

  test('popstate listener is attached on open and removed on close', () => {
    const addEventListenerStub = sinon.stub(window, 'addEventListener');
    const removeEventListenerStub = sinon.stub(window, 'removeEventListener');
    element.open();
    assert.isTrue(addEventListenerStub.called);
    assert.equal(addEventListenerStub.lastCall.args[0], 'popstate');
    assert.equal(
      addEventListenerStub.lastCall.args[1],
      element._boundHandleClose
    );
    element._overlayClosed();
    assert.isTrue(removeEventListenerStub.called);
    assert.equal(removeEventListenerStub.lastCall.args[0], 'popstate');
    assert.equal(
      removeEventListenerStub.lastCall.args[1],
      element._boundHandleClose
    );
  });

  test('events are fired on fullscreen view', async () => {
    const isMobileStub = sinon.stub(element, '_isMobile').returns(true as any);
    const openHandler = sinon.stub();
    const closeHandler = sinon.stub();
    element.addEventListener('fullscreen-overlay-opened', openHandler);
    element.addEventListener('fullscreen-overlay-closed', closeHandler);

    await element.open();

    assert.isTrue(isMobileStub.called);
    assert.isTrue(element.fullScreenOpen);
    assert.isTrue(openHandler.called);

    element._overlayClosed();
    assert.isFalse(element.fullScreenOpen);
    assert.isTrue(closeHandler.called);
  });

  test('events are not fired on desktop view', async () => {
    const isMobileStub = sinon.stub(element, '_isMobile').returns(false as any);
    const openHandler = sinon.stub();
    const closeHandler = sinon.stub();
    element.addEventListener('fullscreen-overlay-opened', openHandler);
    element.addEventListener('fullscreen-overlay-closed', closeHandler);

    await element.open();

    assert.isTrue(isMobileStub.called);
    assert.isFalse(element.fullScreenOpen);
    assert.isFalse(openHandler.called);

    element._overlayClosed();
    assert.isFalse(element.fullScreenOpen);
    assert.isFalse(closeHandler.called);
  });
});
