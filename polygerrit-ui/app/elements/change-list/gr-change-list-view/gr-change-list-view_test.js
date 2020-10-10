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

const basicFixture = fixtureFromElement('gr-change-list-view');

const CHANGE_ID = 'IcA3dAB3edAB9f60B8dcdA6ef71A75980e4B7127';
const COMMIT_HASH = '12345678';

suite('gr-change-list-view tests', () => {
  let element;

  setup(() => {
    stub('gr-rest-api-interface', {
      getLoggedIn() { return Promise.resolve(false); },
      getChanges(num, query) {
        return Promise.resolve([]);
      },
      getAccountDetails() { return Promise.resolve({}); },
      getAccountStatus() { return Promise.resolve({}); },
    });
    element = basicFixture.instantiate();
  });

  teardown(done => {
    flush(() => {
      done();
    });
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

  test('_userId query', done => {
    assert.isNull(element._userId);
    element._query = 'owner: foo@bar';
    element._changes = [{owner: {email: 'foo@bar'}}];
    flush(() => {
      assert.equal(element._userId, 'foo@bar');

      element._query = 'foo bar baz';
      element._changes = [{owner: {email: 'foo@bar'}}];
      assert.isNull(element._userId);

      done();
    });
  });

  test('_userId query without email', done => {
    assert.isNull(element._userId);
    element._query = 'owner: foo@bar';
    element._changes = [{owner: {}}];
    flush(() => {
      assert.isNull(element._userId);
      done();
    });
  });

  test('_repo query', done => {
    assert.isNull(element._repo);
    element._query = 'project: test-repo';
    element._changes = [{owner: {email: 'foo@bar'}, project: 'test-repo'}];
    flush(() => {
      assert.equal(element._repo, 'test-repo');
      element._query = 'foo bar baz';
      element._changes = [{owner: {email: 'foo@bar'}}];
      assert.isNull(element._repo);
      done();
    });
  });

  test('_repo query with open status', done => {
    assert.isNull(element._repo);
    element._query = 'project:test-repo status:open';
    element._changes = [{owner: {email: 'foo@bar'}, project: 'test-repo'}];
    flush(() => {
      assert.equal(element._repo, 'test-repo');
      element._query = 'foo bar baz';
      element._changes = [{owner: {email: 'foo@bar'}}];
      assert.isNull(element._repo);
      done();
    });
  });

  suite('query based navigation', () => {
    setup(() => {
    });

    teardown(done => {
      flush(() => {
        sinon.restore();
        done();
      });
    });

    test('Searching for a change ID redirects to change', done => {
      const change = {_number: 1};
      sinon.stub(element, '_getChanges')
          .returns(Promise.resolve([change]));
      sinon.stub(GerritNav, 'navigateToChange').callsFake(
          (url, opt_patchNum, opt_basePatchNum, opt_isEdit, opt_redirect) => {
            assert.equal(url, change);
            assert.isTrue(opt_redirect);
            done();
          });

      element.params = {view: GerritNav.View.SEARCH, query: CHANGE_ID};
    });

    test('Searching for a change num redirects to change', done => {
      const change = {_number: 1};
      sinon.stub(element, '_getChanges')
          .returns(Promise.resolve([change]));
      sinon.stub(GerritNav, 'navigateToChange').callsFake(
          (url, opt_patchNum, opt_basePatchNum, opt_isEdit, opt_redirect) => {
            assert.equal(url, change);
            assert.isTrue(opt_redirect);
            done();
          });

      element.params = {view: GerritNav.View.SEARCH, query: '1'};
    });

    test('Commit hash redirects to change', done => {
      const change = {_number: 1};
      sinon.stub(element, '_getChanges')
          .returns(Promise.resolve([change]));
      sinon.stub(GerritNav, 'navigateToChange').callsFake(
          (url, opt_patchNum, opt_basePatchNum, opt_isEdit, opt_redirect) => {
            assert.equal(url, change);
            assert.isTrue(opt_redirect);
            done();
          });

      element.params = {view: GerritNav.View.SEARCH, query: COMMIT_HASH};
    });

    test('Searching for an invalid change ID searches', () => {
      sinon.stub(element, '_getChanges')
          .returns(Promise.resolve([]));
      const stub = sinon.stub(GerritNav, 'navigateToChange');

      element.params = {view: GerritNav.View.SEARCH, query: CHANGE_ID};
      flush();

      assert.isFalse(stub.called);
    });

    test('Change ID with multiple search results searches', () => {
      sinon.stub(element, '_getChanges')
          .returns(Promise.resolve([{}, {}]));
      const stub = sinon.stub(GerritNav, 'navigateToChange');

      element.params = {view: GerritNav.View.SEARCH, query: CHANGE_ID};
      flush();

      assert.isFalse(stub.called);
    });
  });
});

