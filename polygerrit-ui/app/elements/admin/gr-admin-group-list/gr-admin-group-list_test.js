/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
import './gr-admin-group-list.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';
import 'lodash/lodash.js';
import {stubRestApi} from '../../../test/test-utils.js';

const basicFixture = fixtureFromElement('gr-admin-group-list');

let counter = 0;
const groupGenerator = () => {
  return {
    name: `test${++counter}`,
    id: '59b92f35489e62c80d1ab1bf0c2d17843038df8b',
    url: '#/admin/groups/uuid-59b92f35489e62c80d1ab1bf0c2d17843038df8b',
    options: {
      visible_to_all: false,
    },
    description: 'Gerrit Site Administrators',
    group_id: 1,
    owner: 'Administrators',
    owner_id: '7ca042f4d5847936fcb90ca91057673157fd06fc',
  };
};

suite('gr-admin-group-list tests', () => {
  let element;
  let groups;

  let value;

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('_computeGroupUrl', () => {
    let urlStub = sinon.stub(GerritNav, 'getUrlForGroup').callsFake(
        () => '/admin/groups/e2cd66f88a2db4d391ac068a92d987effbe872f5');

    let group = {
      id: 'e2cd66f88a2db4d391ac068a92d987effbe872f5',
    };
    assert.equal(element._computeGroupUrl(group),
        '/admin/groups/e2cd66f88a2db4d391ac068a92d987effbe872f5');

    urlStub.restore();

    urlStub = sinon.stub(GerritNav, 'getUrlForGroup').callsFake(
        () => '/admin/groups/user/test');

    group = {
      id: 'user%2Ftest',
    };
    assert.equal(element._computeGroupUrl(group),
        '/admin/groups/user/test');

    urlStub.restore();
  });

  suite('list with groups', () => {
    setup(async () => {
      groups = _.times(26, groupGenerator);
      stubRestApi('getGroups').returns(Promise.resolve(groups));
      element._paramsChanged(value);
      await flush();
    });

    test('test for test group in the list', () => {
      assert.equal(element._groups[1].name, '1');
      assert.equal(element._groups[1].options.visible_to_all, false);
    });

    test('_shownGroups', () => {
      assert.equal(element._shownGroups.length, 25);
    });

    test('_maybeOpenCreateOverlay', () => {
      const overlayOpen = sinon.stub(element.$.createOverlay, 'open');
      element._maybeOpenCreateOverlay();
      assert.isFalse(overlayOpen.called);
      const params = {};
      element._maybeOpenCreateOverlay(params);
      assert.isFalse(overlayOpen.called);
      params.openCreateModal = true;
      element._maybeOpenCreateOverlay(params);
      assert.isTrue(overlayOpen.called);
    });
  });

  suite('test with less then 25 groups', () => {
    setup(done => {
      groups = _.times(25, groupGenerator);
      stubRestApi('getGroups').returns(Promise.resolve(groups));
      element._paramsChanged(value).then(() => { flush(done); });
    });

    test('_shownGroups', () => {
      assert.equal(element._shownGroups.length, 25);
    });
  });

  suite('filter', () => {
    test('_paramsChanged', async () => {
      const getGroupsStub = stubRestApi('getGroups');
      getGroupsStub.returns(Promise.resolve(groups));
      const value = {
        filter: 'test',
        offset: 25,
      };
      await element._paramsChanged(value);
      assert.isTrue(getGroupsStub.lastCall.calledWithExactly('test', 25, 25));
    });
  });

  suite('loading', () => {
    test('correct contents are displayed', () => {
      assert.isTrue(element._loading);
      assert.equal(element.computeLoadingClass(element._loading), 'loading');
      assert.equal(getComputedStyle(element.$.loading).display, 'block');

      element._loading = false;
      element._groups = _.times(25, groupGenerator);

      flush();
      assert.equal(element.computeLoadingClass(element._loading), '');
      assert.equal(getComputedStyle(element.$.loading).display, 'none');
    });
  });

  suite('create new', () => {
    test('_handleCreateClicked called when create-click fired', () => {
      sinon.stub(element, '_handleCreateClicked');
      element.shadowRoot
          .querySelector('gr-list-view').dispatchEvent(
              new CustomEvent('create-clicked', {
                composed: true, bubbles: true,
              }));
      assert.isTrue(element._handleCreateClicked.called);
    });

    test('_handleCreateClicked opens modal', () => {
      const openStub = sinon.stub(element.$.createOverlay, 'open').returns(
          Promise.resolve());
      element._handleCreateClicked();
      assert.isTrue(openStub.called);
    });

    test('_handleCreateGroup called when confirm fired', () => {
      sinon.stub(element, '_handleCreateGroup');
      element.$.createDialog.dispatchEvent(
          new CustomEvent('confirm', {
            composed: true, bubbles: true,
          }));
      assert.isTrue(element._handleCreateGroup.called);
    });

    test('_handleCloseCreate called when cancel fired', () => {
      sinon.stub(element, '_handleCloseCreate');
      element.$.createDialog.dispatchEvent(
          new CustomEvent('cancel', {
            composed: true, bubbles: true,
          }));
      assert.isTrue(element._handleCloseCreate.called);
    });
  });
});

