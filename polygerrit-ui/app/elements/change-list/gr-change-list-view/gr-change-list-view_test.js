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

import '../../../test/common-test-setup-karma.js';
import './gr-change-list-view.js';
import {page} from '../../../utils/page-wrapper-utils.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';
import 'lodash/lodash.js';
import {mockPromise, stubRestApi} from '../../../test/test-utils.js';

const basicFixture = fixtureFromElement('gr-change-list-view');

const CHANGE_ID = 'IcA3dAB3edAB9f60B8dcdA6ef71A75980e4B7127';
const COMMIT_HASH = '12345678';

suite('gr-change-list-view tests', () => {
  let element;

  setup(() => {
    stubRestApi('getLoggedIn').returns(Promise.resolve(false));
    stubRestApi('getChanges').returns(Promise.resolve([]));
    stubRestApi('getAccountDetails').returns(Promise.resolve({}));
    stubRestApi('getAccountStatus').returns(Promise.resolve({}));
    element = basicFixture.instantiate();
  });

  teardown(async () => {
    await flush();
  });

  test('_computePage', () => {
    assert.equal(element._computePage(0, 25), 1);
    assert.equal(element._computePage(50, 25), 3);
  });

  test('_limitFor', () => {
    const defaultLimit = 25;
    const _limitFor = q => element._limitFor(q, defaultLimit);
    assert.equal(_limitFor(''), defaultLimit);
    assert.equal(_limitFor('limit:10'), 10);
    assert.equal(_limitFor('xlimit:10'), defaultLimit);
    assert.equal(_limitFor('x(limit:10'), 10);
  });

  test('_computeNavLink', () => {
    const getUrlStub = sinon.stub(GerritNav, 'getUrlForSearchQuery')
        .returns('');
    const query = 'status:open';
    let offset = 0;
    let direction = 1;
    const changesPerPage = 5;

    element._computeNavLink(query, offset, direction, changesPerPage);
    assert.equal(getUrlStub.lastCall.args[1], 5);

    direction = -1;
    element._computeNavLink(query, offset, direction, changesPerPage);
    assert.equal(getUrlStub.lastCall.args[1], 0);

    offset = 5;
    direction = 1;
    element._computeNavLink(query, offset, direction, changesPerPage);
    assert.equal(getUrlStub.lastCall.args[1], 10);
  });

  test('_computePrevArrowClass', () => {
    let offset = 0;
    assert.equal(element._computePrevArrowClass(offset), 'hide');
    offset = 5;
    assert.equal(element._computePrevArrowClass(offset), '');
  });

  test('_computeNextArrowClass', () => {
    let changes = _.times(25, _.constant({_more_changes: true}));
    assert.equal(element._computeNextArrowClass(changes), '');
    changes = _.times(25, _.constant({}));
    assert.equal(element._computeNextArrowClass(changes), 'hide');
  });

  test('_computeNavClass', () => {
    let loading = true;
    assert.equal(element._computeNavClass(loading), 'hide');
    loading = false;
    assert.equal(element._computeNavClass(loading), 'hide');
    element._changes = [];
    assert.equal(element._computeNavClass(loading), 'hide');
    element._changes = _.times(5, _.constant({}));
    assert.equal(element._computeNavClass(loading), '');
  });

  test('_handleNextPage', () => {
    const showStub = sinon.stub(page, 'show');
    element._changesPerPage = 10;
    element.$.nextArrow.hidden = true;
    element._handleNextPage();
    assert.isFalse(showStub.called);
    element.$.nextArrow.hidden = false;
    element._handleNextPage();
    assert.isTrue(showStub.called);
  });

  test('_handlePreviousPage', () => {
    const showStub = sinon.stub(page, 'show');
    element._changesPerPage = 10;
    element.$.prevArrow.hidden = true;
    element._handlePreviousPage();
    assert.isFalse(showStub.called);
    element.$.prevArrow.hidden = false;
    element._handlePreviousPage();
    assert.isTrue(showStub.called);
  });

  test('_userId query', async () => {
    assert.isNull(element._userId);
    element._query = 'owner: foo@bar';
    element._changes = [{owner: {email: 'foo@bar'}}];
    await flush();
    assert.equal(element._userId, 'foo@bar');

    element._query = 'foo bar baz';
    element._changes = [{owner: {email: 'foo@bar'}}];
    assert.isNull(element._userId);
  });

  test('_userId query without email', async () => {
    assert.isNull(element._userId);
    element._query = 'owner: foo@bar';
    element._changes = [{owner: {}}];
    await flush();
    assert.isNull(element._userId);
  });

  test('_repo query', async () => {
    assert.isNull(element._repo);
    element._query = 'project: test-repo';
    element._changes = [{owner: {email: 'foo@bar'}, project: 'test-repo'}];
    await flush();
    assert.equal(element._repo, 'test-repo');
    element._query = 'foo bar baz';
    element._changes = [{owner: {email: 'foo@bar'}}];
    assert.isNull(element._repo);
  });

  test('_repo query with open status', async () => {
    assert.isNull(element._repo);
    element._query = 'project:test-repo status:open';
    element._changes = [{owner: {email: 'foo@bar'}, project: 'test-repo'}];
    await flush();
    assert.equal(element._repo, 'test-repo');
    element._query = 'foo bar baz';
    element._changes = [{owner: {email: 'foo@bar'}}];
    assert.isNull(element._repo);
  });

  suite('query based navigation', () => {
    setup(() => {
    });

    teardown(async () => {
      await flush();
      sinon.restore();
    });

    test('Searching for a change ID redirects to change', async () => {
      const change = {_number: 1};
      sinon.stub(element, '_getChanges')
          .returns(Promise.resolve([change]));
      const promise = mockPromise();
      sinon.stub(GerritNav, 'navigateToChange').callsFake(
          (url, opt) => {
            assert.equal(url, change);
            assert.isTrue(opt.redirect);
            promise.resolve();
          });

      element.params = {view: GerritNav.View.SEARCH, query: CHANGE_ID};
      await promise;
    });

    test('Searching for a change num redirects to change', async () => {
      const change = {_number: 1};
      sinon.stub(element, '_getChanges')
          .returns(Promise.resolve([change]));
      const promise = mockPromise();
      sinon.stub(GerritNav, 'navigateToChange').callsFake(
          (url, opt) => {
            assert.equal(url, change);
            assert.isTrue(opt.redirect);
            promise.resolve();
          });

      element.params = {view: GerritNav.View.SEARCH, query: '1'};
      await promise;
    });

    test('Commit hash redirects to change', async () => {
      const change = {_number: 1};
      sinon.stub(element, '_getChanges')
          .returns(Promise.resolve([change]));
      const promise = mockPromise();
      sinon.stub(GerritNav, 'navigateToChange').callsFake(
          (url, opt) => {
            assert.equal(url, change);
            assert.isTrue(opt.redirect);
            promise.resolve();
          });

      element.params = {view: GerritNav.View.SEARCH, query: COMMIT_HASH};
      await promise;
    });

    test('Searching for an invalid change ID searches', async () => {
      sinon.stub(element, '_getChanges')
          .returns(Promise.resolve([]));
      const stub = sinon.stub(GerritNav, 'navigateToChange');

      element.params = {view: GerritNav.View.SEARCH, query: CHANGE_ID};
      await flush();

      assert.isFalse(stub.called);
    });

    test('Change ID with multiple search results searches', async () => {
      sinon.stub(element, '_getChanges')
          .returns(Promise.resolve([{}, {}]));
      const stub = sinon.stub(GerritNav, 'navigateToChange');

      element.params = {view: GerritNav.View.SEARCH, query: CHANGE_ID};
      await flush();

      assert.isFalse(stub.called);
    });
  });
});

