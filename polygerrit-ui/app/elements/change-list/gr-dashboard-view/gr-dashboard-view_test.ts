/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-dashboard-view';
import {GrDashboardView} from './gr-dashboard-view';
import {GerritView} from '../../../services/router/router-model';
import {changeIsOpen} from '../../../utils/change-util';
import {ChangeStatus} from '../../../constants/constants';
import {
  createAccountDetailWithId,
  createChange,
} from '../../../test/test-data-generators';
import {
  addListenerForTest,
  stubReporting,
  stubRestApi,
  mockPromise,
  queryAndAssert,
  query,
  stubFlags,
  waitUntil,
} from '../../../test/test-utils';
import {
  ChangeInfoId,
  DashboardId,
  RepoName,
  Timestamp,
} from '../../../types/common';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import {GrCreateChangeHelp} from '../gr-create-change-help/gr-create-change-help';
import {PageErrorEvent} from '../../../types/events';
import {fixture, html, assert} from '@open-wc/testing';
import {SinonStubbedMember} from 'sinon';
import {RestApiService} from '../../../services/gr-rest-api/gr-rest-api';
import {GrButton} from '../../shared/gr-button/gr-button';

suite('gr-dashboard-view tests', () => {
  let element: GrDashboardView;

  let getChangesStub: SinonStubbedMember<
    RestApiService['getChangesForMultipleQueries']
  >;

  setup(async () => {
    getChangesStub = stubRestApi('getChangesForMultipleQueries');
    stubRestApi('getLoggedIn').returns(Promise.resolve(false));
    stubRestApi('getAccountDetails').returns(
      Promise.resolve({
        registered_on: '2015-03-12 18:32:08.000000000' as Timestamp,
      })
    );

    element = await fixture<GrDashboardView>(html`
      <gr-dashboard-view></gr-dashboard-view>
    `);

    await element.updateComplete;
  });

  test('render', async () => {
    element.viewState = {
      view: GerritView.DASHBOARD,
      user: 'self',
      sections: [
        {name: 'test1', query: 'test1', hideIfEmpty: true},
        {name: 'test2', query: 'test2', hideIfEmpty: true},
      ],
    };
    getChangesStub.returns(Promise.resolve([[createChange()]]));
    await element.reload();
    element.loading = false;
    stubFlags('isEnabled').returns(true);
    element.requestUpdate();
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* prettier-ignore */ /* HTML */ `
        <div class="loading" hidden="">Loading...</div>
        <div>
          <h1 class="assistive-tech-only">Dashboard</h1>
          <gr-change-list showstar="">
            <div id="emptyOutgoing" slot="outgoing-slot">No changes</div>
            <div id="emptyYourTurn" slot="your-turn-slot">
              <span> No changes need your attention &nbsp🎉 </span>
            </div>
          </gr-change-list>
        </div>
        <dialog
          id="confirmDeleteModal"
          tabindex="-1"
        >
          <gr-dialog
            confirm-label="Delete"
            id="confirmDeleteDialog"
            role="dialog"
          >
            <div class="header" slot="header">Delete comments</div>
            <div class="main" slot="main">
              Are you sure you want to delete all your draft comments in closed
            changes? This action cannot be undone.
            </div>
          </gr-dialog>
        </dialog>
        <gr-create-destination-dialog id="destinationDialog">
        </gr-create-destination-dialog>
        <gr-create-commands-dialog id="commandsDialog">
        </gr-create-commands-dialog>
      `
    );
  });

  suite('bulk actions', () => {
    setup(async () => {
      element.viewState = {
        view: GerritView.DASHBOARD,
        user: 'user',
        sections: [
          {name: 'test1', query: 'test1', hideIfEmpty: true},
          {name: 'test2', query: 'test2', hideIfEmpty: true},
        ],
      };
      getChangesStub.returns(Promise.resolve([[createChange()]]));
      stubFlags('isEnabled').returns(true);
      await element.reload();
      element.loading = false;
      element.requestUpdate();
      await element.updateComplete;
    });

    test('checkboxes remain checked after soft reload', async () => {
      const checkbox = queryAndAssert<HTMLInputElement>(
        query(
          query(query(element, 'gr-change-list'), 'gr-change-list-section'),
          'gr-change-list-item'
        ),
        '.selection > label > input'
      );
      checkbox.click();
      await waitUntil(() => checkbox.checked);

      getChangesStub.restore();
      getChangesStub.returns(Promise.resolve([[createChange()]]));

      await element.reload();
      await element.updateComplete;
      assert.isTrue(checkbox.checked);
    });
  });

  suite('drafts banner functionality', () => {
    setup(async () => {
      element.viewState = {
        view: GerritView.DASHBOARD,
        user: 'self',
        sections: [
          {name: 'test1', query: 'test1', hideIfEmpty: true},
          {name: 'test2', query: 'test2', hideIfEmpty: true},
        ],
      };
    });

    suite('maybeShowDraftsBanner', () => {
      test('not dashboard/self', () => {
        element.viewState = {
          view: GerritView.DASHBOARD,
          user: 'notself',
          dashboard: '' as DashboardId,
        };
        element.maybeShowDraftsBanner();
        assert.isFalse(element.showDraftsBanner);
      });

      test('no drafts at all', () => {
        element.results = [];
        element.viewState = {
          view: GerritView.DASHBOARD,
          user: 'self',
          dashboard: '' as DashboardId,
        };
        element.maybeShowDraftsBanner();
        assert.isFalse(element.showDraftsBanner);
      });

      test('no drafts on open changes', () => {
        const openChange = {...createChange(), status: ChangeStatus.NEW};
        element.results = [
          {countLabel: '', name: '', query: 'has:draft', results: [openChange]},
        ];
        element.viewState = {
          view: GerritView.DASHBOARD,
          user: 'self',
          dashboard: '' as DashboardId,
        };
        element.maybeShowDraftsBanner();
        assert.isFalse(element.showDraftsBanner);
      });

      test('no drafts on not open changes', () => {
        const notOpenChange = {...createChange(), status: '_' as ChangeStatus};
        element.results = [
          {
            name: '',
            countLabel: '',
            query: 'has:draft',
            results: [notOpenChange],
          },
        ];
        assert.isFalse(changeIsOpen(element.results[0].results[0]));
        element.viewState = {
          view: GerritView.DASHBOARD,
          user: 'self',
          dashboard: '' as DashboardId,
        };
        element.maybeShowDraftsBanner();
        assert.isTrue(element.showDraftsBanner);
      });
    });

    test('showDraftsBanner', async () => {
      element.showDraftsBanner = false;
      await element.updateComplete;
      assert.isNotOk(query(element, '.banner'));

      element.showDraftsBanner = true;
      await element.updateComplete;
      assert.isOk(query(element, '.banner'));
    });

    test('delete tap opens dialog', async () => {
      const handleOpenDeleteDialogStub = sinon.stub(
        element,
        'handleOpenDeleteDialog'
      );
      element.showDraftsBanner = true;
      await element.updateComplete;

      queryAndAssert<GrButton>(element, '.banner .delete').click();
      assert.isTrue(handleOpenDeleteDialogStub.called);
    });

    test('delete comments flow', async () => {
      sinon.spy(element, 'handleConfirmDelete');
      const reloadStub = sinon.stub(element, 'reload');

      // Set up control over timing of when RPC resolves.
      let deleteDraftCommentsPromiseResolver: (
        value: Response | PromiseLike<Response>
      ) => void;
      const deleteDraftCommentsPromise: Promise<Response> = new Promise(
        resolve => {
          deleteDraftCommentsPromiseResolver = resolve;
          return Promise.resolve(new Response());
        }
      );

      const deleteStub = stubRestApi('deleteDraftComments').returns(
        deleteDraftCommentsPromise
      );

      // Open confirmation dialog and tap confirm button.
      const modal = queryAndAssert<HTMLDialogElement>(
        element,
        '#confirmDeleteModal'
      );
      modal.showModal();
      const dialog = queryAndAssert<GrDialog>(modal, '#confirmDeleteDialog');
      await waitUntil(() => !!dialog.confirmButton);
      dialog.confirmButton!.click();
      await element.updateComplete;
      assert.isTrue(deleteStub.calledWithExactly('-is:open'));
      assert.isTrue(
        queryAndAssert<GrDialog>(element, '#confirmDeleteDialog').disabled
      );
      assert.equal(reloadStub.callCount, 0);

      // Verify state after RPC resolves.
      // We have to put this in setTimeout otherwise typescript fails with
      // variable is used before assigned.
      setTimeout(() => deleteDraftCommentsPromiseResolver(new Response()), 0);
      await deleteDraftCommentsPromise;
      assert.equal(reloadStub.callCount, 1);
    });
  });

  test('computeTitle', () => {
    assert.equal(element.computeTitle('self'), 'My Reviews');
    assert.equal(element.computeTitle('not self'), 'Dashboard for not self');
  });

  suite('computeSectionCountLabel', () => {
    test('empty changes dont count label', () => {
      assert.equal('', element.computeSectionCountLabel([]));
    });

    test('1 change', () => {
      assert.equal('(1)', element.computeSectionCountLabel([createChange()]));
    });

    test('2 changes', () => {
      assert.equal(
        '(2)',
        element.computeSectionCountLabel([createChange(), createChange()])
      );
    });

    test('1 change and more', () => {
      assert.equal(
        '(1 and more)',
        element.computeSectionCountLabel([
          {...createChange(), _more_changes: true},
        ])
      );
    });
  });

  suite('selfOnly sections', () => {
    test('viewing self dashboard includes selfOnly sections', async () => {
      element.account = undefined;
      element.viewState = {
        view: GerritView.DASHBOARD,
        user: 'self',
        dashboard: '' as DashboardId,
        sections: [
          {name: '', query: '1'},
          {name: '', query: '2', selfOnly: true},
        ],
      };
      await element.reload();
      assert.isTrue(getChangesStub.calledWith(undefined, ['1', '2']));
    });

    test('viewing dashboard when logged in includes owner:self query', async () => {
      element.account = createAccountDetailWithId(1);
      element.viewState = {
        view: GerritView.DASHBOARD,
        user: 'self',
        dashboard: '' as DashboardId,
        sections: [
          {name: '', query: '1'},
          {name: '', query: '2', selfOnly: true},
        ],
      };
      await element.reload();
      assert.isTrue(
        getChangesStub.calledWith(undefined, ['1', '2', 'owner:self limit:1'])
      );
    });

    test("viewing another user's dashboard omits selfOnly sections", async () => {
      element.viewState = {
        view: GerritView.DASHBOARD,
        user: 'user',
        dashboard: '' as DashboardId,
        sections: [
          {name: '', query: '1'},
          {name: '', query: '2', selfOnly: true},
        ],
      };
      await element.reload();
      assert.isTrue(getChangesStub.calledWith(undefined, ['1']));
    });
  });

  test('suffixForDashboard is included in getChanges query', async () => {
    element.viewState = {
      view: GerritView.DASHBOARD,
      dashboard: '' as DashboardId,
      sections: [
        {name: '', query: '1'},
        {name: '', query: '2', suffixForDashboard: 'suffix'},
      ],
    };
    await element.reload();
    assert.isTrue(getChangesStub.calledWith(undefined, ['1', '2 suffix']));
  });

  suite('getProjectDashboard', () => {
    test('dashboard with foreach', async () => {
      stubRestApi('getDashboard').callsFake(() =>
        Promise.resolve({
          id: '' as DashboardId,
          project: 'project' as RepoName,
          defining_project: '' as RepoName,
          ref: '',
          path: '',
          url: '',
          title: 'title',
          foreach: 'foreach for ${project}',
          sections: [
            {name: 'section 1', query: 'query 1'},
            {name: 'section 2', query: '${project} query 2'},
          ],
        })
      );
      const dashboard = await element.getRepositoryDashboard(
        'project' as RepoName,
        '' as DashboardId
      );
      assert.deepEqual(dashboard, {
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

    test('dashboard without foreach', async () => {
      stubRestApi('getDashboard').callsFake(() =>
        Promise.resolve({
          id: '' as DashboardId,
          project: 'project' as RepoName,
          defining_project: '' as RepoName,
          ref: '',
          path: '',
          url: '',
          title: 'title',
          sections: [
            {name: 'section 1', query: 'query 1'},
            {name: 'section 2', query: '${project} query 2'},
          ],
        })
      );
      const dashboard = await element.getRepositoryDashboard(
        'project' as RepoName,
        '' as DashboardId
      );
      assert.deepEqual(dashboard, {
        title: 'title',
        sections: [
          {name: 'section 1', query: 'query 1'},
          {name: 'section 2', query: 'project query 2'},
        ],
      });
    });
  });

  test('hideIfEmpty sections', async () => {
    const sections = [
      {name: 'test1', query: 'test1', hideIfEmpty: true},
      {name: 'test2', query: 'test2', hideIfEmpty: true},
    ];
    getChangesStub.returns(Promise.resolve([[createChange()]]));

    await element.fetchDashboardChanges({sections}, false);
    assert.equal(element.results!.length, 1);
    assert.equal(element.results![0].name, 'test1');
  });

  test('sets slot name to section name if custom state is requested', async () => {
    const sections = [
      {name: 'Outgoing reviews', query: 'test1'},
      {name: 'test2', query: 'test2'},
    ];
    getChangesStub.returns(Promise.resolve([[], []]));

    await element.fetchDashboardChanges({sections}, false);
    assert.equal(element.results!.length, 2);
    assert.equal(element.results![0].emptyStateSlotName, 'outgoing-slot');
    assert.isNotOk(element.results![1].emptyStateSlotName);
  });

  test('toggling star will update change everywhere', async () => {
    // It is important that the same change is represented by multiple objects
    // and all are updated.
    const change = {...createChange(), id: '5' as ChangeInfoId, starred: false};
    const sameChange = {
      ...createChange(),
      id: '5' as ChangeInfoId,
      starred: false,
    };
    const differentChange = {
      ...createChange(),
      id: '4' as ChangeInfoId,
      starred: false,
    };
    element.results = [
      {name: '', countLabel: '', query: 'has:draft', results: [change]},
      {
        name: '',
        countLabel: '',
        query: 'is:open',
        results: [sameChange, differentChange],
      },
    ];

    await element.handleToggleStar(
      new CustomEvent('toggle-star', {
        detail: {
          change,
          starred: true,
        },
      })
    );

    assert.isTrue(change.starred);
    assert.isTrue(sameChange.starred);
    assert.isFalse(differentChange.starred);
  });

  test('showNewUserHelp', async () => {
    element.viewState = {
      view: GerritView.DASHBOARD,
    };
    element.loading = false;
    element.showNewUserHelp = false;
    await element.updateComplete;

    assert.equal(
      queryAndAssert<HTMLDivElement>(
        element,
        '#emptyOutgoing'
      ).textContent!.trim(),
      'No changes'
    );
    query<GrCreateChangeHelp>(element, 'gr-create-change-help');
    assert.isNotOk(query<GrCreateChangeHelp>(element, 'gr-create-change-help'));
    element.showNewUserHelp = true;
    await element.updateComplete;

    assert.notEqual(
      queryAndAssert<HTMLDivElement>(
        element,
        '#emptyOutgoing'
      ).textContent!.trim(),
      'No changes'
    );
    assert.isOk(query<GrCreateChangeHelp>(element, 'gr-create-change-help'));
  });

  test('gr-user-header', async () => {
    element.viewState = undefined;
    await element.updateComplete;
    assert.isNotOk(query(element, 'gr-user-header'));

    element.viewState = {
      view: GerritView.DASHBOARD,
      dashboard: '' as DashboardId,
      user: 'self',
    };
    await element.updateComplete;
    assert.isNotOk(query(element, 'gr-user-header'));

    element.loading = false;
    element.viewState = {
      view: GerritView.DASHBOARD,
      dashboard: '' as DashboardId,
      user: 'user',
    };
    await element.updateComplete;
    assert.isOk(query(element, 'gr-user-header'));

    element.viewState = {
      view: GerritView.DASHBOARD,
      dashboard: '' as DashboardId,
      project: 'p' as RepoName,
      user: 'user',
    };
    await element.updateComplete;
    assert.isNotOk(query(element, 'gr-user-header'));
  });

  test('404 page', async () => {
    const response = {...new Response(), status: 404};
    stubRestApi('getDashboard').callsFake(
      async (_project, _dashboard, errFn) => {
        if (errFn !== undefined) {
          errFn(response);
        }
        return Promise.resolve(undefined);
      }
    );
    const promise = mockPromise();
    addListenerForTest(document, 'page-error', e => {
      assert.strictEqual((e as PageErrorEvent).detail.response, response);
      promise.resolve();
    });
    element.viewState = {
      view: GerritView.DASHBOARD,
      dashboard: 'dashboard' as DashboardId,
      project: 'project' as RepoName,
      user: '',
    };
    await Promise.all([element.reload(), promise]);
  });

  test('viewState change triggers dashboardDisplayed()', async () => {
    stubRestApi('getDashboard').returns(
      Promise.resolve({
        id: '' as DashboardId,
        project: 'project' as RepoName,
        defining_project: '' as RepoName,
        ref: '',
        path: '',
        url: '',
        title: 'title',
        foreach: 'foreach for ${project}',
        sections: [],
      })
    );
    getChangesStub.returns(Promise.resolve([]));
    const dashboardDisplayedStub = stubReporting('dashboardDisplayed');
    element.viewState = {
      view: GerritView.DASHBOARD,
      dashboard: 'dashboard' as DashboardId,
      project: 'project' as RepoName,
      user: '',
    };
    await element.reload();
    assert.isTrue(dashboardDisplayedStub.calledOnce);
  });
});
