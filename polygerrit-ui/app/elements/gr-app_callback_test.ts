/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup';
import './gr-app';
import {fixture, html, assert} from '@open-wc/testing';
import {GrApp} from './gr-app';
import {GrAppElement} from './gr-app-element';
import {GrRouter} from './core/gr-router/gr-router';

suite('gr-app callback tests', () => {
  let grApp: GrApp;
  
  setup(async () => {
    grApp = await fixture<GrApp>(html`<gr-app id="app"></gr-app>`);
  });

  var handleLocationChangeSpy = sinon.spy(GrAppElement.prototype, 'handleLocationChange');
  var dispatchLocationChangeEventSpy = sinon.spy(GrRouter.prototype, <any>'dispatchLocationChangeEvent');

  test('handleLocationChange in gr-app-element is called after dispatching \'location-change\' event in gr-router', () => {
    dispatchLocationChangeEventSpy();
    assert.isTrue(handleLocationChangeSpy.calledOnce);
  });
});

