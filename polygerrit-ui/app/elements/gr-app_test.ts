/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup-karma';
import './gr-app';
import {getAppContext} from '../services/app-context';
import {fixture, html} from '@open-wc/testing-helpers';
import {queryAndAssert, stubRestApi} from '../test/test-utils';
import {GrApp} from './gr-app';
import {
  createAppElementChangeViewParams,
  createAppElementSearchViewParams,
  createPreferences,
  createServerInfo,
} from '../test/test-data-generators';
import {GrAppElement} from './gr-app-element';
import {GrPluginHost} from './plugins/gr-plugin-host/gr-plugin-host';
import {GrRouter} from './core/gr-router/gr-router';

suite('gr-app tests', () => {
  let grApp: GrApp;
  const config = createServerInfo();
  let appStartedStub: sinon.SinonStub;
  let routerStartStub: sinon.SinonStub;

  setup(async () => {
    appStartedStub = sinon.stub(getAppContext().reportingService, 'appStarted');
    stub('gr-account-dropdown', '_getTopContent');
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

  test('passes config to gr-plugin-host', () => {
    const grAppElement = queryAndAssert<GrAppElement>(grApp, '#app-element');
    const pluginHost = queryAndAssert<GrPluginHost>(grAppElement, '#plugins');
    assert.deepEqual(pluginHost.config, config);
  });

  test('_paramsChanged sets search page', () => {
    const grAppElement = queryAndAssert<GrAppElement>(grApp, '#app-element');

    grAppElement.params = createAppElementChangeViewParams();
    grAppElement.paramsChanged();
    assert.notOk(grAppElement.lastSearchPage);

    grAppElement.params = createAppElementSearchViewParams();
    grAppElement.paramsChanged();
    assert.ok(grAppElement.lastSearchPage);
  });
});
