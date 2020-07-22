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
import './gr-editor-view.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';
import {SPECIAL_PATCH_SET_NUM} from '../../../utils/patch-set-util.js';

const basicFixture = fixtureFromElement('gr-editor-view');

suite('gr-editor-view tests', () => {
  let element;

  let savePathStub;
  let saveFileStub;
  let changeDetailStub;
  let navigateStub;
  const mockParams = {
    changeNum: '42',
    path: 'foo/bar.baz',
    patchNum: 'edit',
  };

  setup(() => {
    stub('gr-rest-api-interface', {
      getLoggedIn() { return Promise.resolve(true); },
      getEditPreferences() { return Promise.resolve({}); },
    });

    element = basicFixture.instantiate();
    savePathStub = sinon.stub(element.$.restAPI, 'renameFileInChangeEdit');
    saveFileStub = sinon.stub(element.$.restAPI, 'saveChangeEdit');
    changeDetailStub = sinon.stub(element.$.restAPI, 'getDiffChangeDetail');
    navigateStub = sinon.stub(element, '_viewEditInChangeView');
  });

  suite('_paramsChanged', () => {
    test('incorrect view returns immediately', () => {
      element._paramsChanged(
          {...mockParams, view: GerritNav.View.DIFF});
      assert.notOk(element._changeNum);
    });

    test('good params proceed', () => {
      changeDetailStub.returns(Promise.resolve({}));
      const fileStub = sinon.stub(element, '_getFileData').callsFake(() => {
        element._content = 'text';
        element._newContent = 'text';
        element._type = 'application/octet-stream';
      });

      const promises = element._paramsChanged(
          {...mockParams, view: GerritNav.View.EDIT});

      flushAsynchronousOperations();
      assert.equal(element._changeNum, mockParams.changeNum);
      assert.equal(element._path, mockParams.path);
      assert.deepEqual(changeDetailStub.lastCall.args[0],
          mockParams.changeNum);
      assert.deepEqual(fileStub.lastCall.args,
          [mockParams.changeNum, mockParams.path, mockParams.patchNum]);

      return promises.then(() => {
        assert.equal(element._content, 'text');
        assert.equal(element._newContent, 'text');
        assert.equal(element._type, 'application/octet-stream');
      });
    });
  });

  test('edit file path', () => {
    element._changeNum = mockParams.changeNum;
    element._path = mockParams.path;
    savePathStub.onFirstCall().returns(Promise.resolve({}));
    savePathStub.onSecondCall().returns(Promise.resolve({ok: true}));

    // Calling with the same path should not navigate.
    return element._handlePathChanged({detail: mockParams.path}).then(() => {
      assert.isFalse(savePathStub.called);
      // !ok response
      element._handlePathChanged({detail: 'newPath'}).then(() => {
        assert.isTrue(savePathStub.called);
        assert.isFalse(navigateStub.called);
        // ok response
        element._handlePathChanged({detail: 'newPath'}).then(() => {
          assert.isTrue(navigateStub.called);
          assert.isTrue(element._successfulSave);
        });
      });
    });
  });

  test('reacts to content-change event', () => {
    const storeStub = sinon.spy(element.$.storage, 'setEditableContentItem');
    element._newContent = 'test';
    element.$.editorEndpoint.dispatchEvent(new CustomEvent('content-change', {
      bubbles: true, composed: true,
      detail: {value: 'new content value'},
    }));
    element.flushDebouncer('store');
    flushAsynchronousOperations();

    assert.equal(element._newContent, 'new content value');
    assert.isTrue(storeStub.called);
    assert.equal(storeStub.lastCall.args[1], 'new content value');
  });

  suite('edit file content', () => {
    const originalText = 'file text';
    const newText = 'file text changed';

    setup(() => {
      element._changeNum = mockParams.changeNum;
      element._path = mockParams.path;
      element._content = originalText;
      element._newContent = originalText;
      flushAsynchronousOperations();
    });

    test('initial load', () => {
      assert.equal(element.$.file.fileContent, originalText);
      assert.isTrue(element.$.save.hasAttribute('disabled'));
    });

    test('file modification and save, !ok response', () => {
      const saveSpy = sinon.spy(element, '_saveEdit');
      const eraseStub = sinon.stub(element.$.storage,
          'eraseEditableContentItem');
      const alertStub = sinon.stub(element, '_showAlert');
      saveFileStub.returns(Promise.resolve({ok: false}));
      element._newContent = newText;
      flushAsynchronousOperations();

      assert.isFalse(element.$.save.hasAttribute('disabled'));
      assert.isFalse(element._saving);

      MockInteractions.tap(element.$.save);
      assert.isTrue(saveSpy.called);
      assert.equal(alertStub.lastCall.args[0], 'Saving changes...');
      assert.isTrue(element._saving);
      assert.isTrue(element.$.save.hasAttribute('disabled'));

      return saveSpy.lastCall.returnValue.then(() => {
        assert.isTrue(saveFileStub.called);
        assert.isTrue(eraseStub.called);
        assert.isFalse(element._saving);
        assert.equal(alertStub.lastCall.args[0], 'Failed to save changes');
        assert.deepEqual(saveFileStub.lastCall.args,
            [mockParams.changeNum, mockParams.path, newText]);
        assert.isFalse(navigateStub.called);
        assert.isFalse(element.$.save.hasAttribute('disabled'));
        assert.notEqual(element._content, element._newContent);
      });
    });

    test('file modification and save', () => {
      const saveSpy = sinon.spy(element, '_saveEdit');
      const alertStub = sinon.stub(element, '_showAlert');
      saveFileStub.returns(Promise.resolve({ok: true}));
      element._newContent = newText;
      flushAsynchronousOperations();

      assert.isFalse(element._saving);
      assert.isFalse(element.$.save.hasAttribute('disabled'));

      MockInteractions.tap(element.$.save);
      assert.isTrue(saveSpy.called);
      assert.equal(alertStub.lastCall.args[0], 'Saving changes...');
      assert.isTrue(element._saving);
      assert.isTrue(element.$.save.hasAttribute('disabled'));

      return saveSpy.lastCall.returnValue.then(() => {
        assert.isTrue(saveFileStub.called);
        assert.isFalse(element._saving);
        assert.equal(alertStub.lastCall.args[0], 'All changes saved');
        assert.isFalse(navigateStub.called);
        assert.isTrue(element.$.save.hasAttribute('disabled'));
        assert.equal(element._content, element._newContent);
        assert.isTrue(element._successfulSave);
      });
    });

    test('file modification and close', () => {
      const closeSpy = sinon.spy(element, '_handleCloseTap');
      element._newContent = newText;
      flushAsynchronousOperations();

      assert.isFalse(element.$.save.hasAttribute('disabled'));

      MockInteractions.tap(element.$.close);
      assert.isTrue(closeSpy.called);
      assert.isFalse(saveFileStub.called);
      assert.isTrue(navigateStub.called);
    });
  });

  suite('_getFileData', () => {
    setup(() => {
      element._newContent = 'initial';
      element._content = 'initial';
      element._type = 'initial';
      sinon.stub(element.$.storage, 'getEditableContentItem').returns(null);
    });

    test('res.ok', () => {
      sinon.stub(element.$.restAPI, 'getFileContent')
          .returns(Promise.resolve({
            ok: true,
            type: 'text/javascript',
            content: 'new content',
          }));

      // Ensure no data is set with a bad response.
      return element._getFileData('1', 'test/path', 'edit').then(() => {
        assert.equal(element._newContent, 'new content');
        assert.equal(element._content, 'new content');
        assert.equal(element._type, 'text/javascript');
      });
    });

    test('!res.ok', () => {
      sinon.stub(element.$.restAPI, 'getFileContent')
          .returns(Promise.resolve({}));

      // Ensure no data is set with a bad response.
      return element._getFileData('1', 'test/path', 'edit').then(() => {
        assert.equal(element._newContent, '');
        assert.equal(element._content, '');
        assert.equal(element._type, '');
      });
    });

    test('content is undefined', () => {
      sinon.stub(element.$.restAPI, 'getFileContent')
          .returns(Promise.resolve({
            ok: true,
            type: 'text/javascript',
          }));

      return element._getFileData('1', 'test/path', 'edit').then(() => {
        assert.equal(element._newContent, '');
        assert.equal(element._content, '');
        assert.equal(element._type, 'text/javascript');
      });
    });

    test('content and type is undefined', () => {
      sinon.stub(element.$.restAPI, 'getFileContent')
          .returns(Promise.resolve({
            ok: true,
          }));

      return element._getFileData('1', 'test/path', 'edit').then(() => {
        assert.equal(element._newContent, '');
        assert.equal(element._content, '');
        assert.equal(element._type, '');
      });
    });
  });

  test('_showAlert', done => {
    element.addEventListener('show-alert', e => {
      assert.deepEqual(e.detail, {message: 'test message'});
      assert.isTrue(e.bubbles);
      done();
    });

    element._showAlert('test message');
  });

  test('_viewEditInChangeView respects _patchNum', () => {
    navigateStub.restore();
    const navStub = sinon.stub(GerritNav, 'navigateToChange');
    element._patchNum = SPECIAL_PATCH_SET_NUM.EDIT;
    element._viewEditInChangeView();
    assert.equal(navStub.lastCall.args[1], SPECIAL_PATCH_SET_NUM.EDIT);
    element._patchNum = '1';
    element._viewEditInChangeView();
    assert.equal(navStub.lastCall.args[1], '1');
    element._successfulSave = true;
    element._viewEditInChangeView();
    assert.equal(navStub.lastCall.args[1], SPECIAL_PATCH_SET_NUM.EDIT);
  });

  suite('keyboard shortcuts', () => {
    // Used as the spy on the handler for each entry in keyBindings.
    let handleSpy;

    suite('_handleSaveShortcut', () => {
      let saveStub;
      setup(() => {
        handleSpy = sinon.spy(element, '_handleSaveShortcut');
        saveStub = sinon.stub(element, '_saveEdit');
      });

      test('save enabled', () => {
        element._content = '';
        element._newContent = '_test';
        MockInteractions.pressAndReleaseKeyOn(element, 83, 'ctrl', 's');
        flushAsynchronousOperations();

        assert.isTrue(handleSpy.calledOnce);
        assert.isTrue(saveStub.calledOnce);

        MockInteractions.pressAndReleaseKeyOn(element, 83, 'meta', 's');
        flushAsynchronousOperations();

        assert.equal(handleSpy.callCount, 2);
        assert.equal(saveStub.callCount, 2);
      });

      test('save disabled', () => {
        MockInteractions.pressAndReleaseKeyOn(element, 83, 'ctrl', 's');
        flushAsynchronousOperations();

        assert.isTrue(handleSpy.calledOnce);
        assert.isFalse(saveStub.called);

        MockInteractions.pressAndReleaseKeyOn(element, 83, 'meta', 's');
        flushAsynchronousOperations();

        assert.equal(handleSpy.callCount, 2);
        assert.isFalse(saveStub.called);
      });
    });
  });

  suite('gr-storage caching', () => {
    test('local edit exists', () => {
      sinon.stub(element.$.storage, 'getEditableContentItem')
          .returns({message: 'pending edit'});
      sinon.stub(element.$.restAPI, 'getFileContent')
          .returns(Promise.resolve({
            ok: true,
            type: 'text/javascript',
            content: 'old content',
          }));

      const alertStub = sinon.stub();
      element.addEventListener('show-alert', alertStub);

      return element._getFileData(1, 'test', 1).then(() => {
        flushAsynchronousOperations();

        assert.isTrue(alertStub.called);
        assert.equal(element._newContent, 'pending edit');
        assert.equal(element._content, 'old content');
        assert.equal(element._type, 'text/javascript');
      });
    });

    test('local edit exists, is same as remote edit', () => {
      sinon.stub(element.$.storage, 'getEditableContentItem')
          .returns({message: 'pending edit'});
      sinon.stub(element.$.restAPI, 'getFileContent')
          .returns(Promise.resolve({
            ok: true,
            type: 'text/javascript',
            content: 'pending edit',
          }));

      const alertStub = sinon.stub();
      element.addEventListener('show-alert', alertStub);

      return element._getFileData(1, 'test', 1).then(() => {
        flushAsynchronousOperations();

        assert.isFalse(alertStub.called);
        assert.equal(element._newContent, 'pending edit');
        assert.equal(element._content, 'pending edit');
        assert.equal(element._type, 'text/javascript');
      });
    });

    test('storage key computation', () => {
      element._changeNum = 1;
      element._patchNum = 1;
      element._path = 'test';
      assert.equal(element.storageKey, 'c1_ps1_test');
    });
  });
});

