
/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


<meta charset="utf-8">







<test-fixture id="basic">
  <template>
    <gr-app id="app"></gr-app>
  </template>
</test-fixture>


import '../test/common-test-setup.js';
import './gr-app.js';
import {appContext} from '../services/app-context.js';
import {GerritNav} from './core/gr-navigation/gr-navigation.js';

suite('gr-app tests', () => {
  let sandbox;
  let element;

  setup(done => {
    sandbox = sinon.sandbox.create();
    sandbox.stub(appContext.reportingService, 'appStarted');
    stub('gr-account-dropdown', {
      _getTopContent: sinon.stub(),
    });
    stub('gr-router', {
      start: sandbox.stub(),
    });
    stub('gr-rest-api-interface', {
      getAccount() { return Promise.resolve({}); },
      getAccountCapabilities() { return Promise.resolve({}); },
      getConfig() {
        return Promise.resolve({
          plugin: {},
          auth: {
            auth_type: undefined,
          },
        });
      },
      getPreferences() { return Promise.resolve({my: []}); },
      getDiffPreferences() { return Promise.resolve({}); },
      getEditPreferences() { return Promise.resolve({}); },
      getVersion() { return Promise.resolve(42); },
      probePath() { return Promise.resolve(42); },
    });

    element = fixture('basic');
    flush(done);
  });

  teardown(() => {
    sandbox.restore();
  });

  const appElement = () => element.$['app-element'];

  test('reporting', () => {
    assert.isTrue(appElement().reporting.appStarted.calledOnce);
  });

  test('reporting called before router start', () => {
    const element = appElement();
    const appStartedStub = element.reporting.appStarted;
    const routerStartStub = element.$.router.start;
    sinon.assert.callOrder(appStartedStub, routerStartStub);
  });

  test('passes config to gr-plugin-host', () => {
    const config = appElement().$.restAPI.getConfig;
    return config.lastCall.returnValue.then(config => {
      assert.deepEqual(appElement().$.plugins.config, config);
    });
  });

  test('_paramsChanged sets search page', () => {
    appElement()._paramsChanged({base: {view: GerritNav.View.CHANGE}});
    assert.notOk(appElement()._lastSearchPage);
    appElement()._paramsChanged({base: {view: GerritNav.View.SEARCH}});
    assert.ok(appElement()._lastSearchPage);
  });
});

