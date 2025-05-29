/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-search-bar';
import {GrSearchBar} from './gr-search-bar';
import '../../../utils/async-util';
import {
  mockPromise,
  pressKey,
  stubRestApi,
  waitUntil,
  waitUntilObserved,
} from '../../../test/test-utils';
import {
  createChangeConfig,
  createServerInfo,
} from '../../../test/test-data-generators';
import {MergeabilityComputationBehavior} from '../../../constants/constants';
import {queryAndAssert} from '../../../test/test-utils';
import {GrAutocomplete} from '../../shared/gr-autocomplete/gr-autocomplete';
import {PaperInputElement} from '@polymer/paper-input/paper-input';
import {assert, fixture, html} from '@open-wc/testing';
import {Key} from '../../../utils/dom-util';
import {getAppContext} from '../../../services/app-context';
import {changeModelToken} from '../../../models/change/change-model';
import {
  ConfigModel,
  configModelToken,
} from '../../../models/config/config-model';
import {wrapInProvider} from '../../../models/di-provider-element';
import {testResolver} from '../../../test/common-test-setup';

suite('gr-search-bar tests', () => {
  let element: GrSearchBar;
  let configModel: ConfigModel;

  setup(async () => {
    const serverConfig = createServerInfo();
    serverConfig.gerrit.doc_url = 'https://mydocumentationurl.google.com/';
    stubRestApi('getConfig').returns(Promise.resolve(serverConfig));
    configModel = new ConfigModel(
      testResolver(changeModelToken),
      getAppContext().restApiService
    );
    configModel.updateServerConfig(serverConfig);
    await waitUntilObserved(
      configModel.docsBaseUrl$,
      docsBaseUrl => docsBaseUrl === 'https://mydocumentationurl.google.com/'
    );

    element = (
      await fixture(
        wrapInProvider(
          html`<gr-search-bar></gr-search-bar>`,
          configModelToken,
          configModel
        )
      )
    ).querySelector('gr-search-bar')!;
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <form>
          <gr-autocomplete
            allow-non-suggested-values=""
            skip-commit-on-item-select=""
            id="searchInput"
            label="Search for changes"
            multi=""
            tab-complete=""
          >
            <gr-icon icon="search" class="searchIcon" slot="prefix"></gr-icon>
            <a
              class="help"
              href="https://mydocumentationurl.google.com/user-search.html"
              slot="suffix"
              tabindex="-1"
              target="_blank"
              rel="noopener noreferrer"
            >
              <gr-icon icon="help" title="read documentation"></gr-icon>
            </a>
          </gr-autocomplete>
        </form>
      `
    );
  });

  test('value is propagated to inputVal', async () => {
    element.value = 'foo';
    await element.updateComplete;
    assert.equal(element.inputVal, 'foo');
  });

  const getActiveElement = () =>
    document.activeElement!.shadowRoot
      ? document.activeElement!.shadowRoot.activeElement
      : document.activeElement;

  test('enter in search input fires event', async () => {
    const promise = mockPromise();
    element.addEventListener('handle-search', () => {
      assert.notEqual(
        getActiveElement(),
        queryAndAssert<GrAutocomplete>(element, '#searchInput')
      );
      promise.resolve();
    });
    element.value = 'test';
    await element.updateComplete;
    const searchInput = queryAndAssert<GrAutocomplete>(element, '#searchInput');
    pressKey(
      queryAndAssert<PaperInputElement>(searchInput, '#input'),
      Key.ENTER
    );
    await promise;
  });

  test('empty search query does not trigger nav', async () => {
    const searchSpy = sinon.spy();
    element.addEventListener('handle-search', searchSpy);
    element.value = '';
    await element.updateComplete;
    const searchInput = queryAndAssert<GrAutocomplete>(element, '#searchInput');
    pressKey(
      queryAndAssert<PaperInputElement>(searchInput, '#input'),
      Key.ENTER
    );
    assert.isFalse(searchSpy.called);
  });

  test('Predefined query op with no predication doesnt trigger nav', async () => {
    const searchSpy = sinon.spy();
    element.addEventListener('handle-search', searchSpy);
    element.value = 'added:';
    await element.updateComplete;
    const searchInput = queryAndAssert<GrAutocomplete>(element, '#searchInput');
    pressKey(
      queryAndAssert<PaperInputElement>(searchInput, '#input'),
      Key.ENTER
    );
    assert.isFalse(searchSpy.called);
  });

  test('predefined predicate query triggers nav', async () => {
    const searchSpy = sinon.spy();
    element.addEventListener('handle-search', searchSpy);
    element.value = 'age:1week';
    await element.updateComplete;
    const searchInput = queryAndAssert<GrAutocomplete>(element, '#searchInput');
    pressKey(
      queryAndAssert<PaperInputElement>(searchInput, '#input'),
      Key.ENTER
    );
    await waitUntil(() => searchSpy.called);
  });

  test('undefined predicate query triggers nav', async () => {
    const searchSpy = sinon.spy();
    element.addEventListener('handle-search', searchSpy);
    element.value = 'random:1week';
    await element.updateComplete;
    const searchInput = queryAndAssert<GrAutocomplete>(element, '#searchInput');
    pressKey(
      queryAndAssert<PaperInputElement>(searchInput, '#input'),
      Key.ENTER
    );
    await waitUntil(() => searchSpy.called);
  });

  test('empty undefined predicate query triggers nav', async () => {
    const searchSpy = sinon.spy();
    element.addEventListener('handle-search', searchSpy);
    element.value = 'random:';
    await element.updateComplete;
    const searchInput = queryAndAssert<GrAutocomplete>(element, '#searchInput');
    pressKey(
      queryAndAssert<PaperInputElement>(searchInput, '#input'),
      Key.ENTER
    );
    await waitUntil(() => searchSpy.called);
  });

  test('keyboard shortcuts', async () => {
    const focusSpy = sinon.spy(
      queryAndAssert<GrAutocomplete>(element, '#searchInput'),
      'focus'
    );
    const selectAllSpy = sinon.spy(
      queryAndAssert<GrAutocomplete>(element, '#searchInput'),
      'selectAll'
    );
    pressKey(document.body, '/');
    assert.isTrue(focusSpy.called);
    assert.isTrue(selectAllSpy.called);
  });

  suite('getSearchSuggestions', () => {
    setup(async () => {
      element = await fixture(html`<gr-search-bar></gr-search-bar>`);
      element.mergeabilityComputationBehavior =
        MergeabilityComputationBehavior.NEVER;
      await element.updateComplete;
    });

    test('Autocompletes accounts', async () => {
      element.accountSuggestions = () =>
        Promise.resolve([{text: 'owner:fred@goog.co'}]);
      await element.updateComplete;
      const s = await element.getSearchSuggestions('owner:fr');
      assert.equal(s[0].value, 'owner:fred@goog.co');
    });

    test('Autocompletes groups', async () => {
      element.groupSuggestions = () =>
        Promise.resolve([
          {text: 'ownerin:Polygerrit'},
          {text: 'ownerin:gerrit'},
        ]);
      await element.updateComplete;
      const s = await element.getSearchSuggestions('ownerin:pol');
      assert.equal(s[0].value, 'ownerin:Polygerrit');
    });

    test('Autocompletes projects', async () => {
      element.projectSuggestions = () =>
        Promise.resolve([
          {text: 'project:Polygerrit'},
          {text: 'project:gerrit'},
          {text: 'project:gerrittest'},
        ]);
      await element.updateComplete;
      const s = await element.getSearchSuggestions('project:pol');
      assert.equal(s[0].value, 'project:Polygerrit');
    });

    test('Autocompletes simple searches', async () => {
      const s = await element.getSearchSuggestions('is:o');
      assert.equal(s[0].name, 'is:open');
      assert.equal(s[0].value, 'is:open');
      assert.equal(s[1].name, 'is:owner');
      assert.equal(s[1].value, 'is:owner');
    });

    test('Does not autocomplete with no match', async () => {
      const s = await element.getSearchSuggestions('asdasdasdasd');
      assert.equal(s.length, 0);
    });

    test('Autocompletes without is:mergable when disabled', async () => {
      const s = await element.getSearchSuggestions('is:mergeab');
      assert.isEmpty(s);
    });

    test('Autocompletes correctly second condition', async () => {
      const s = await element.getSearchSuggestions('is:open me');
      assert.equal(s[0].value, 'mergedafter:');
    });

    test('Autocomplete handles space before expression correctly', async () => {
      // This previously suggested "mergedafter" (incorrectly) due to the
      // leading space.
      const s = await element.getSearchSuggestions('author: me');
      assert.isEmpty(s);
    });
  });

  [
    'API_REF_UPDATED_AND_CHANGE_REINDEX',
    'REF_UPDATED_AND_CHANGE_REINDEX',
  ].forEach(mergeability => {
    suite(`mergeability as ${mergeability}`, () => {
      setup(async () => {
        element = await fixture(html`<gr-search-bar></gr-search-bar>`);
        const serverConfig = {
          ...createServerInfo(),
          change: {
            ...createChangeConfig(),
            mergeability_computation_behavior:
              mergeability as MergeabilityComputationBehavior,
          },
        };
        configModel.updateServerConfig(serverConfig);
        await element.updateComplete;
      });

      test('Autocompltes with is:mergable when enabled', async () => {
        const s = await element.getSearchSuggestions('is:mergeab');
        assert.equal(s.length, 2);
        assert.equal(s[0].name, 'is:mergeable');
        assert.equal(s[0].value, 'is:mergeable');
        assert.equal(s[1].name, '-is:mergeable');
        assert.equal(s[1].value, '-is:mergeable');
      });
    });
  });
});
