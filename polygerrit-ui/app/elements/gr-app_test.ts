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
import {GrApp} from './gr-app.js';
import {appContext} from '../services/app-context.js';
import {html} from '@polymer/polymer/lib/utils/html-tag.js';
import {queryAndAssert, stubRestApi} from '../test/test-utils.js';
import {
  createAccountDetailWithId,
  createPreferences,
  createServerInfo,
} from '../test/test-data-generators.js';
import {GrAppElement} from './gr-app-element.js';
import {GrPluginHost} from './plugins/gr-plugin-host/gr-plugin-host';
import {ServerInfo} from '../types/common.js';
import {GerritView} from '../services/router/router-model.js';
import {
  AppElementChangeViewParams,
  AppElementSearchParam,
} from './gr-app-types.js';

const basicFixture = fixtureFromTemplate(html`<gr-app id="app"></gr-app>`);

suite('gr-app tests', () => {
  let element: GrApp;
  let configStub: sinon.SinonStub;
  let appStartedStub: sinon.SinonStub;
  let routerStartStub: sinon.SinonStub;

  setup(done => {
    appStartedStub = sinon.stub(appContext.reportingService, 'appStarted');
    routerStartStub = stub('gr-router', {
      start: sinon.stub(),
    }).get('start')!;
    stub('gr-account-dropdown', {
      _getTopContent: sinon.stub(),
    });

    stubRestApi('getAccount').returns(
      Promise.resolve(createAccountDetailWithId())
    );
    stubRestApi('getAccountCapabilities').returns(Promise.resolve({}));
    configStub = stubRestApi('getConfig').returns(
      Promise.resolve(createServerInfo())
    );
    stubRestApi('getPreferences').returns(Promise.resolve(createPreferences()));
    stubRestApi('getVersion').returns(Promise.resolve('42'));
    stubRestApi('probePath').returns(Promise.resolve(false));

    element = basicFixture.instantiate() as GrApp;
    flush(done);
  });

  const appElement = () =>
    queryAndAssert<GrAppElement>(element, '#app-element');

  test('reporting', () => {
    assert.isTrue(appStartedStub.calledOnce);
  });

  test('reporting called before router start', () => {
    sinon.assert.callOrder(appStartedStub, routerStartStub);
  });

  test('passes config to gr-plugin-host', () =>
    configStub.lastCall.returnValue.then((config: ServerInfo) => {
      assert.deepEqual(
        queryAndAssert<GrPluginHost>(appElement(), 'gr-plugin-host').config,
        config
      );
    }));

  test('_paramsChanged sets search page', () => {
    appElement()._paramsChanged({
      path: '',
      value: undefined,
      base: {view: GerritView.CHANGE} as AppElementChangeViewParams,
    });
    assert.notOk(appElement()._lastSearchPage);
    appElement()._paramsChanged({
      path: '',
      value: undefined,
      base: {view: GerritView.SEARCH} as AppElementSearchParam,
    });
    assert.ok(appElement()._lastSearchPage);
  });
});
