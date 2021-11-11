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

import '../../../test/common-test-setup-karma';
import {GrEditorView} from './gr-editor-view';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {HttpMethod} from '../../../constants/constants';
import {mockPromise, stubRestApi, stubStorage} from '../../../test/test-utils';
import {
  EditPatchSetNum,
  NumericChangeId,
  PatchSetNum,
} from '../../../types/common';
import {
  createChangeViewChange,
  createGenerateUrlEditViewParameters,
} from '../../../test/test-data-generators';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';

const basicFixture = fixtureFromElement('gr-editor-view');

suite('gr-editor-view tests', () => {
  let element: GrEditorView;

  let savePathStub: sinon.SinonStub;
  let saveFileStub: sinon.SinonStub;
  let changeDetailStub: sinon.SinonStub;
  let navigateStub: sinon.SinonStub;

  setup(() => {
    element = basicFixture.instantiate();
    savePathStub = stubRestApi('renameFileInChangeEdit');
    saveFileStub = stubRestApi('saveChangeEdit');
    changeDetailStub = stubRestApi('getDiffChangeDetail');
    navigateStub = sinon.stub(element, '_viewEditInChangeView');
  });

  suite('_paramsChanged', () => {
    test('good params proceed', () => {
      changeDetailStub.returns(Promise.resolve({}));
      const fileStub = sinon.stub(element, '_getFileData').callsFake(() => {
        element._content = 'text';
        element._newContent = 'text';
        element._type = 'application/octet-stream';
        return Promise.resolve();
      });

      const promises = element._paramsChanged({
        ...createGenerateUrlEditViewParameters(),
      });

      flush();
      const changeNum = 42 as NumericChangeId;
      assert.equal(element._changeNum, changeNum);
      assert.equal(element._path, 'foo/bar.baz');
      assert.deepEqual(changeDetailStub.lastCall.args[0], changeNum);
      assert.deepEqual(fileStub.lastCall.args, [
        changeNum,
        'foo/bar.baz',
        EditPatchSetNum as PatchSetNum,
      ]);

      return promises?.then(() => {
        assert.equal(element._content, 'text');
        assert.equal(element._newContent, 'text');
        assert.equal(element._type, 'application/octet-stream');
      });
    });
  });

  test('edit file path', () => {
    element._changeNum = 42 as NumericChangeId;
    element._path = 'foo/bar.baz';
    savePathStub.onFirstCall().returns(Promise.resolve({}));
    savePathStub.onSecondCall().returns(Promise.resolve({ok: true}));

    // Calling with the same path should not navigate.
    return element
      ._handlePathChanged(new CustomEvent('change', {detail: 'foo/bar.baz'}))
      .then(() => {
        assert.isFalse(savePathStub.called);
        // !ok response
        element
          ._handlePathChanged(new CustomEvent('change', {detail: 'newPath'}))
          .then(() => {
            assert.isTrue(savePathStub.called);
            assert.isFalse(navigateStub.called);
            // ok response
            element
              ._handlePathChanged(
                new CustomEvent('change', {detail: 'newPath'})
              )
              .then(() => {
                assert.isTrue(navigateStub.called);
                assert.isTrue(element._successfulSave);
              });
          });
      });
  });

  test('reacts to content-change event', () => {
    const storageStub = stubStorage('setEditableContentItem');
    element._newContent = 'test';
    element.$.editorEndpoint.dispatchEvent(
      new CustomEvent('content-change', {
        bubbles: true,
        composed: true,
        detail: {value: 'new content value'},
      })
    );
    element.storeTask?.flush();
    flush();

    assert.equal(element._newContent, 'new content value');
    assert.isTrue(storageStub.called);
    assert.equal(storageStub.lastCall.args[1], 'new content value');
  });

  suite('edit file content', () => {
    const originalText = 'file text';
    const newText = 'file text changed';

    setup(() => {
      element._changeNum = 42 as NumericChangeId;
      element._path = 'foo/bar.baz';
      element._content = originalText;
      element._newContent = originalText;
      flush();
    });

    test('initial load', () => {
      assert.equal(element.$.file.fileContent, originalText);
      assert.isTrue(element.$.save.hasAttribute('disabled'));
    });

    test('file modification and save, !ok response', () => {
      const saveSpy = sinon.spy(element, '_saveEdit');
      const eraseStub = stubStorage('eraseEditableContentItem');
      const alertStub = sinon.stub(element, '_showAlert');
      saveFileStub.returns(Promise.resolve({ok: false}));
      element._newContent = newText;
      flush();

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
        assert.deepEqual(saveFileStub.lastCall.args, [
          42 as NumericChangeId,
          'foo/bar.baz',
          newText,
        ]);
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
      flush();

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
        assert.isTrue(element.$.save.hasAttribute('disabled'));
        assert.equal(element._content, element._newContent);
        assert.isTrue(element._successfulSave);
        assert.isTrue(navigateStub.called);
      });
    });

    test('file modification and publish', () => {
      const saveSpy = sinon.spy(element, '_saveEdit');
      const alertStub = sinon.stub(element, '_showAlert');
      const changeActionsStub = stubRestApi('executeChangeAction');
      saveFileStub.returns(Promise.resolve({ok: true}));
      element._newContent = newText;
      flush();

      assert.isFalse(element._saving);
      assert.isFalse(element.$.save.hasAttribute('disabled'));

      MockInteractions.tap(element.$.publish);
      assert.isTrue(saveSpy.called);
      assert.equal(alertStub.getCall(0).args[0], 'Saving changes...');
      assert.isTrue(element._saving);
      assert.isTrue(element.$.save.hasAttribute('disabled'));

      return saveSpy.lastCall.returnValue.then(() => {
        assert.isTrue(saveFileStub.called);
        assert.isFalse(element._saving);

        assert.equal(alertStub.getCall(1).args[0], 'All changes saved');
        assert.equal(alertStub.getCall(2).args[0], 'Publishing edit...');

        assert.isTrue(element.$.save.hasAttribute('disabled'));
        assert.equal(element._content, element._newContent);
        assert.isTrue(element._successfulSave);
        assert.isFalse(navigateStub.called);

        const args = changeActionsStub.lastCall.args;
        assert.equal(args[0], 42 as NumericChangeId);
        assert.equal(args[1], HttpMethod.POST);
        assert.equal(args[2], '/edit:publish');
      });
    });

    test('file modification and close', () => {
      const closeSpy = sinon.spy(element, '_handleCloseTap');
      element._newContent = newText;
      flush();

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
      stubStorage('getEditableContentItem').returns(null);
    });

    test('res.ok', () => {
      stubRestApi('getFileContent').returns(
        Promise.resolve({
          ok: true,
          type: 'text/javascript',
          content: 'new content',
        })
      );

      // Ensure no data is set with a bad response.
      return element
        ._getFileData(
          1 as NumericChangeId,
          'test/path',
          EditPatchSetNum as PatchSetNum
        )
        .then(() => {
          assert.equal(element._newContent, 'new content');
          assert.equal(element._content, 'new content');
          assert.equal(element._type, 'text/javascript');
        });
    });

    test('!res.ok', () => {
      stubRestApi('getFileContent').returns(
        Promise.resolve(new Response(null, {status: 500}))
      );

      // Ensure no data is set with a bad response.
      return element
        ._getFileData(
          1 as NumericChangeId,
          'test/path',
          EditPatchSetNum as PatchSetNum
        )
        .then(() => {
          assert.equal(element._newContent, '');
          assert.equal(element._content, '');
          assert.equal(element._type, '');
        });
    });

    test('content is undefined', () => {
      stubRestApi('getFileContent').returns(
        Promise.resolve({
          ...new Response(),
          ok: true,
          type: 'text/javascript' as ResponseType,
        })
      );

      return element
        ._getFileData(
          1 as NumericChangeId,
          'test/path',
          EditPatchSetNum as PatchSetNum
        )
        .then(() => {
          assert.equal(element._newContent, '');
          assert.equal(element._content, '');
          assert.equal(element._type, 'text/javascript');
        });
    });

    test('content and type is undefined', () => {
      stubRestApi('getFileContent').returns(
        Promise.resolve({...new Response(), ok: true})
      );

      return element
        ._getFileData(
          1 as NumericChangeId,
          'test/path',
          EditPatchSetNum as PatchSetNum
        )
        .then(() => {
          assert.equal(element._newContent, '');
          assert.equal(element._content, '');
          assert.equal(element._type, '');
        });
    });
  });

  test('_showAlert', async () => {
    const promise = mockPromise();
    element.addEventListener('show-alert', e => {
      assert.deepEqual(e.detail, {message: 'test message'});
      assert.isTrue(e.bubbles);
      promise.resolve();
    });

    element._showAlert('test message');
    await promise;
  });

  test('_viewEditInChangeView', () => {
    element._change = createChangeViewChange();
    navigateStub.restore();
    const navStub = sinon.stub(GerritNav, 'navigateToChange');
    element._patchNum = EditPatchSetNum;
    element._viewEditInChangeView();
    assert.equal(navStub.lastCall.args[1].patchNum, undefined);
    assert.equal(navStub.lastCall.args[1].isEdit, true);
  });

  suite('keyboard shortcuts', () => {
    // Used as the spy on the handler for each entry in keyBindings.
    let handleSpy: sinon.SinonSpy;

    suite('_handleSaveShortcut', () => {
      let saveStub: sinon.SinonStub;
      setup(() => {
        handleSpy = sinon.spy(element, '_handleSaveShortcut');
        saveStub = sinon.stub(element, '_saveEdit');
      });

      test('save enabled', () => {
        element._content = '';
        element._newContent = '_test';
        MockInteractions.pressAndReleaseKeyOn(element, 83, 'ctrl', 's');
        flush();

        assert.isTrue(handleSpy.calledOnce);
        assert.isTrue(saveStub.calledOnce);

        MockInteractions.pressAndReleaseKeyOn(element, 83, 'meta', 's');
        flush();

        assert.equal(handleSpy.callCount, 2);
        assert.equal(saveStub.callCount, 2);
      });

      test('save disabled', () => {
        MockInteractions.pressAndReleaseKeyOn(element, 83, 'ctrl', 's');
        flush();

        assert.isTrue(handleSpy.calledOnce);
        assert.isFalse(saveStub.called);

        MockInteractions.pressAndReleaseKeyOn(element, 83, 'meta', 's');
        flush();

        assert.equal(handleSpy.callCount, 2);
        assert.isFalse(saveStub.called);
      });
    });
  });

  suite('gr-storage caching', () => {
    test('local edit exists', () => {
      stubStorage('getEditableContentItem').returns({
        message: 'pending edit',
        updated: 0,
      });
      stubRestApi('getFileContent').returns(
        Promise.resolve({
          ok: true,
          type: 'text/javascript',
          content: 'old content',
        })
      );

      const alertStub = sinon.stub();
      element.addEventListener('show-alert', alertStub);

      return element
        ._getFileData(1 as NumericChangeId, 'test', 1 as PatchSetNum)
        .then(() => {
          flush();

          assert.isTrue(alertStub.called);
          assert.equal(element._newContent, 'pending edit');
          assert.equal(element._content, 'old content');
          assert.equal(element._type, 'text/javascript');
        });
    });

    test('local edit exists, is same as remote edit', () => {
      stubStorage('getEditableContentItem').returns({
        message: 'pending edit',
        updated: 0,
      });
      stubRestApi('getFileContent').returns(
        Promise.resolve({
          ok: true,
          type: 'text/javascript',
          content: 'pending edit',
        })
      );

      const alertStub = sinon.stub();
      element.addEventListener('show-alert', alertStub);

      return element
        ._getFileData(1 as NumericChangeId, 'test', 1 as PatchSetNum)
        .then(() => {
          flush();

          assert.isFalse(alertStub.called);
          assert.equal(element._newContent, 'pending edit');
          assert.equal(element._content, 'pending edit');
          assert.equal(element._type, 'text/javascript');
        });
    });

    test('storage key computation', () => {
      element._changeNum = 1 as NumericChangeId;
      element._patchNum = 1 as PatchSetNum;
      element._path = 'test';
      assert.equal(element.storageKey, 'c1_ps1_test');
    });
  });
});
