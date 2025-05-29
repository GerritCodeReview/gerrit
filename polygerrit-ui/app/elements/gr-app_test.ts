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
});
