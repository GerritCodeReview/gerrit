/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup';
import './gr-app-element';
import {testResolver} from '../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import {GrRouter, routerToken} from './core/gr-router/gr-router';
import {
  queryAll,
  queryAndAssert,
  stubRestApi,
  waitQueryAndAssert,
} from '../test/test-utils';
import {GrAppElement} from './gr-app-element';
import {LitElement} from 'lit';
import {createSearchUrl} from '../models/views/search';
import {createChange} from '../test/test-data-generators';
import {NumericChangeId} from '../api/rest-api';
import {createSettingsUrl} from '../models/views/settings';

suite('integration tests', () => {
  let appElement: GrAppElement;
  let router: GrRouter;

  const assertView = async function <T extends LitElement>(tagName: string) {
    await appElement.updateComplete;
    const view = await waitQueryAndAssert<T>(appElement, tagName);
    assert.isOk(view);
    return view;
  };

  const assertItems = function (el: HTMLElement) {
    const list = queryAndAssert(el, 'gr-change-list');
    const section = queryAndAssert(list, 'gr-change-list-section');
    return queryAll(section, 'gr-change-list-item');
  };

  setup(async () => {
    appElement = await fixture<GrAppElement>(
      html`<gr-app-element id="app-element"></gr-app-element>`
    );
    router = testResolver(routerToken);
    router._testOnly_startRouter();
    await appElement.updateComplete;
  });

  teardown(async () => {
    router.finalize();
  });

  test('navigate from search view page to settings page and back', async () => {
    stubRestApi('getChanges').returns(
      Promise.resolve([
        createChange({_number: 1 as NumericChangeId}),
        createChange({_number: 2 as NumericChangeId}),
        createChange({_number: 3 as NumericChangeId}),
      ])
    );

    router.setUrl(createSearchUrl({query: 'asdf'}));
    let view = await assertView('gr-change-list-view');
    assert.equal(assertItems(view).length, 3);

    router.setUrl(createSettingsUrl());
    await assertView('gr-settings-view');

    window.history.back();
    view = await assertView('gr-change-list-view');
    assert.equal(assertItems(view).length, 3);
  });
});
