/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup';
import './gr-app';
import {getAppContext} from '../services/app-context';
import {fixture, html, assert} from '@open-wc/testing';
import {queryAndAssert, stubElement, stubRestApi} from '../test/test-utils';
import {GrApp} from './gr-app';
import {
  createChangeViewState,
  createAppElementSearchViewParams,
  createPreferences,
  createServerInfo,
} from '../test/test-data-generators';
import {GrAppElement} from './gr-app-element';
import {GrRouter} from './core/gr-router/gr-router';

suite('gr-app callback tests', () => {
  const handleLocationChangeSpy = sinon.spy(
    GrAppElement.prototype,
    <any>'handleLocationChange'
  );
  const dispatchLocationChangeEventSpy = sinon.spy(
    GrRouter.prototype,
    <any>'dispatchLocationChangeEvent'
  );

  setup(async () => {
    await fixture<GrApp>(html`<gr-app id="app"></gr-app>`);
  });

  test("handleLocationChange in gr-app-element is called after dispatching 'location-change' event in gr-router", () => {
    dispatchLocationChangeEventSpy();
    assert.isTrue(handleLocationChangeSpy.calledOnce);
  });
});

suite('gr-app tests', () => {
  let grApp: GrApp;
  const config = createServerInfo();
  let appStartedStub: sinon.SinonStub;
  let routerStartStub: sinon.SinonStub;

  setup(async () => {
    appStartedStub = sinon.stub(getAppContext().reportingService, 'appStarted');
    stubElement('gr-account-dropdown', '_getTopContent');
    routerStartStub = sinon.stub(GrRouter.prototype, 'start');
    stubRestApi('getAccount').returns(Promise.resolve(undefined));
    stubRestApi('getAccountCapabilities').returns(Promise.resolve({}));
    stubRestApi('getConfig').returns(Promise.resolve(config));
    stubRestApi('getPreferences').returns(Promise.resolve(createPreferences()));
    stubRestApi('getVersion').returns(Promise.resolve('42'));
    stubRestApi('probePath').returns(Promise.resolve(false));

    grApp = await fixture<GrApp>(html`<gr-app id="app"></gr-app>`);
    await grApp.updateComplete;
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
