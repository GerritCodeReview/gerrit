/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-smart-search';
import {GrSmartSearch} from './gr-smart-search';
import {stubRestApi} from '../../../test/test-utils';
import {GroupId, UrlEncodedRepoName} from '../../../types/common';
import {assert, fixture, html} from '@open-wc/testing';

suite('gr-smart-search tests', () => {
  let element: GrSmartSearch;

  setup(async () => {
    element = await fixture(html`<gr-smart-search></gr-smart-search>`);
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ ' <gr-search-bar id="search"> </gr-search-bar> '
    );
  });

  test('Autocompletes groups', () => {
    stubRestApi('getSuggestedGroups').callsFake(() =>
      Promise.resolve({
        Polygerrit: {id: '4c97682e6ce61b7247f3381b6f1789356666de7f' as GroupId},
        gerrit: {id: '4c97682e6ce61b7247f3381b6f1789356666de7f' as GroupId},
        gerrittest: {id: '4c97682e6ce61b7247f3381b6f1789356666de7f' as GroupId},
      })
    );
    return element.fetchGroups('ownerin', 'pol').then(s => {
      assert.deepEqual(s[0], {text: 'ownerin:Polygerrit'});
    });
  });

  test('Autocompletes projects', () => {
    stubRestApi('getSuggestedRepos').callsFake(() =>
      Promise.resolve({Polygerrit: {id: 'test' as UrlEncodedRepoName}})
    );
    return element.fetchProjects('project', 'pol').then(s => {
      assert.deepEqual(s[0], {text: 'project:Polygerrit'});
    });
  });

  test('Autocomplete doesnt override exact matches to input', () => {
    stubRestApi('getSuggestedGroups').callsFake(() =>
      Promise.resolve({
        Polygerrit: {id: '4c97682e6ce61b7247f3381b6f1789356666de7f' as GroupId},
        gerrit: {id: '4c97682e6ce61b7247f3381b6f1789356666de7f' as GroupId},
        gerrittest: {id: '4c97682e6ce61b7247f3381b6f1789356666de7f' as GroupId},
      })
    );
    return element.fetchGroups('ownerin', 'gerrit').then(s => {
      assert.deepEqual(s[0], {text: 'ownerin:Polygerrit'});
      assert.deepEqual(s[1], {text: 'ownerin:gerrit'});
      assert.deepEqual(s[2], {text: 'ownerin:gerrittest'});
    });
  });
});
