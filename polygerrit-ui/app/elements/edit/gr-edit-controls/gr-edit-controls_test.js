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
import './gr-edit-controls.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';
import {stubRestApi} from '../../../test/test-utils.js';

const basicFixture = fixtureFromElement('gr-edit-controls');

suite('gr-edit-controls tests', () => {
  let element;

  let showDialogSpy;
  let closeDialogSpy;
  let queryStub;

  setup(() => {
    element = basicFixture.instantiate();
    element.change = {_number: '42'};
    showDialogSpy = sinon.spy(element, '_showDialog');
    closeDialogSpy = sinon.spy(element, '_closeDialog');
    sinon.stub(element, '_hideAllDialogs');
    queryStub = stubRestApi('queryChangeFiles').returns(Promise.resolve([]));
    flush();
  });

  test('all actions exist', () => {
    // We take 1 away from the total found, due to an extra button being
    // added for the file uploads (browse).
    assert.equal(
        element.root.querySelectorAll('gr-button').length - 1,
        element._actions.length);
  });

  suite('edit button CUJ', () => {
    let navStubs;
    let openAutoComplete;

    setup(() => {
      navStubs = [
        sinon.stub(GerritNav, 'getEditUrlForDiff'),
        sinon.stub(GerritNav, 'navigateToRelativeUrl'),
      ];
      openAutoComplete = element.$.openDialog.querySelector('gr-autocomplete');
    });

    test('_isValidPath', () => {
      assert.isFalse(element._isValidPath(''));
      assert.isFalse(element._isValidPath('test/'));
      assert.isFalse(element._isValidPath('/'));
      assert.isTrue(element._isValidPath('test/path.cpp'));
      assert.isTrue(element._isValidPath('test.js'));
    });

    test('open', () => {
      MockInteractions.tap(element.shadowRoot.querySelector('#open'));
      element.patchNum = 1;
      return showDialogSpy.lastCall.returnValue.then(() => {
        assert.isTrue(element._hideAllDialogs.called);
        assert.isTrue(element.$.openDialog.disabled);
        assert.isFalse(queryStub.called);
        // Setup _focused manually - in headless mode Chrome sometimes don't
        // setup focus. flush and/or flushAsynchronousOperations don't help
        openAutoComplete._focused = true;
        openAutoComplete.noDebounce = true;
        openAutoComplete.text = 'src/test.cpp';
        assert.isTrue(queryStub.called);
        assert.isFalse(element.$.openDialog.disabled);
        MockInteractions.tap(element.$.openDialog.shadowRoot
            .querySelector('gr-button[primary]'));
        for (const stub of navStubs) { assert.isTrue(stub.called); }
        assert.deepEqual(GerritNav.getEditUrlForDiff.lastCall.args,
            [element.change, 'src/test.cpp', element.patchNum]);
        assert.isTrue(closeDialogSpy.called);
      });
    });

    test('cancel', () => {
      MockInteractions.tap(element.shadowRoot.querySelector('#open'));
      return showDialogSpy.lastCall.returnValue.then(() => {
        assert.isTrue(element.$.openDialog.disabled);
        openAutoComplete.noDebounce = true;
        openAutoComplete.text = 'src/test.cpp';
        assert.isFalse(element.$.openDialog.disabled);
        MockInteractions.tap(element.$.openDialog.shadowRoot
            .querySelector('gr-button'));
        for (const stub of navStubs) { assert.isFalse(stub.called); }
        assert.isTrue(closeDialogSpy.called);
        assert.equal(element._path, 'src/test.cpp');
      });
    });
  });

  suite('delete button CUJ', () => {
    let navStub;
    let deleteStub;
    let deleteAutocomplete;

    setup(() => {
      navStub = sinon.stub(GerritNav, 'navigateToChange');
      deleteStub = stubRestApi('deleteFileInChangeEdit');
      deleteAutocomplete =
          element.$.deleteDialog.querySelector('gr-autocomplete');
    });

    test('delete', () => {
      deleteStub.returns(Promise.resolve({ok: true}));
      MockInteractions.tap(element.shadowRoot.querySelector('#delete'));
      return showDialogSpy.lastCall.returnValue.then(() => {
        assert.isTrue(element.$.deleteDialog.disabled);
        assert.isFalse(queryStub.called);
        // Setup _focused manually - in headless mode Chrome sometimes don't
        // setup focus. flush and/or flushAsynchronousOperations don't help
        deleteAutocomplete._focused = true;
        deleteAutocomplete.noDebounce = true;
        deleteAutocomplete.text = 'src/test.cpp';
        assert.isTrue(queryStub.called);
        assert.isFalse(element.$.deleteDialog.disabled);
        MockInteractions.tap(element.$.deleteDialog.shadowRoot
            .querySelector('gr-button[primary]'));
        flush();

        assert.isTrue(deleteStub.called);

        return deleteStub.lastCall.returnValue.then(() => {
          assert.equal(element._path, '');
          assert.isTrue(navStub.called);
          assert.isTrue(closeDialogSpy.called);
        });
      });
    });

    test('delete fails', () => {
      deleteStub.returns(Promise.resolve({ok: false}));
      MockInteractions.tap(element.shadowRoot.querySelector('#delete'));
      return showDialogSpy.lastCall.returnValue.then(() => {
        assert.isTrue(element.$.deleteDialog.disabled);
        assert.isFalse(queryStub.called);
        // Setup _focused manually - in headless mode Chrome sometimes don't
        // setup focus. flush and/or flushAsynchronousOperations don't help
        deleteAutocomplete._focused = true;
        deleteAutocomplete.noDebounce = true;
        deleteAutocomplete.text = 'src/test.cpp';
        assert.isTrue(queryStub.called);
        assert.isFalse(element.$.deleteDialog.disabled);
        MockInteractions.tap(element.$.deleteDialog.shadowRoot
            .querySelector('gr-button[primary]'));
        flush();

        assert.isTrue(deleteStub.called);

        return deleteStub.lastCall.returnValue.then(() => {
          assert.isFalse(navStub.called);
          assert.isFalse(closeDialogSpy.called);
        });
      });
    });

    test('cancel', () => {
      MockInteractions.tap(element.shadowRoot.querySelector('#delete'));
      return showDialogSpy.lastCall.returnValue.then(() => {
        assert.isTrue(element.$.deleteDialog.disabled);
        element.$.deleteDialog.querySelector('gr-autocomplete').text =
            'src/test.cpp';
        assert.isFalse(element.$.deleteDialog.disabled);
        MockInteractions.tap(element.$.deleteDialog.shadowRoot
            .querySelector('gr-button'));
        assert.isFalse(navStub.called);
        assert.isTrue(closeDialogSpy.called);
        assert.equal(element._path, 'src/test.cpp');
      });
    });
  });

  suite('rename button CUJ', () => {
    let navStub;
    let renameStub;
    let renameAutocomplete;
    const inputSelector = PolymerElement ?
      '.newPathIronInput' :
      '.newPathInput';

    setup(() => {
      navStub = sinon.stub(GerritNav, 'navigateToChange');
      renameStub = stubRestApi('renameFileInChangeEdit');
      renameAutocomplete =
          element.$.renameDialog.querySelector('gr-autocomplete');
    });

    test('rename', () => {
      renameStub.returns(Promise.resolve({ok: true}));
      MockInteractions.tap(element.shadowRoot.querySelector('#rename'));
      return showDialogSpy.lastCall.returnValue.then(() => {
        assert.isTrue(element.$.renameDialog.disabled);
        assert.isFalse(queryStub.called);
        // Setup _focused manually - in headless mode Chrome sometimes don't
        // setup focus. flush and/or flushAsynchronousOperations don't help
        renameAutocomplete._focused = true;
        renameAutocomplete.noDebounce = true;
        renameAutocomplete.text = 'src/test.cpp';
        assert.isTrue(queryStub.called);
        assert.isTrue(element.$.renameDialog.disabled);

        element.$.renameDialog.querySelector(inputSelector).bindValue =
            'src/test.newPath';

        assert.isFalse(element.$.renameDialog.disabled);
        MockInteractions.tap(element.$.renameDialog.shadowRoot
            .querySelector('gr-button[primary]'));
        flush();

        assert.isTrue(renameStub.called);

        return renameStub.lastCall.returnValue.then(() => {
          assert.equal(element._path, '');
          assert.isTrue(navStub.called);
          assert.isTrue(closeDialogSpy.called);
        });
      });
    });

    test('rename fails', () => {
      renameStub.returns(Promise.resolve({ok: false}));
      MockInteractions.tap(element.shadowRoot.querySelector('#rename'));
      return showDialogSpy.lastCall.returnValue.then(() => {
        assert.isTrue(element.$.renameDialog.disabled);
        assert.isFalse(queryStub.called);
        // Setup _focused manually - in headless mode Chrome sometimes don't
        // setup focus. flush and/or flushAsynchronousOperations don't help
        renameAutocomplete._focused = true;
        renameAutocomplete.noDebounce = true;
        renameAutocomplete.text = 'src/test.cpp';
        assert.isTrue(queryStub.called);
        assert.isTrue(element.$.renameDialog.disabled);

        element.$.renameDialog.querySelector(inputSelector).bindValue =
            'src/test.newPath';

        assert.isFalse(element.$.renameDialog.disabled);
        MockInteractions.tap(element.$.renameDialog.shadowRoot
            .querySelector('gr-button[primary]'));
        flush();

        assert.isTrue(renameStub.called);

        return renameStub.lastCall.returnValue.then(() => {
          assert.isFalse(navStub.called);
          assert.isFalse(closeDialogSpy.called);
        });
      });
    });

    test('cancel', () => {
      MockInteractions.tap(element.shadowRoot.querySelector('#rename'));
      return showDialogSpy.lastCall.returnValue.then(() => {
        assert.isTrue(element.$.renameDialog.disabled);
        element.$.renameDialog.querySelector('gr-autocomplete').text =
            'src/test.cpp';
        element.$.renameDialog.querySelector(inputSelector).bindValue =
            'src/test.newPath';
        assert.isFalse(element.$.renameDialog.disabled);
        MockInteractions.tap(element.$.renameDialog.shadowRoot
            .querySelector('gr-button'));
        assert.isFalse(navStub.called);
        assert.isTrue(closeDialogSpy.called);
        assert.equal(element._path, 'src/test.cpp');
        assert.equal(element._newPath, 'src/test.newPath');
      });
    });
  });

  suite('restore button CUJ', () => {
    let navStub;
    let restoreStub;

    setup(() => {
      navStub = sinon.stub(GerritNav, 'navigateToChange');
      restoreStub = stubRestApi(
          'restoreFileInChangeEdit');
    });

    test('restore hidden by default', () => {
      assert.isTrue(element.shadowRoot
          .querySelector('#restore').classList.contains('invisible'));
    });

    test('restore', () => {
      restoreStub.returns(Promise.resolve({ok: true}));
      element._path = 'src/test.cpp';
      MockInteractions.tap(element.shadowRoot.querySelector('#restore'));
      return showDialogSpy.lastCall.returnValue.then(() => {
        MockInteractions.tap(element.$.restoreDialog.shadowRoot
            .querySelector('gr-button[primary]'));
        flush();

        assert.isTrue(restoreStub.called);
        assert.equal(restoreStub.lastCall.args[1], 'src/test.cpp');
        return restoreStub.lastCall.returnValue.then(() => {
          assert.equal(element._path, '');
          assert.isTrue(navStub.called);
          assert.isTrue(closeDialogSpy.called);
        });
      });
    });

    test('restore fails', () => {
      restoreStub.returns(Promise.resolve({ok: false}));
      element._path = 'src/test.cpp';
      MockInteractions.tap(element.shadowRoot.querySelector('#restore'));
      return showDialogSpy.lastCall.returnValue.then(() => {
        MockInteractions.tap(element.$.restoreDialog.shadowRoot
            .querySelector('gr-button[primary]'));
        flush();

        assert.isTrue(restoreStub.called);
        assert.equal(restoreStub.lastCall.args[1], 'src/test.cpp');
        return restoreStub.lastCall.returnValue.then(() => {
          assert.isFalse(navStub.called);
          assert.isFalse(closeDialogSpy.called);
        });
      });
    });

    test('cancel', () => {
      element._path = 'src/test.cpp';
      MockInteractions.tap(element.shadowRoot.querySelector('#restore'));
      return showDialogSpy.lastCall.returnValue.then(() => {
        MockInteractions.tap(element.$.restoreDialog.shadowRoot
            .querySelector('gr-button'));
        assert.isFalse(navStub.called);
        assert.isTrue(closeDialogSpy.called);
        assert.equal(element._path, 'src/test.cpp');
      });
    });
  });

  suite('save file upload', () => {
    let navStub;
    let fileStub;

    setup(() => {
      navStub = sinon.stub(GerritNav, 'navigateToChange');
      fileStub = stubRestApi('saveFileUploadChangeEdit');
    });

    test('_handleUploadConfirm', () => {
      fileStub.returns(Promise.resolve({ok: true}));

      element.change = {
        _number: '1',
        project: 'project',
        revisions: {
          abcd: {_number: 1},
          efgh: {_number: 2},
        },
        current_revision: 'efgh',
      };

      element._handleUploadConfirm('test.php', 'base64').then(() => {
        assert.equal(
            navStub.lastCall.args,
            '/c/project/+/1');
      });
    });
  });

  test('openOpenDialog', done => {
    element.openOpenDialog('test/path.cpp')
        .then(() => {
          assert.isFalse(element.$.openDialog.hasAttribute('hidden'));
          assert.equal(
              element.$.openDialog.querySelector('gr-autocomplete').text,
              'test/path.cpp');
          done();
        });
  });

  test('_getDialogFromEvent', () => {
    const spy = sinon.spy(element, '_getDialogFromEvent');
    element.addEventListener('tap', element._getDialogFromEvent);

    MockInteractions.tap(element.$.openDialog);
    flush();
    assert.equal(spy.lastCall.returnValue.id, 'openDialog');

    MockInteractions.tap(element.$.deleteDialog);
    flush();
    assert.equal(spy.lastCall.returnValue.id, 'deleteDialog');

    MockInteractions.tap(
        element.$.deleteDialog.querySelector('gr-autocomplete'));
    flush();
    assert.equal(spy.lastCall.returnValue.id, 'deleteDialog');

    MockInteractions.tap(element);
    flush();
    assert.notOk(spy.lastCall.returnValue);
  });
});

