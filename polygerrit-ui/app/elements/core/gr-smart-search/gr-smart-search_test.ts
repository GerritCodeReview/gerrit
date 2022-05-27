/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../../test/common-test-setup-karma';
import './gr-smart-search';
import {GrSmartSearch} from './gr-smart-search';
import {stubRestApi} from '../../../test/test-utils';
import {EmailAddress, GroupId, UrlEncodedRepoName} from '../../../types/common';

const basicFixture = fixtureFromElement('gr-smart-search');

suite('gr-smart-search tests', () => {
  let element: GrSmartSearch;

  setup(async () => {
    element = basicFixture.instantiate();
    await element.updateComplete;
  });

  test('Autocompletes accounts', () => {
    stubRestApi('getSuggestedAccounts').callsFake(() =>
      Promise.resolve([
        {
          name: 'fred',
          email: 'fred@goog.co' as EmailAddress,
        },
      ])
    );
    return element.fetchAccounts('owner', 'fr').then(s => {
      assert.deepEqual(s[0], {text: 'owner:fred@goog.co', label: 'fred'});
    });
  });

  test('Inserts self as option when valid', () => {
    stubRestApi('getSuggestedAccounts').callsFake(() =>
      Promise.resolve([
        {
          name: 'fred',
          email: 'fred@goog.co' as EmailAddress,
        },
      ])
    );
    element
      .fetchAccounts('owner', 's')
      .then(s => {
        assert.deepEqual(s[0], {text: 'owner:fred@goog.co', label: 'fred'});
        assert.deepEqual(s[1], {text: 'owner:self'});
      })
      .then(() => element.fetchAccounts('owner', 'selfs'))
      .then(s => {
        assert.notEqual(s[0], {text: 'owner:self'});
      });
  });

  test('Inserts me as option when valid', () => {
    stubRestApi('getSuggestedAccounts').callsFake(() =>
      Promise.resolve([
        {
          name: 'fred',
          email: 'fred@goog.co' as EmailAddress,
        },
      ])
    );
    return element
      .fetchAccounts('owner', 'm')
      .then(s => {
        assert.deepEqual(s[0], {text: 'owner:fred@goog.co', label: 'fred'});
        assert.deepEqual(s[1], {text: 'owner:me'});
      })
      .then(() => element.fetchAccounts('owner', 'meme'))
      .then(s => {
        assert.notEqual(s[0], {text: 'owner:me'});
      });
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
    stubRestApi('getSuggestedProjects').callsFake(() =>
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

  test('Autocompletes accounts with no email', () => {
    stubRestApi('getSuggestedAccounts').callsFake(() =>
      Promise.resolve([{name: 'fred'}])
    );
    return element.fetchAccounts('owner', 'fr').then(s => {
      assert.deepEqual(s[0], {text: 'owner:"fred"', label: 'fred'});
    });
  });

  test('Autocompletes accounts with email', () => {
    stubRestApi('getSuggestedAccounts').callsFake(() =>
      Promise.resolve([{email: 'fred@goog.co' as EmailAddress}])
    );
    return element.fetchAccounts('owner', 'fr').then(s => {
      assert.deepEqual(s[0], {text: 'owner:fred@goog.co', label: ''});
    });
  });
});
