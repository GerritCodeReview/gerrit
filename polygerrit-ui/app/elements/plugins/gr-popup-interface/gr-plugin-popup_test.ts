/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {fixture, html, assert} from '@open-wc/testing';
import '../../../test/common-test-setup';
import {stubElement} from '../../../test/test-utils';
import './gr-plugin-popup';
import {GrPluginPopup} from './gr-plugin-popup';

suite('gr-plugin-popup tests', () => {
  let element: GrPluginPopup;
  let overlayOpen: sinon.SinonStub;
  let overlayClose: sinon.SinonStub;

  setup(async () => {
    element = await fixture(html`<gr-plugin-popup></gr-plugin-popup>`);
    await element.updateComplete;
    overlayOpen = stubElement('gr-overlay', 'open').callsFake(() =>
      Promise.resolve()
    );
    overlayClose = stubElement('gr-overlay', 'close');
  });

  test('exists', () => {
    assert.isOk(element);
  });

  test('open uses open() from gr-overlay', async () => {
    await element.open();
    assert.isTrue(overlayOpen.called);
  });

  test('close uses close() from gr-overlay', () => {
    element.close();
    assert.isTrue(overlayClose.called);
  });
});
