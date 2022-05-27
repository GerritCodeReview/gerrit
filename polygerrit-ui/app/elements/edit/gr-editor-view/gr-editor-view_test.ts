/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-editor-view';
import {GrEditorView} from './gr-editor-view';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {HttpMethod} from '../../../constants/constants';
import {
  mockPromise,
  query,
  stubRestApi,
  stubStorage,
} from '../../../test/test-utils';
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
import {GrEndpointDecorator} from '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import {GrDefaultEditor} from '../gr-default-editor/gr-default-editor';
import {GrButton} from '../../shared/gr-button/gr-button';

const basicFixture = fixtureFromElement('gr-editor-view');

suite('gr-editor-view tests', () => {
  let element: GrEditorView;

  let savePathStub: sinon.SinonStub;
  let saveFileStub: sinon.SinonStub;
  let changeDetailStub: sinon.SinonStub;
  let navigateStub: sinon.SinonStub;

  setup(async () => {
    element = basicFixture.instantiate();
    savePathStub = stubRestApi('renameFileInChangeEdit');
    saveFileStub = stubRestApi('saveChangeEdit');
    changeDetailStub = stubRestApi('getChangeDetail');
    navigateStub = sinon.stub(element, 'viewEditInChangeView');
    await element.updateComplete;
  });

  suite('paramsChanged', () => {
    test('good params proceed', async () => {
      changeDetailStub.returns(Promise.resolve({}));
      const fileStub = sinon.stub(element, 'getFileData').callsFake(() => {
        element.content = 'text';
        element.newContent = 'text';
        element.type = 'application/octet-stream';
        return Promise.resolve();
      });

      element.params = {...createGenerateUrlEditViewParameters()};
      const promises = element.paramsChanged();

      await element.updateComplete;

      const changeNum = 42 as NumericChangeId;
      assert.equal(element.changeNum, changeNum);
      assert.equal(element.path, 'foo/bar.baz');
      assert.deepEqual(changeDetailStub.lastCall.args[0], changeNum);
      assert.deepEqual(fileStub.lastCall.args, [
        changeNum,
        'foo/bar.baz',
        EditPatchSetNum as PatchSetNum,
      ]);

      return promises?.then(() => {
        assert.equal(element.content, 'text');
        assert.equal(element.newContent, 'text');
        assert.equal(element.type, 'application/octet-stream');
      });
    });
  });

  test('edit file path', () => {
    element.changeNum = 42 as NumericChangeId;
    element.path = 'foo/bar.baz';
    savePathStub.onFirstCall().returns(Promise.resolve({}));
    savePathStub.onSecondCall().returns(Promise.resolve({ok: true}));

    // Calling with the same path should not navigate.
    return element
      .handlePathChanged(new CustomEvent('change', {detail: 'foo/bar.baz'}))
      .then(() => {
        assert.isFalse(savePathStub.called);
        // !ok response
        element
          .handlePathChanged(new CustomEvent('change', {detail: 'newPath'}))
          .then(() => {
            assert.isTrue(savePathStub.called);
            assert.isFalse(navigateStub.called);
            // ok response
            element
              .handlePathChanged(new CustomEvent('change', {detail: 'newPath'}))
              .then(() => {
                assert.isTrue(navigateStub.called);
                assert.isTrue(element.successfulSave);
              });
          });
      });
  });

  test('reacts to content-change event', async () => {
    const storageStub = stubStorage('setEditableContentItem');
    element.newContent = 'test';
    await element.updateComplete;
    query<GrEndpointDecorator>(element, '#editorEndpoint')!.dispatchEvent(
      new CustomEvent('content-change', {
        bubbles: true,
        composed: true,
        detail: {value: 'new content value'},
      })
    );
    element.storeTask?.flush();
    await element.updateComplete;

    assert.equal(element.newContent, 'new content value');
    assert.isTrue(storageStub.called);
    assert.equal(storageStub.lastCall.args[1], 'new content value');
  });

  suite('edit file content', () => {
    const originalText = 'file text';
    const newText = 'file text changed';

    setup(async () => {
      element.changeNum = 42 as NumericChangeId;
      element.path = 'foo/bar.baz';
      element.content = originalText;
      element.newContent = originalText;
      await element.updateComplete;
    });

    test('initial load', () => {
      assert.equal(
        query<GrDefaultEditor>(element, '#file')!.fileContent,
        originalText
      );
      assert.isTrue(
        query<GrButton>(element, '#save')!.hasAttribute('disabled')
      );
    });

    test('file modification and save, !ok response', async () => {
      const saveSpy = sinon.spy(element, 'saveEdit');
      const eraseStub = stubStorage('eraseEditableContentItem');
      const alertStub = sinon.stub(element, 'showAlert');
      saveFileStub.returns(Promise.resolve({ok: false}));
      element.newContent = newText;
      await element.updateComplete;

      assert.isFalse(
        query<GrButton>(element, '#save')!.hasAttribute('disabled')
      );
      assert.isFalse(element.saving);

      MockInteractions.tap(query<GrButton>(element, '#save')!);
      assert.isTrue(saveSpy.called);
      assert.equal(alertStub.lastCall.args[0], 'Saving changes...');
      assert.isTrue(element.saving);
      await element.updateComplete;
      assert.isTrue(
        query<GrButton>(element, '#save')!.hasAttribute('disabled')
      );

      return saveSpy.lastCall.returnValue.then(() => {
        assert.isTrue(saveFileStub.called);
        assert.isTrue(eraseStub.called);
        assert.isFalse(element.saving);
        assert.equal(alertStub.lastCall.args[0], 'Failed to save changes');
        assert.deepEqual(saveFileStub.lastCall.args, [
          42 as NumericChangeId,
          'foo/bar.baz',
          newText,
        ]);
        assert.isFalse(navigateStub.called);
        assert.isFalse(
          query<GrButton>(element, '#save')!.hasAttribute('disabled')
        );
        assert.notEqual(element.content, element.newContent);
      });
    });

    test('file modification and save', async () => {
      const saveSpy = sinon.spy(element, 'saveEdit');
      const alertStub = sinon.stub(element, 'showAlert');
      saveFileStub.returns(Promise.resolve({ok: true}));
      element.newContent = newText;
      await element.updateComplete;

      assert.isFalse(element.saving);
      assert.isFalse(
        query<GrButton>(element, '#save')!.hasAttribute('disabled')
      );

      MockInteractions.tap(query<GrButton>(element, '#save')!);
      assert.isTrue(saveSpy.called);
      assert.equal(alertStub.lastCall.args[0], 'Saving changes...');
      assert.isTrue(element.saving);
      await element.updateComplete;
      assert.isTrue(
        query<GrButton>(element, '#save')!.hasAttribute('disabled')
      );

      return saveSpy.lastCall.returnValue.then(() => {
        assert.isTrue(saveFileStub.called);
        assert.isFalse(element.saving);
        assert.equal(alertStub.lastCall.args[0], 'All changes saved');
        assert.isTrue(
          query<GrButton>(element, '#save')!.hasAttribute('disabled')
        );
        assert.equal(element.content, element.newContent);
        assert.isTrue(element.successfulSave);
        assert.isTrue(navigateStub.called);
      });
    });

    test('file modification and publish', async () => {
      const saveSpy = sinon.spy(element, 'saveEdit');
      const alertStub = sinon.stub(element, 'showAlert');
      const changeActionsStub = stubRestApi('executeChangeAction');
      saveFileStub.returns(Promise.resolve({ok: true}));
      element.newContent = newText;
      await element.updateComplete;

      assert.isFalse(element.saving);
      assert.isFalse(
        query<GrButton>(element, '#save')!.hasAttribute('disabled')
      );

      MockInteractions.tap(query<GrButton>(element, '#publish')!);
      assert.isTrue(saveSpy.called);
      assert.equal(alertStub.getCall(0).args[0], 'Saving changes...');
      assert.isTrue(element.saving);
      await element.updateComplete;
      assert.isTrue(
        query<GrButton>(element, '#save')!.hasAttribute('disabled')
      );

      return saveSpy.lastCall.returnValue.then(() => {
        assert.isTrue(saveFileStub.called);
        assert.isFalse(element.saving);

        assert.equal(alertStub.getCall(1).args[0], 'All changes saved');
        assert.equal(alertStub.getCall(2).args[0], 'Publishing edit...');

        assert.isTrue(
          query<GrButton>(element, '#save')!.hasAttribute('disabled')
        );
        assert.equal(element.content, element.newContent);
        assert.isTrue(element.successfulSave);
        assert.isFalse(navigateStub.called);

        const args = changeActionsStub.lastCall.args;
        assert.equal(args[0], 42 as NumericChangeId);
        assert.equal(args[1], HttpMethod.POST);
        assert.equal(args[2], '/edit:publish');
      });
    });

    test('file modification and close', async () => {
      const closeSpy = sinon.spy(element, 'handleCloseTap');
      element.newContent = newText;
      await element.updateComplete;

      assert.isFalse(
        query<GrButton>(element, '#save')!.hasAttribute('disabled')
      );

      MockInteractions.tap(query<GrButton>(element, '#close')!);
      assert.isTrue(closeSpy.called);
      assert.isFalse(saveFileStub.called);
      assert.isTrue(navigateStub.called);
    });
  });

  suite('getFileData', () => {
    setup(() => {
      element.newContent = 'initial';
      element.content = 'initial';
      element.type = 'initial';
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
        .getFileData(
          1 as NumericChangeId,
          'test/path',
          EditPatchSetNum as PatchSetNum
        )
        .then(() => {
          assert.equal(element.newContent, 'new content');
          assert.equal(element.content, 'new content');
          assert.equal(element.type, 'text/javascript');
        });
    });

    test('!res.ok', () => {
      stubRestApi('getFileContent').returns(
        Promise.resolve(new Response(null, {status: 500}))
      );

      // Ensure no data is set with a bad response.
      return element
        .getFileData(
          1 as NumericChangeId,
          'test/path',
          EditPatchSetNum as PatchSetNum
        )
        .then(() => {
          assert.equal(element.newContent, '');
          assert.equal(element.content, '');
          assert.equal(element.type, '');
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
        .getFileData(
          1 as NumericChangeId,
          'test/path',
          EditPatchSetNum as PatchSetNum
        )
        .then(() => {
          assert.equal(element.newContent, '');
          assert.equal(element.content, '');
          assert.equal(element.type, 'text/javascript');
        });
    });

    test('content and type is undefined', () => {
      stubRestApi('getFileContent').returns(
        Promise.resolve({...new Response(), ok: true})
      );

      return element
        .getFileData(
          1 as NumericChangeId,
          'test/path',
          EditPatchSetNum as PatchSetNum
        )
        .then(() => {
          assert.equal(element.newContent, '');
          assert.equal(element.content, '');
          assert.equal(element.type, '');
        });
    });
  });

  test('showAlert', async () => {
    const promise = mockPromise();
    element.addEventListener('show-alert', e => {
      assert.deepEqual(e.detail, {message: 'test message', showDismiss: true});
      assert.isTrue(e.bubbles);
      promise.resolve();
    });

    element.showAlert('test message');
    await promise;
  });

  test('viewEditInChangeView', () => {
    element.change = createChangeViewChange();
    navigateStub.restore();
    const navStub = sinon.stub(GerritNav, 'navigateToChange');
    element.patchNum = EditPatchSetNum;
    element.viewEditInChangeView();
    assert.equal(navStub.lastCall.args[1]!.patchNum, undefined);
    assert.equal(navStub.lastCall.args[1]!.isEdit, true);
  });

  suite('keyboard shortcuts', () => {
    // Used as the spy on the handler for each entry in keyBindings.
    let handleSpy: sinon.SinonSpy;

    suite('handleSaveShortcut', () => {
      let saveStub: sinon.SinonStub;
      setup(() => {
        handleSpy = sinon.spy(element, 'handleSaveShortcut');
        saveStub = sinon.stub(element, 'saveEdit');
      });

      test('save enabled', async () => {
        element.content = '';
        element.newContent = '_test';
        MockInteractions.pressAndReleaseKeyOn(element, 83, 'ctrl', 's');
        await element.updateComplete;

        assert.isTrue(handleSpy.calledOnce);
        assert.isTrue(saveStub.calledOnce);

        MockInteractions.pressAndReleaseKeyOn(element, 83, 'meta', 's');
        await element.updateComplete;

        assert.equal(handleSpy.callCount, 2);
        assert.equal(saveStub.callCount, 2);
      });

      test('save disabled', async () => {
        MockInteractions.pressAndReleaseKeyOn(element, 83, 'ctrl', 's');
        await element.updateComplete;

        assert.isTrue(handleSpy.calledOnce);
        assert.isFalse(saveStub.called);

        MockInteractions.pressAndReleaseKeyOn(element, 83, 'meta', 's');
        await element.updateComplete;

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
        .getFileData(1 as NumericChangeId, 'test', 1 as PatchSetNum)
        .then(async () => {
          await element.updateComplete;

          assert.isTrue(alertStub.called);
          assert.equal(element.newContent, 'pending edit');
          assert.equal(element.content, 'old content');
          assert.equal(element.type, 'text/javascript');
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
        .getFileData(1 as NumericChangeId, 'test', 1 as PatchSetNum)
        .then(async () => {
          await element.updateComplete;

          assert.isFalse(alertStub.called);
          assert.equal(element.newContent, 'pending edit');
          assert.equal(element.content, 'pending edit');
          assert.equal(element.type, 'text/javascript');
        });
    });

    test('storage key computation', () => {
      element.changeNum = 1 as NumericChangeId;
      element.patchNum = 1 as PatchSetNum;
      element.path = 'test';
      assert.equal(element.storageKey, 'c1_ps1_test');
    });
  });
});
