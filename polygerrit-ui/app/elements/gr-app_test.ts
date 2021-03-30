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

import '../test/common-test-setup-karma';
import {GrApp} from './gr-app';
import {appContext} from '../services/app-context';
import {html} from '@polymer/polymer/lib/utils/html-tag';
import {queryAndAssert} from '../test/test-utils';
import {createServerInfo} from '../test/test-data-generators';
import {GrAppElement} from './gr-app-element';
import {GrPluginHost} from './plugins/gr-plugin-host/gr-plugin-host';
import {GerritView} from '../services/router/router-model';
import {
  AppElementChangeViewParams,
  AppElementSearchParam,
} from './gr-app-types';
import {GrRouter} from './core/gr-router/gr-router';
import {ReportingService} from '../services/gr-reporting/gr-reporting';

const basicFixture = fixtureFromTemplate(html`<gr-app id="app"></gr-app>`);

suite('gr-app tests', () => {
  let element: GrApp;
  let appStartedStub: sinon.SinonStubbedMember<ReportingService['appStarted']>;
  let routerStartStub: sinon.SinonStubbedMember<GrRouter['start']>;

  setup(done => {
    appStartedStub = sinon.stub(appContext.reportingService, 'appStarted');
    routerStartStub = stub('gr-router', 'start');
    stub('gr-account-dropdown', '_getTopContent');

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

  test('passes config to gr-plugin-host', () => {
    assert.deepEqual(
      queryAndAssert<GrPluginHost>(appElement(), 'gr-plugin-host').config,
      createServerInfo()
    );
  });

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
