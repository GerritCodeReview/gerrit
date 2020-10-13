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
import './gr-dashboard-view.js';
import {isHidden} from '../../../test/test-utils.js';
import {GerritNav, GerritView} from '../../core/gr-navigation/gr-navigation.js';
import {changeIsOpen} from '../../../utils/change-util.js';
import {ChangeStatus} from '../../../constants/constants.js';

const basicFixture = fixtureFromElement('gr-dashboard-view');

suite('gr-dashboard-view tests', () => {
  let element;

  let paramsChangedPromise;
  let getChangesStub;

  setup(() => {
    stub('gr-rest-api-interface', {
      getLoggedIn() { return Promise.resolve(false); },
      getAccountDetails() { return Promise.resolve({}); },
      getAccountStatus() { return Promise.resolve(false); },
    });
    element = basicFixture.instantiate();

    getChangesStub = sinon.stub(element.$.restAPI, 'getChanges').callsFake(
        (_, qs) => Promise.resolve(qs.map(() => [])));

    let resolver;
    paramsChangedPromise = new Promise(resolve => {
      resolver = resolve;
    });
    const paramsChanged = element._paramsChanged.bind(element);
    sinon.stub(element, '_paramsChanged').callsFake( params => {
      paramsChanged(params).then(() => resolver());
    });
  });

  suite('drafts banner functionality', () => {
    suite('_maybeShowDraftsBanner', () => {
      test('not dashboard/self', () => {
        element._maybeShowDraftsBanner({
          view: GerritView.DASHBOARD,
          user: 'notself',
        });
        assert.isFalse(element._showDraftsBanner);
      });

      test('no drafts at all', () => {
        element._results = [];
        element._maybeShowDraftsBanner({
          view: GerritView.DASHBOARD,
          user: 'self',
        });
        assert.isFalse(element._showDraftsBanner);
      });

      test('no drafts on open changes', () => {
        const openChange = {status: ChangeStatus.NEW};
        element._results = [{query: 'has:draft', results: [openChange]}];
        element._maybeShowDraftsBanner({
          view: GerritView.DASHBOARD,
          user: 'self',
        });
        assert.isFalse(element._showDraftsBanner);
      });

      test('no drafts on not open changes', () => {
        const notOpenChange = {status: '_'};
        element._results = [{query: 'has:draft', results: [notOpenChange]}];
        assert.isFalse(changeIsOpen(element._results[0].results[0]));
        element._maybeShowDraftsBanner({
          view: GerritView.DASHBOARD,
          user: 'self',
        });
        assert.isTrue(element._showDraftsBanner);
      });
    });

    test('_showDraftsBanner', () => {
      element._showDraftsBanner = false;
      flush();
      assert.isTrue(isHidden(element.shadowRoot
          .querySelector('.banner')));

      element._showDraftsBanner = true;
      flush();
      assert.isFalse(isHidden(element.shadowRoot
          .querySelector('.banner')));
    });

    test('delete tap opens dialog', () => {
      sinon.stub(element, '_handleOpenDeleteDialog');
      element._showDraftsBanner = true;
      flush();

      MockInteractions.tap(element.shadowRoot
          .querySelector('.banner .delete'));
      assert.isTrue(element._handleOpenDeleteDialog.called);
    });

    test('delete comments flow', async () => {
      sinon.spy(element, '_handleConfirmDelete');
      sinon.stub(element, '_reload');

      // Set up control over timing of when RPC resolves.
      let deleteDraftCommentsPromiseResolver;
      const deleteDraftCommentsPromise = new Promise(resolve => {
        deleteDraftCommentsPromiseResolver = resolve;
      });
      sinon.stub(element.$.restAPI, 'deleteDraftComments')
          .returns(deleteDraftCommentsPromise);

      // Open confirmation dialog and tap confirm button.
      await element.$.confirmDeleteOverlay.open();
      MockInteractions.tap(element.$.confirmDeleteDialog.$.confirm);
      flush();
      assert.isTrue(element.$.restAPI.deleteDraftComments
          .calledWithExactly('-is:open'));
      assert.isTrue(element.$.confirmDeleteDialog.disabled);
      assert.equal(element._reload.callCount, 0);

      // Verify state after RPC resolves.
      deleteDraftCommentsPromiseResolver([]);
      await deleteDraftCommentsPromise;
      assert.equal(element._reload.callCount, 1);
    });
  });

  test('_computeTitle', () => {
    assert.equal(element._computeTitle('self'), 'My Reviews');
    assert.equal(element._computeTitle('not self'), 'Dashboard for not self');
  });

  suite('_computeSectionCountLabel', () => {
    test('empty changes dont count label', () => {
      assert.equal('', element._computeSectionCountLabel([]));
    });

    test('1 change', () => {
      assert.equal('(1)',
          element._computeSectionCountLabel(['1']));
    });

    test('2 changes', () => {
      assert.equal('(2)',
          element._computeSectionCountLabel(['1', '2']));
    });

    test('1 change and more', () => {
      assert.equal('(1 and more)',
          element._computeSectionCountLabel([{_more_changes: true}]));
    });
  });

  suite('_isViewActive', () => {
    test('nothing happens when user param is falsy', () => {
      element.params = {};
      flush();
      assert.equal(getChangesStub.callCount, 0);

      element.params = {user: ''};
      flush();
      assert.equal(getChangesStub.callCount, 0);
    });

    test('content is refreshed when user param is updated', () => {
      element.params = {
        view: GerritNav.View.DASHBOARD,
        user: 'self',
      };
      return paramsChangedPromise.then(() => {
        assert.equal(getChangesStub.callCount, 1);
      });
    });
  });

  suite('selfOnly sections', () => {
    test('viewing self dashboard includes selfOnly sections', () => {
      element.params = {
        view: GerritNav.View.DASHBOARD,
        sections: [
          {query: '1'},
          {query: '2', selfOnly: true},
        ],
        user: 'self',
      };
      return paramsChangedPromise.then(() => {
        assert.isTrue(
            getChangesStub.calledWith(undefined,
                ['1', '2', 'owner:self limit:1']));
      });
    });

    test('viewing another user\'s dashboard omits selfOnly sections', () => {
      element.params = {
        view: GerritNav.View.DASHBOARD,
        sections: [
          {query: '1'},
          {query: '2', selfOnly: true},
        ],
        user: 'user',
      };
      return paramsChangedPromise.then(() => {
        assert.isTrue(getChangesStub.calledWith(undefined, ['1']));
      });
    });
  });

  test('suffixForDashboard is included in getChanges query', () => {
    element.params = {
      view: GerritNav.View.DASHBOARD,
      sections: [
        {query: '1'},
        {query: '2', suffixForDashboard: 'suffix'},
      ],
    };
    return paramsChangedPromise.then(() => {
      assert.isTrue(getChangesStub.calledOnce);
      assert.deepEqual(
          getChangesStub.firstCall.args, [undefined, ['1', '2 suffix']]);
    });
  });

  suite('_getProjectDashboard', () => {
    test('dashboard with foreach', () => {
      sinon.stub(element.$.restAPI, 'getDashboard')
          .callsFake( () => Promise.resolve({
            title: 'title',
            foreach: 'foreach for ${project}',
            sections: [
              {name: 'section 1', query: 'query 1'},
              {name: 'section 2', query: '${project} query 2'},
            ],
          }));
      return element._getProjectDashboard('project', '').then(dashboard => {
        assert.deepEqual(
            dashboard,
            {
              title: 'title',
              sections: [
                {name: 'section 1', query: 'query 1 foreach for project'},
                {
                  name: 'section 2',
                  query: 'project query 2 foreach for project',
                },
              ],
            });
      });
    });

    test('dashboard without foreach', () => {
      sinon.stub(element.$.restAPI, 'getDashboard').callsFake(
          () => Promise.resolve({
            title: 'title',
            sections: [
              {name: 'section 1', query: 'query 1'},
              {name: 'section 2', query: '${project} query 2'},
            ],
          }));
      return element._getProjectDashboard('project', '').then(dashboard => {
        assert.deepEqual(
            dashboard,
            {
              title: 'title',
              sections: [
                {name: 'section 1', query: 'query 1'},
                {name: 'section 2', query: 'project query 2'},
              ],
            });
      });
    });
  });

  test('hideIfEmpty sections', () => {
    const sections = [
      {name: 'test1', query: 'test1', hideIfEmpty: true},
      {name: 'test2', query: 'test2', hideIfEmpty: true},
    ];
    getChangesStub.restore();
    sinon.stub(element.$.restAPI, 'getChanges')
        .returns(Promise.resolve([[], ['nonempty']]));

    return element._fetchDashboardChanges({sections}, false).then(() => {
      assert.equal(element._results.length, 1);
      assert.equal(element._results[0].name, 'test2');
    });
  });

  test('preserve isOutgoing sections', () => {
    const sections = [
      {name: 'test1', query: 'test1', isOutgoing: true},
      {name: 'test2', query: 'test2'},
    ];
    getChangesStub.restore();
    sinon.stub(element.$.restAPI, 'getChanges')
        .returns(Promise.resolve([[], []]));

    return element._fetchDashboardChanges({sections}, false).then(() => {
      assert.equal(element._results.length, 2);
      assert.isTrue(element._results[0].isOutgoing);
      assert.isNotOk(element._results[1].isOutgoing);
    });
  });

  test('_showNewUserHelp', () => {
    element._loading = false;
    element._showNewUserHelp = false;
    flush();

    assert.equal(element.$.emptyOutgoing.textContent.trim(), 'No changes');
    assert.isNotOk(element.shadowRoot
        .querySelector('gr-create-change-help'));
    element._showNewUserHelp = true;
    flush();

    assert.notEqual(element.$.emptyOutgoing.textContent.trim(), 'No changes');
    assert.isOk(element.shadowRoot
        .querySelector('gr-create-change-help'));
  });

  test('_computeUserHeaderClass', () => {
    assert.equal(element._computeUserHeaderClass(undefined), 'hide');
    assert.equal(element._computeUserHeaderClass({}), 'hide');
    assert.equal(element._computeUserHeaderClass({user: 'self'}), 'hide');
    assert.equal(element._computeUserHeaderClass({user: 'user'}), 'hide');
    assert.equal(
        element._computeUserHeaderClass({
          view: GerritView.DASHBOARD,
          user: 'user',
        }),
        '');
    assert.equal(
        element._computeUserHeaderClass({project: 'p', user: 'user'}),
        'hide');
    assert.equal(
        element._computeUserHeaderClass({
          view: GerritView.DASHBOARD,
          project: 'p',
          user: 'user',
        }),
        'hide');
  });

  test('404 page', done => {
    const response = {status: 404};
    sinon.stub(element.$.restAPI, 'getDashboard').callsFake(
        async (project, dashboard, errFn) => {
          errFn(response);
        });
    element.addEventListener('page-error', e => {
      assert.strictEqual(e.detail.response, response);
      paramsChangedPromise.then(done);
    });
    element.params = {
      view: GerritNav.View.DASHBOARD,
      project: 'project',
      dashboard: 'dashboard',
    };
  });

  test('params change triggers dashboardDisplayed()', () => {
    sinon.stub(element.reporting, 'dashboardDisplayed');
    element.params = {
      view: GerritNav.View.DASHBOARD,
      project: 'project',
      dashboard: 'dashboard',
    };
    return paramsChangedPromise.then(() => {
      assert.isTrue(element.reporting.dashboardDisplayed.calledOnce);
    });
  });
});

