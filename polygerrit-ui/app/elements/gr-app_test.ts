/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../test/common-test-setup';
import './gr-app';
import {getAppContext} from '../services/app-context';
import {assert, fixture, html} from '@open-wc/testing';
import {queryAndAssert, stubElement, stubRestApi} from '../test/test-utils';
import {GrApp} from './gr-app';
import {
  createAppElementSearchViewParams,
  createChangeViewState,
  createPreferences,
  createServerInfo,
} from '../test/test-data-generators';
import {GrAppElement} from './gr-app-element';
import {GrRouter, routerToken} from './core/gr-router/gr-router';
import {resolve} from '../models/dependency';
import {removeRequestDependencyListener} from '../test/common-test-setup';
import {ReactiveElement} from 'lit';

suite('gr-app callback tests', () => {
  const requestUpdateStub = sinon.stub(
    ReactiveElement.prototype,
    'requestUpdate'
  );
  const dispatchLocationChangeEventSpy = sinon.spy(
    GrRouter.prototype,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    <any>'dispatchLocationChangeEvent'
  );
  setup(async () => {
    await fixture<GrApp>(html`<gr-app id="app"></gr-app>`);
  });

  test("requestUpdate in reactive-element is called after dispatching 'location-change' event in gr-router", () => {
    dispatchLocationChangeEventSpy();
    assert.isTrue(requestUpdateStub.calledOnce);
  });
});

suite('gr-app tests', () => {
  let grApp: GrApp;
  const config = createServerInfo();
  let appStartedStub: sinon.SinonStub;
  let routerStartStub: sinon.SinonStub;

  setup(async () => {
    appStartedStub = sinon.stub(getAppContext().reportingService, 'appStarted');
    stubElement('gr-account-dropdown', 'getTopContent');
    routerStartStub = sinon.stub(GrRouter.prototype, 'start');
    stubRestApi('getAccount').returns(Promise.resolve(undefined));
    stubRestApi('getAccountCapabilities').returns(Promise.resolve({}));
    stubRestApi('getConfig').returns(Promise.resolve(config));
    stubRestApi('getPreferences').returns(Promise.resolve(createPreferences()));
    stubRestApi('getVersion').returns(Promise.resolve('42'));
    stubRestApi('probePath').returns(Promise.resolve(false));
    grApp = await fixture<GrApp>(html`<gr-app id="app"></gr-app>`);
  });

  test('models resolve', () => {
    // Verify that models resolve on grApp without falling back
    // to the ones instantiated by the test-setup.
    removeRequestDependencyListener();
    assert.ok(resolve(grApp, routerToken)());
  });

  test('reporting', () => {
    assert.isTrue(appStartedStub.calledOnce);
  });

  test('reporting called before router start', () => {
    sinon.assert.callOrder(appStartedStub, routerStartStub);
  });

  test('_paramsChanged sets search page', () => {
    const grAppElement = queryAndAssert<GrAppElement>(grApp, '#app-element');

    grAppElement.params = createChangeViewState();
    grAppElement.paramsChanged();
    assert.notOk(grAppElement.lastSearchPage);

    grAppElement.params = createAppElementSearchViewParams();
    grAppElement.paramsChanged();
    assert.ok(grAppElement.lastSearchPage);
  });

  test('scroll-padding-top is set when connected', () => {
    assert.equal(
      document.documentElement.style.getPropertyValue('scroll-padding-top'),
      'calc(var(--main-header-height) + var(--change-header-height) + var(--diff-header-height))'
    );
  });

  test('scroll-padding-top is removed when disconnected', () => {
    assert.equal(
      document.documentElement.style.getPropertyValue('scroll-padding-top'),
      'calc(var(--main-header-height) + var(--change-header-height) + var(--diff-header-height))'
    );
    grApp.remove();
    assert.equal(
      document.documentElement.style.getPropertyValue('scroll-padding-top'),
      ''
    );
  });

  test('scroll-padding-top drops to 0px on focus inside sticky header and restores on blur', () => {
    const grAppElement = queryAndAssert<GrAppElement>(grApp, '#app-element');
    const mainHeader = queryAndAssert(grAppElement, 'gr-main-header');

    const searchBar = queryAndAssert(mainHeader, 'gr-smart-search');
    const searchAutocomplete = queryAndAssert(
      searchBar,
      'gr-search-autocomplete'
    );
    const autocomplete = queryAndAssert(searchAutocomplete, 'gr-autocomplete');
    const input = queryAndAssert(autocomplete, '#input');

    // 1. Focus inside nested search bar input in sticky mainHeader
    input.dispatchEvent(
      new FocusEvent('focusin', {bubbles: true, composed: true})
    );
    assert.equal(
      document.documentElement.style.getPropertyValue('scroll-padding-top'),
      '0px'
    );

    // 2. Focus moves to non-sticky content
    grAppElement.dispatchEvent(
      new FocusEvent('focusin', {bubbles: true, composed: true})
    );
    assert.equal(
      document.documentElement.style.getPropertyValue('scroll-padding-top'),
      'calc(var(--main-header-height) + var(--change-header-height) + var(--diff-header-height))'
    );

    // 3. Re-focus inside sticky header
    mainHeader.dispatchEvent(
      new FocusEvent('focusin', {bubbles: true, composed: true})
    );
    assert.equal(
      document.documentElement.style.getPropertyValue('scroll-padding-top'),
      '0px'
    );

    // 4. Focus leaves the window (blur, relatedTarget: null)
    mainHeader.dispatchEvent(
      new FocusEvent('focusout', {
        bubbles: true,
        composed: true,
        relatedTarget: null,
      })
    );
    assert.equal(
      document.documentElement.style.getPropertyValue('scroll-padding-top'),
      'calc(var(--main-header-height) + var(--change-header-height) + var(--diff-header-height))'
    );
  });

  test('focus transition between sticky elements maintains 0px without intermediate reset', () => {
    const grAppElement = queryAndAssert<GrAppElement>(grApp, '#app-element');
    const mainHeader = queryAndAssert(grAppElement, 'gr-main-header');

    // Create a second sticky element to simulate another sticky header or sibling
    const secondSticky = document.createElement('div');
    secondSticky.style.position = 'sticky';
    secondSticky.style.top = '48px';
    grAppElement.shadowRoot!.appendChild(secondSticky);

    // Focus first sticky element
    mainHeader.dispatchEvent(
      new FocusEvent('focusin', {bubbles: true, composed: true})
    );
    assert.equal(
      document.documentElement.style.getPropertyValue('scroll-padding-top'),
      '0px'
    );

    // Focusout from mainHeader transferring to secondSticky
    mainHeader.dispatchEvent(
      new FocusEvent('focusout', {
        bubbles: true,
        composed: true,
        relatedTarget: secondSticky,
      })
    );
    // Because relatedTarget is non-null, focusout does not prematurely reset
    assert.equal(
      document.documentElement.style.getPropertyValue('scroll-padding-top'),
      '0px'
    );

    // Focusin on secondSticky
    secondSticky.dispatchEvent(
      new FocusEvent('focusin', {bubbles: true, composed: true})
    );
    assert.equal(
      document.documentElement.style.getPropertyValue('scroll-padding-top'),
      '0px'
    );

    secondSticky.remove();
  });
});
