/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-edit-controls';
import {GrEditControls} from './gr-edit-controls';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {queryAll, stubRestApi, waitUntil} from '../../../test/test-utils';
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
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {fixture, html, assert} from '@open-wc/testing';
import {GrButton} from '../../shared/gr-button/gr-button';
import '../../shared/gr-dialog/gr-dialog';

suite('gr-edit-controls tests', () => {
  let element: GrEditControls;

  let showDialogSpy: sinon.SinonSpy;
  let closeDialogSpy: sinon.SinonSpy;
  let hideDialogStub: sinon.SinonStub;
  let queryStub: sinon.SinonStub;

  setup(async () => {
    element = await fixture<GrEditControls>(html`
      <gr-edit-controls></gr-edit-controls>
    `);
    element.change = createChange();
    element.patchNum = 1 as RevisionPatchSetNum;
    showDialogSpy = sinon.spy(element, 'showDialog');
    closeDialogSpy = sinon.spy(element, 'closeDialog');
    hideDialogStub = sinon.stub(element, 'hideAllDialogs');
    queryStub = stubRestApi('queryChangeFiles').returns(Promise.resolve([]));
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
        <gr-overlay
          aria-hidden="true"
          id="overlay"
          style="outline: none; display: none;"
          tabindex="-1"
          with-backdrop=""
        >
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
              <div contenteditable="true" id="dragDropArea">
                <p>Drag and drop a file here</p>
                <p>or</p>
                <p>
                  <iron-input>
                    <input
                      hidden=""
                      id="fileUploadInput"
                      multiple=""
                      type="file"
                    />
                  </iron-input>
                  <label for="fileUploadInput">
                    <gr-button
                      aria-disabled="false"
                      id="fileUploadBrowse"
                      role="button"
                      tabindex="0"
                    >
                      Browse
                    </gr-button>
                  </label>
                </p>
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
        </gr-overlay>
      `
    );
  });

  test('all actions exist', () => {
    // We take 1 away from the total found, due to an extra button being
    // added for the file uploads (browse).
    assert.equal(
      queryAll<GrButton>(element, 'gr-button').length - 1,
      element.actions.length
    );
  });

  suite('edit button CUJ', () => {
    let editDiffStub: sinon.SinonStub;
    let navStub: sinon.SinonStub;
    let openAutoComplete: GrAutocomplete;

    setup(() => {
      editDiffStub = sinon.stub(GerritNav, 'getEditUrlForDiff');
      navStub = sinon.stub(GerritNav, 'navigateToRelativeUrl');
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
      MockInteractions.tap(queryAndAssert(element, '#open'));
      element.patchNum = 1 as RevisionPatchSetNum;
      await showDialogSpy.lastCall.returnValue;
      assert.isTrue(hideDialogStub.called);
      assert.isTrue(element.openDialog!.disabled);
      assert.isFalse(queryStub.called);
      // Setup focused manually - in headless mode Chrome sometimes don't
      // setup focus. flush and/or flushAsynchronousOperations don't help
      openAutoComplete.focused = true;
      openAutoComplete.noDebounce = true;
      openAutoComplete.text = 'src/test.cpp';
      await element.updateComplete;
      assert.isTrue(queryStub.called);
      await waitUntil(() => !element.openDialog!.disabled);
      queryAndAssert<GrButton>(
        element.openDialog,
        'gr-button[primary]'
      ).click();
      await waitUntil(() => editDiffStub.called);

      assert.isTrue(navStub.called);
      assert.deepEqual(editDiffStub.lastCall.args, [
        element.change,
        'src/test.cpp',
        element.patchNum,
      ]);
      assert.isTrue(closeDialogSpy.called);
    });

    test('cancel', async () => {
      MockInteractions.tap(queryAndAssert(element, '#open'));
      return showDialogSpy.lastCall.returnValue.then(async () => {
        assert.isTrue(element.openDialog!.disabled);
        openAutoComplete.noDebounce = true;
        openAutoComplete.text = 'src/test.cpp';
        await element.updateComplete;
        await waitUntil(() => !element.openDialog!.disabled);
        MockInteractions.tap(
          queryAndAssert<GrButton>(element.openDialog, 'gr-button')
        );
        assert.isFalse(editDiffStub.called);
        assert.isFalse(navStub.called);
        await waitUntil(() => closeDialogSpy.called);
        assert.equal(element.path, '');
      });
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
      MockInteractions.tap(queryAndAssert(element, '#delete'));
      await showDialogSpy.lastCall.returnValue;
      assert.isTrue(element.deleteDialog!.disabled);
      assert.isFalse(queryStub.called);
      // Setup focused manually - in headless mode Chrome sometimes don't
      // setup focus. flush and/or flushAsynchronousOperations don't help
      deleteAutocomplete.focused = true;
      deleteAutocomplete.noDebounce = true;
      deleteAutocomplete.text = 'src/test.cpp';
      await element.updateComplete;
      assert.isTrue(queryStub.called);
      await waitUntil(() => !element.deleteDialog!.disabled);
      MockInteractions.tap(
        queryAndAssert(element.deleteDialog, 'gr-button[primary]')
      );
      await element.updateComplete;

      assert.isTrue(deleteStub.called);
      await deleteStub.lastCall.returnValue;
      assert.equal(element.path, '');
      assert.equal(eventStub.firstCall.args[0].type, 'reload');
      assert.isTrue(closeDialogSpy.called);
    });

    test('delete fails', async () => {
      deleteStub.returns(Promise.resolve({ok: false}));
      MockInteractions.tap(queryAndAssert(element, '#delete'));
      await showDialogSpy.lastCall.returnValue;
      assert.isTrue(element.deleteDialog!.disabled);
      assert.isFalse(queryStub.called);
      // Setup focused manually - in headless mode Chrome sometimes don't
      // setup focus. flush and/or flushAsynchronousOperations don't help
      deleteAutocomplete.focused = true;
      deleteAutocomplete.noDebounce = true;
      deleteAutocomplete.text = 'src/test.cpp';
      await element.updateComplete;
      assert.isTrue(queryStub.called);
      await waitUntil(() => !element.deleteDialog!.disabled);
      MockInteractions.tap(
        queryAndAssert(element.deleteDialog, 'gr-button[primary]')
      );
      await element.updateComplete;

      assert.isTrue(deleteStub.called);

      await deleteStub.lastCall.returnValue;
      assert.isFalse(eventStub.called);
      assert.isFalse(closeDialogSpy.called);
    });

    test('cancel', () => {
      MockInteractions.tap(queryAndAssert(element, '#delete'));
      return showDialogSpy.lastCall.returnValue.then(async () => {
        assert.isTrue(element.deleteDialog!.disabled);
        queryAndAssert<GrAutocomplete>(
          element.deleteDialog,
          'gr-autocomplete'
        ).text = 'src/test.cpp';
        await element.updateComplete;
        await waitUntil(() => !element.deleteDialog!.disabled);
        MockInteractions.tap(queryAndAssert(element.deleteDialog, 'gr-button'));
        assert.isFalse(eventStub.called);
        assert.isTrue(closeDialogSpy.called);
        await waitUntil(() => element.path === '');
      });
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
      MockInteractions.tap(queryAndAssert(element, '#rename'));
      await showDialogSpy.lastCall.returnValue;
      assert.isTrue(element.renameDialog!.disabled);
      assert.isFalse(queryStub.called);
      // Setup focused manually - in headless mode Chrome sometimes don't
      // setup focus. flush and/or flushAsynchronousOperations don't help
      renameAutocomplete.focused = true;
      renameAutocomplete.noDebounce = true;
      renameAutocomplete.text = 'src/test.cpp';
      await element.updateComplete;
      assert.isTrue(queryStub.called);
      assert.isTrue(element.renameDialog!.disabled);

      element.newPathIronInput!.bindValue = 'src/test.newPath';
      await element.updateComplete;

      assert.isFalse(element.renameDialog!.disabled);
      MockInteractions.tap(
        queryAndAssert(element.renameDialog, 'gr-button[primary]')
      );
      await element.updateComplete;
      assert.isTrue(renameStub.called);

      await renameStub.lastCall.returnValue;
      assert.equal(element.path, '');
      assert.equal(eventStub.firstCall.args[0].type, 'reload');
      assert.isTrue(closeDialogSpy.called);
    });

    test('rename fails', async () => {
      renameStub.returns(Promise.resolve({ok: false}));
      MockInteractions.tap(queryAndAssert(element, '#rename'));
      await showDialogSpy.lastCall.returnValue;
      assert.isTrue(element.renameDialog!.disabled);
      assert.isFalse(queryStub.called);
      // Setup focused manually - in headless mode Chrome sometimes don't
      // setup focus. flush and/or flushAsynchronousOperations don't help
      renameAutocomplete.focused = true;
      renameAutocomplete.noDebounce = true;
      renameAutocomplete.text = 'src/test.cpp';
      await element.updateComplete;
      assert.isTrue(queryStub.called);
      assert.isTrue(element.renameDialog!.disabled);

      element.newPathIronInput!.bindValue = 'src/test.newPath';
      await element.updateComplete;

      assert.isFalse(element.renameDialog!.disabled);
      MockInteractions.tap(
        queryAndAssert(element.renameDialog, 'gr-button[primary]')
      );
      await element.updateComplete;

      assert.isTrue(renameStub.called);

      await renameStub.lastCall.returnValue;
      assert.isFalse(eventStub.called);
      assert.isFalse(closeDialogSpy.called);
    });

    test('cancel', () => {
      MockInteractions.tap(queryAndAssert(element, '#rename'));
      return showDialogSpy.lastCall.returnValue.then(async () => {
        assert.isTrue(element.renameDialog!.disabled);
        queryAndAssert<GrAutocomplete>(
          element.renameDialog,
          'gr-autocomplete'
        ).text = 'src/test.cpp';
        element.newPathIronInput!.bindValue = 'src/test.newPath';
        await element.updateComplete;
        assert.isFalse(element.renameDialog!.disabled);
        MockInteractions.tap(queryAndAssert(element.renameDialog, 'gr-button'));
        assert.isFalse(eventStub.called);
        assert.isTrue(closeDialogSpy.called);
        await waitUntil(() => element.path === '');
      });
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
        queryAndAssert(element, '#restore').classList.contains('invisible')!
      );
    });

    test('restore', () => {
      restoreStub.returns(Promise.resolve({ok: true}));
      element.path = 'src/test.cpp';
      MockInteractions.tap(queryAndAssert(element, '#restore'));
      return showDialogSpy.lastCall.returnValue.then(async () => {
        MockInteractions.tap(
          queryAndAssert(element.restoreDialog, 'gr-button[primary]')
        );
        await element.updateComplete;

        assert.isTrue(restoreStub.called);
        assert.equal(restoreStub.lastCall.args[1], 'src/test.cpp');
        return restoreStub.lastCall.returnValue.then(() => {
          assert.equal(element.path, '');
          assert.equal(eventStub.firstCall.args[0].type, 'reload');
          assert.isTrue(closeDialogSpy.called);
        });
      });
    });

    test('restore fails', () => {
      restoreStub.returns(Promise.resolve({ok: false}));
      element.path = 'src/test.cpp';
      MockInteractions.tap(queryAndAssert(element, '#restore'));
      return showDialogSpy.lastCall.returnValue.then(async () => {
        MockInteractions.tap(
          queryAndAssert(element.restoreDialog, 'gr-button[primary]')
        );
        await element.updateComplete;

        assert.isTrue(restoreStub.called);
        assert.equal(restoreStub.lastCall.args[1], 'src/test.cpp');
        return restoreStub.lastCall.returnValue.then(() => {
          assert.isFalse(eventStub.called);
          assert.isFalse(closeDialogSpy.called);
        });
      });
    });

    test('cancel', () => {
      element.path = 'src/test.cpp';
      MockInteractions.tap(queryAndAssert(element, '#restore'));
      return showDialogSpy.lastCall.returnValue.then(() => {
        MockInteractions.tap(
          queryAndAssert(element.restoreDialog, 'gr-button')
        );
        assert.isFalse(eventStub.called);
        assert.isTrue(closeDialogSpy.called);
        assert.equal(element.path, '');
      });
    });
  });

  suite('save file upload', () => {
    let navStub: sinon.SinonStub;
    let fileStub: sinon.SinonStub;

    setup(() => {
      navStub = sinon.stub(GerritNav, 'navigateToChange');
      fileStub = stubRestApi('saveFileUploadChangeEdit');
    });

    test('handleUploadConfirm', () => {
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

      element.handleUploadConfirm('test.php', 'base64').then(() => {
        assert.isTrue(navStub.calledWithExactly(1 as NumericChangeId));
      });
    });
  });

  test('openOpenDialog', async () => {
    await element.openOpenDialog('test/path.cpp');
    assert.isFalse(element.openDialog!.hasAttribute('hidden'));
    assert.equal(
      queryAndAssert<GrAutocomplete>(element.openDialog, 'gr-autocomplete')
        .text,
      'test/path.cpp'
    );
  });

  test('getDialogFromEvent', async () => {
    const spy = sinon.spy(element, 'getDialogFromEvent');
    element.addEventListener('tap', element.getDialogFromEvent);

    MockInteractions.tap(element.openDialog!);
    await element.updateComplete;
    assert.equal(spy.lastCall.returnValue!.id, 'openDialog');

    MockInteractions.tap(element.deleteDialog!);
    await element.updateComplete;
    assert.equal(spy.lastCall.returnValue!.id, 'deleteDialog');

    MockInteractions.tap(
      queryAndAssert<GrAutocomplete>(element.deleteDialog, 'gr-autocomplete')
    );
    await element.updateComplete;
    assert.equal(spy.lastCall.returnValue!.id, 'deleteDialog');

    MockInteractions.tap(element);
    await element.updateComplete;
    assert.notOk(spy.lastCall.returnValue);
  });
});
