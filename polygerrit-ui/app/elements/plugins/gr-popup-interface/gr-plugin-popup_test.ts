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
  let modalOpen: sinon.SinonStub;
  let modalClose: sinon.SinonStub;

  setup(async () => {
    element = await fixture(html`<gr-plugin-popup></gr-plugin-popup>`);
    await element.updateComplete;
    modalOpen = stubElement('dialog', 'showModal');
    modalClose = stubElement('dialog', 'close');
  });

  test('exists', () => {
    assert.isOk(element);
  });

  test('open uses open() from dialog', () => {
    element.open();
    assert.isTrue(modalOpen.called);
  });

  test('close uses close() from dialog', () => {
    element.close();
    assert.isTrue(modalClose.called);
  });
});
