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

import '../test/common-test-setup-karma.js';
import './gr-app.js';
import {appContext} from '../services/app-context.js';
import {GerritNav} from './core/gr-navigation/gr-navigation.js';
import {html} from '@polymer/polymer/lib/utils/html-tag.js';
import {stubRestApi} from '../test/test-utils.js';

const basicFixture = fixtureFromTemplate(html`<gr-app id="app"></gr-app>`);

suite('gr-app tests', () => {
  let element;
  let configStub;

  setup(async () => {
    sinon.stub(appContext.reportingService, 'appStarted');
    stub('gr-account-dropdown', '_getTopContent');
    stub('gr-router', 'start');
    stubRestApi('getAccount').returns(Promise.resolve({}));
    stubRestApi('getAccountCapabilities').returns(Promise.resolve({}));
    configStub = stubRestApi('getConfig').returns(Promise.resolve({
      plugin: {},
      auth: {
        auth_type: undefined,
      },
    }));
    stubRestApi('getPreferences').returns(Promise.resolve({my: []}));
    stubRestApi('getVersion').returns(Promise.resolve(42));
    stubRestApi('probePath').returns(Promise.resolve(42));

    element = basicFixture.instantiate();
    await flush();
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

  test('passes config to gr-plugin-host', () =>
    configStub.lastCall.returnValue.then(config => {
      assert.deepEqual(appElement().$.plugins.config, config);
    })
  );

  test('_paramsChanged sets search page', () => {
    appElement()._paramsChanged({base: {view: GerritNav.View.CHANGE}});
    assert.notOk(appElement()._lastSearchPage);
    appElement()._paramsChanged({base: {view: GerritNav.View.SEARCH}});
    assert.ok(appElement()._lastSearchPage);
  });
});

