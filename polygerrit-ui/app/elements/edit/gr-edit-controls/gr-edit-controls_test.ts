/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-edit-controls';
import {GrEditControls} from './gr-edit-controls';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {
  queryAll,
  stubRestApi,
  waitUntil,
  waitUntilVisible,
} from '../../../test/test-utils';
import {createChange, createRevision} from '../../../test/test-data-generators';
import {GrAutocomplete} from '../../shared/gr-autocomplete/gr-autocomplete';
import {
  CommitId,
  NumericChangeId,
  PatchSetNumber,
  RevisionPatchSetNum,
} from '../../../types/common';
import {RepoName} from '../../../api/rest-api';
import {queryAndAssert} from '../../../test/test-utils';
import {assert, fixture, html} from '@open-wc/testing';
import {GrButton} from '../../shared/gr-button/gr-button';
import '../../shared/gr-dialog/gr-dialog';
import {testResolver} from '../../../test/common-test-setup';
import {
  ChangeModel,
  changeModelToken,
} from '../../../models/change/change-model';
import {SinonStubbedMember} from 'sinon';
import {
  ChangeChildView,
  changeViewModelToken,
} from '../../../models/views/change';
import {GerritView} from '../../../services/router/router-model';

suite('gr-edit-controls tests', () => {
  let element: GrEditControls;

  let showDialogSpy: sinon.SinonSpy;
  let closeDialogSpy: sinon.SinonSpy;
  let hideDialogStub: sinon.SinonStub;
  let queryStub: sinon.SinonStub;
  let navigateResetStub: SinonStubbedMember<
    ChangeModel['navigateToChangeResetReload']
  >;

  setup(async () => {
    testResolver(changeViewModelToken).setState({
      view: GerritView.CHANGE,
      childView: ChangeChildView.OVERVIEW,
      changeNum: 42 as NumericChangeId,
      repo: 'gerrit' as RepoName,
    });

    element = await fixture<GrEditControls>(html`
      <gr-edit-controls></gr-edit-controls>
    `);
    element.change = createChange();
    element.patchNum = 1 as RevisionPatchSetNum;
    showDialogSpy = sinon.spy(element, 'showDialog');
    closeDialogSpy = sinon.spy(element, 'closeDialog');
    hideDialogStub = sinon.stub(element, 'hideAllDialogs');
    queryStub = stubRestApi('queryChangeFiles').returns(Promise.resolve([]));
    navigateResetStub = sinon.stub(
      testResolver(changeModelToken),
      'navigateToChangeResetReload'
    );
    await element.updateComplete;
  });

  test('render', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <gr-button
          aria-disabled="false"
          id="open"
          link=""
          role="button"
          tabindex="0"
        >
          Add/Open/Upload
        </gr-button>
        <gr-button
          aria-disabled="false"
          id="delete"
          link=""
          role="button"
          tabindex="0"
        >
          Delete
        </gr-button>
        <gr-button
          aria-disabled="false"
          id="rename"
          link=""
          role="button"
          tabindex="0"
        >
          Rename
        </gr-button>
        <gr-button
          aria-disabled="false"
          class="invisible"
          id="restore"
          link=""
          role="button"
          tabindex="0"
        >
          Restore
        </gr-button>
        <dialog id="modal" tabindex="-1">
          <gr-dialog
            class="dialog invisible"
            confirm-label="Confirm"
            confirm-on-enter=""
            disabled=""
            id="openDialog"
            role="dialog"
          >
            <div class="header" slot="header">
              Add a new file or open an existing file
            </div>
            <div class="main" slot="main">
              <gr-autocomplete
                placeholder="Enter an existing or new full file path."
              >
              </gr-autocomplete>
              <div id="dragDropArea">
                <p>Drag and drop your file here, or click to select</p>
                <input hidden="" id="fileUploadInput" type="file" />
              </div>
            </div>
          </gr-dialog>
          <gr-dialog
            class="dialog invisible"
            confirm-label="Delete"
            confirm-on-enter=""
            disabled=""
            id="deleteDialog"
            role="dialog"
          >
            <div class="header" slot="header">Delete a file from the repo</div>
            <div class="main" slot="main">
              <gr-autocomplete placeholder="Enter an existing full file path.">
              </gr-autocomplete>
            </div>
          </gr-dialog>
          <gr-dialog
            class="dialog invisible"
            confirm-label="Rename"
            confirm-on-enter=""
            disabled=""
            id="renameDialog"
            role="dialog"
          >
            <div class="header" slot="header">Rename a file in the repo</div>
            <div class="main" slot="main">
              <gr-autocomplete placeholder="Enter an existing full file path.">
              </gr-autocomplete>
              <iron-input id="newPathIronInput">
                <input id="newPathInput" placeholder="Enter the new path." />
              </iron-input>
            </div>
          </gr-dialog>
          <gr-dialog
            class="dialog invisible"
            confirm-label="Restore"
            confirm-on-enter=""
            id="restoreDialog"
            role="dialog"
          >
            <div class="header" slot="header">Restore this file?</div>
            <div class="main" slot="main">
              <iron-input>
                <input />
              </iron-input>
            </div>
          </gr-dialog>
        </dialog>
      `
    );
  });

  test('all actions exist', () => {
    assert.equal(
      queryAll<GrButton>(element, 'gr-button').length,
      element.actions.length
    );
  });

  suite('edit button CUJ', () => {
    let setUrlStub: sinon.SinonStub;
    let openAutoComplete: GrAutocomplete;

    setup(() => {
      setUrlStub = sinon.stub(testResolver(navigationToken), 'setUrl');
      openAutoComplete = queryAndAssert<GrAutocomplete>(
        element.openDialog,
        'gr-autocomplete'
      );
    });

    test('isValidPath', () => {
      assert.isFalse(element.isValidPath(''));
      assert.isFalse(element.isValidPath('test/'));
      assert.isFalse(element.isValidPath('/'));
      assert.isTrue(element.isValidPath('test/path.cpp'));
      assert.isTrue(element.isValidPath('test.js'));
    });

    test('open', async () => {
      assert.isFalse(hideDialogStub.called);
      queryAndAssert<GrButton>(element, '#open').click();
      element.patchNum = 1 as RevisionPatchSetNum;
      await showDialogSpy.lastCall.returnValue;
      assert.isTrue(hideDialogStub.called);
      assert.isTrue(element.openDialog!.disabled);
      assert.isFalse(queryStub.called);
      // Setup focused manually - in headless mode Chrome sometimes doesn't
      // setup focus. waitEventLoop() doesn't help.
      openAutoComplete.debounceWait = 10;
      openAutoComplete.focused = true;
      openAutoComplete.text = 'src/test.cpp';
      // Focus happens after updateComplete, so we first wait for it explicitly.
      await new Promise<void>(resolve => {
        openAutoComplete.addEventListener('focus', () => resolve());
      });
      await element.updateComplete;
      await openAutoComplete.latestSuggestionUpdateComplete;
      assert.isTrue(queryStub.called);
      await waitUntil(() => !element.openDialog!.disabled);
      queryAndAssert<GrButton>(
        element.openDialog,
        'gr-button[primary]'
      ).click();

      assert.isTrue(setUrlStub.called);
      assert.isTrue(closeDialogSpy.called);
    });

    test('cancel', async () => {
      queryAndAssert<GrButton>(element, '#open').click();
      await waitUntilVisible(element.modal!);
      assert.isTrue(element.openDialog!.disabled);
      openAutoComplete.text = 'src/test.cpp';
      await element.updateComplete;
      await waitUntil(() => !element.openDialog!.disabled);
      queryAndAssert<GrButton>(element.openDialog, 'gr-button').click();
      assert.isFalse(setUrlStub.called);
      await waitUntil(() => closeDialogSpy.called);
      assert.equal(element.path, '');
    });
  });

  suite('delete button CUJ', () => {
    let eventStub: sinon.SinonStub;
    let deleteStub: sinon.SinonStub;
    let deleteAutocomplete: GrAutocomplete;

    setup(() => {
      eventStub = sinon.stub(element, 'dispatchEvent');
      deleteStub = stubRestApi('deleteFileInChangeEdit');
      const deleteDialog = element.deleteDialog;
      deleteAutocomplete = queryAndAssert<GrAutocomplete>(
        deleteDialog,
        'gr-autocomplete'
      );
    });

    test('delete', async () => {
      deleteStub.returns(Promise.resolve({ok: true}));
      queryAndAssert<GrButton>(element, '#delete').click();
      await showDialogSpy.lastCall.returnValue;
      assert.isTrue(element.deleteDialog!.disabled);
      assert.isFalse(queryStub.called);
      // Setup focused manually - in headless mode Chrome sometimes doesn't
      // setup focus. waitEventLoop() doesn't help.
      deleteAutocomplete.debounceWait = 10;
      deleteAutocomplete.focused = true;
      deleteAutocomplete.text = 'src/test.cpp';
      // Focus happens after updateComplete, so we first wait for it explicitly.
      await new Promise<void>(resolve => {
        deleteAutocomplete.addEventListener('focus', () => resolve());
      });
      await element.updateComplete;
      await deleteAutocomplete.latestSuggestionUpdateComplete;
      assert.isTrue(queryStub.called);
      await waitUntil(() => !element.deleteDialog!.disabled);
      queryAndAssert<GrButton>(
        element.deleteDialog,
        'gr-button[primary]'
      ).click();
      await element.updateComplete;

      assert.isTrue(deleteStub.called);
      await deleteStub.lastCall.returnValue;
      assert.equal(element.path, '');
      assert.equal(navigateResetStub.callCount, 1);
      assert.isTrue(closeDialogSpy.called);
    });

    test('delete fails', async () => {
      deleteStub.returns(Promise.resolve({ok: false}));
      queryAndAssert<GrButton>(element, '#delete').click();
      await showDialogSpy.lastCall.returnValue;
      assert.isTrue(element.deleteDialog!.disabled);
      assert.isFalse(queryStub.called);
      // Setup focused manually - in headless mode Chrome sometimes doesn't
      // setup focus. waitEventLoop() doesn't help.
      deleteAutocomplete.debounceWait = 10;
      deleteAutocomplete.focused = true;
      deleteAutocomplete.text = 'src/test.cpp';
      // Focus happens after updateComplete, so we first wait for it explicitly.
      await new Promise<void>(resolve => {
        deleteAutocomplete.addEventListener('focus', () => resolve());
      });
      await element.updateComplete;
      await deleteAutocomplete.latestSuggestionUpdateComplete;
      assert.isTrue(queryStub.called);
      await waitUntil(() => !element.deleteDialog!.disabled);
      queryAndAssert<GrButton>(
        element.deleteDialog,
        'gr-button[primary]'
      ).click();
      await element.updateComplete;

      assert.isTrue(deleteStub.called);

      await deleteStub.lastCall.returnValue;
      assert.isFalse(eventStub.called);
      assert.isFalse(closeDialogSpy.called);
    });

    test('cancel', async () => {
      queryAndAssert<GrButton>(element, '#delete').click();
      await waitUntilVisible(element.modal!);
      assert.isTrue(element.deleteDialog!.disabled);
      queryAndAssert<GrAutocomplete>(
        element.deleteDialog,
        'gr-autocomplete'
      ).text = 'src/test.cpp';
      await element.updateComplete;
      await waitUntil(() => !element.deleteDialog!.disabled);
      queryAndAssert<GrButton>(element.deleteDialog, 'gr-button').click();
      assert.isFalse(eventStub.called);
      assert.isTrue(closeDialogSpy.called);
      await waitUntil(() => element.path === '');
    });
  });

  suite('rename button CUJ', () => {
    let eventStub: sinon.SinonStub;
    let renameStub: sinon.SinonStub;
    let renameAutocomplete: GrAutocomplete;

    setup(() => {
      eventStub = sinon.stub(element, 'dispatchEvent');
      renameStub = stubRestApi('renameFileInChangeEdit');
      const renameDialog = element.renameDialog;
      renameAutocomplete = queryAndAssert<GrAutocomplete>(
        renameDialog,
        'gr-autocomplete'
      );
    });

    test('rename', async () => {
      renameStub.returns(Promise.resolve({ok: true}));
      queryAndAssert<GrButton>(element, '#rename').click();
      await showDialogSpy.lastCall.returnValue;
      assert.isTrue(element.renameDialog!.disabled);
      assert.isFalse(queryStub.called);
      // Setup focused manually - in headless mode Chrome sometimes doesn't
      // setup focus. waitEventLoop() doesn't help.
      renameAutocomplete.focused = true;
      renameAutocomplete.text = 'src/test.cpp';
      // Focus happens after updateComplete, so we first wait for it explicitly.
      await new Promise<void>(resolve => {
        renameAutocomplete.addEventListener('focus', () => resolve());
      });
      await element.updateComplete;
      await renameAutocomplete.latestSuggestionUpdateComplete;
      assert.isTrue(queryStub.called);
      assert.isTrue(element.renameDialog!.disabled);

      element.newPathIronInput!.bindValue = 'src/test.newPath';
      await element.updateComplete;

      assert.isFalse(element.renameDialog!.disabled);
      queryAndAssert<GrButton>(
        element.renameDialog,
        'gr-button[primary]'
      ).click();
      await element.updateComplete;
      assert.isTrue(renameStub.called);

      await renameStub.lastCall.returnValue;
      assert.equal(element.path, '');
      assert.equal(navigateResetStub.callCount, 1);
      assert.isTrue(closeDialogSpy.called);
    });

    test('rename fails', async () => {
      renameStub.returns(Promise.resolve({ok: false}));
      queryAndAssert<GrButton>(element, '#rename').click();
      await showDialogSpy.lastCall.returnValue;
      assert.isTrue(element.renameDialog!.disabled);
      assert.isFalse(queryStub.called);
      // Setup focused manually - in headless mode Chrome sometimes doesn't
      // setup focus. waitEventLoop() doesn't help.
      renameAutocomplete.debounceWait = 10;
      renameAutocomplete.focused = true;
      renameAutocomplete.text = 'src/test.cpp';
      // Focus happens after updateComplete, so we first wait for it explicitly.
      await new Promise<void>(resolve => {
        renameAutocomplete.addEventListener('focus', () => resolve());
      });
      await element.updateComplete;
      await renameAutocomplete.latestSuggestionUpdateComplete;
      assert.isTrue(queryStub.called);
      assert.isTrue(element.renameDialog!.disabled);

      element.newPathIronInput!.bindValue = 'src/test.newPath';
      await element.updateComplete;

      assert.isFalse(element.renameDialog!.disabled);
      queryAndAssert<GrButton>(
        element.renameDialog,
        'gr-button[primary]'
      ).click();
      await element.updateComplete;

      assert.isTrue(renameStub.called);

      await renameStub.lastCall.returnValue;
      assert.isFalse(eventStub.called);
      assert.isFalse(closeDialogSpy.called);
    });

    test('cancel', async () => {
      queryAndAssert<GrButton>(element, '#rename').click();
      await waitUntilVisible(element.modal!);
      assert.isTrue(element.renameDialog!.disabled);
      queryAndAssert<GrAutocomplete>(
        element.renameDialog,
        'gr-autocomplete'
      ).text = 'src/test.cpp';
      element.newPathIronInput!.bindValue = 'src/test.newPath';
      await element.updateComplete;
      assert.isFalse(element.renameDialog!.disabled);
      queryAndAssert<GrButton>(element.renameDialog, 'gr-button').click();
      assert.isFalse(eventStub.called);
      assert.isTrue(closeDialogSpy.called);
      await waitUntil(() => element.path === '');
    });
  });

  suite('restore button CUJ', () => {
    let eventStub: sinon.SinonStub;
    let restoreStub: sinon.SinonStub;

    setup(() => {
      eventStub = sinon.stub(element, 'dispatchEvent');
      restoreStub = stubRestApi('restoreFileInChangeEdit');
    });

    test('restore hidden by default', () => {
      assert.isTrue(
        queryAndAssert(element, '#restore').classList.contains('invisible')
      );
    });

    test('restore', async () => {
      restoreStub.returns(Promise.resolve({ok: true}));
      element.path = 'src/test.cpp';
      queryAndAssert<GrButton>(element, '#restore').click();
      await waitUntilVisible(element.modal!);
      queryAndAssert<GrButton>(
        element.restoreDialog,
        'gr-button[primary]'
      ).click();
      await element.updateComplete;

      assert.isTrue(restoreStub.called);
      assert.equal(restoreStub.lastCall.args[1], 'src/test.cpp');
      return restoreStub.lastCall.returnValue.then(() => {
        assert.equal(element.path, '');
        assert.equal(navigateResetStub.callCount, 1);
        assert.isTrue(closeDialogSpy.called);
      });
    });

    test('restore fails', async () => {
      restoreStub.returns(Promise.resolve({ok: false}));
      element.path = 'src/test.cpp';
      queryAndAssert<GrButton>(element, '#restore').click();
      await waitUntilVisible(element.modal!);
      queryAndAssert<GrButton>(
        element.restoreDialog,
        'gr-button[primary]'
      ).click();
      await element.updateComplete;

      assert.isTrue(restoreStub.called);
      assert.equal(restoreStub.lastCall.args[1], 'src/test.cpp');
      return restoreStub.lastCall.returnValue.then(() => {
        assert.isFalse(eventStub.called);
        assert.isFalse(closeDialogSpy.called);
      });
    });

    test('cancel', async () => {
      element.path = 'src/test.cpp';
      queryAndAssert<GrButton>(element, '#restore').click();
      await waitUntilVisible(element.modal!);
      queryAndAssert<GrButton>(element.restoreDialog, 'gr-button').click();
      assert.isFalse(eventStub.called);
      assert.isTrue(closeDialogSpy.called);
      assert.equal(element.path, '');
    });
  });

  suite('save file upload', () => {
    let fileStub: sinon.SinonStub;

    setup(() => {
      fileStub = stubRestApi('saveFileUploadChangeEdit');
    });

    test('handleUploadConfirm', async () => {
      fileStub.returns(Promise.resolve({ok: true}));

      element.change = {
        ...createChange(),
        _number: 1 as NumericChangeId,
        project: 'project' as RepoName,
        revisions: {
          abcd: {
            ...createRevision(1),
            _number: 1 as PatchSetNumber,
          },
          efgh: {
            ...createRevision(2),
            _number: 2 as PatchSetNumber,
          },
        },
        current_revision: 'efgh' as CommitId,
      };

      element.handleUploadConfirm('test.php', 'base64');

      assert.isTrue(fileStub.calledOnce);
      assert.equal(fileStub.lastCall.args[0], 1);
      assert.equal(fileStub.lastCall.args[1], 'test.php');
      assert.equal(fileStub.lastCall.args[2], 'base64');
      await waitUntil(() => navigateResetStub.called);
      assert.equal(navigateResetStub.callCount, 1);
    });
  });

  test('openOpenDialog', async () => {
    element.openOpenDialog('test/path.cpp');
    assert.isFalse(element.openDialog!.hasAttribute('hidden'));
    await waitUntil(
      () =>
        queryAndAssert<GrAutocomplete>(element.openDialog, 'gr-autocomplete')
          .text === 'test/path.cpp'
    );
  });

  test('getDialogFromEvent', async () => {
    const spy = sinon.spy(element, 'getDialogFromEvent');
    element.addEventListener('click', element.getDialogFromEvent);

    element.openDialog!.click();
    await element.updateComplete;
    assert.equal(spy.lastCall.returnValue!.id, 'openDialog');

    element.deleteDialog!.click();
    await element.updateComplete;
    assert.equal(spy.lastCall.returnValue!.id, 'deleteDialog');

    queryAndAssert<GrAutocomplete>(
      element.deleteDialog,
      'gr-autocomplete'
    ).click();

    await element.updateComplete;
    assert.equal(spy.lastCall.returnValue!.id, 'deleteDialog');

    element.click();
    await element.updateComplete;
    assert.notOk(spy.lastCall.returnValue);
  });
});
