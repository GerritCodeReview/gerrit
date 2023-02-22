/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-editor-view';
import {GrEditorView} from './gr-editor-view';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {HttpMethod} from '../../../constants/constants';
import {
  mockPromise,
  pressKey,
  query,
  stubRestApi,
} from '../../../test/test-utils';
import {
  EDIT,
  NumericChangeId,
  PatchSetNumber,
  RevisionPatchSetNum,
} from '../../../types/common';
import {
  createChangeViewChange,
  createEditViewState,
} from '../../../test/test-data-generators';
import {GrEndpointDecorator} from '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import {GrDefaultEditor} from '../gr-default-editor/gr-default-editor';
import {GrButton} from '../../shared/gr-button/gr-button';
import {fixture, html, assert} from '@open-wc/testing';
import {EventType} from '../../../types/events';
import {Modifier} from '../../../utils/dom-util';
import {testResolver} from '../../../test/common-test-setup';
import {storageServiceToken} from '../../../services/storage/gr-storage_impl';
import {StorageService} from '../../../services/storage/gr-storage';

suite('gr-editor-view tests', () => {
  let element: GrEditorView;

  let savePathStub: sinon.SinonStub;
  let saveFileStub: sinon.SinonStub;
  let changeDetailStub: sinon.SinonStub;
  let navigateStub: sinon.SinonStub;
  let storageService: StorageService;

  setup(async () => {
    element = await fixture(html`<gr-editor-view></gr-editor-view>`);
    savePathStub = stubRestApi('renameFileInChangeEdit');
    saveFileStub = stubRestApi('saveChangeEdit');
    changeDetailStub = stubRestApi('getChangeDetail');
    navigateStub = sinon.stub(element, 'viewEditInChangeView');
    element.viewState = {
      ...createEditViewState(),
      patchNum: 1 as PatchSetNumber,
    };
    element.latestPatchsetNumber = 1 as PatchSetNumber;
    await element.updateComplete;
    storageService = testResolver(storageServiceToken);
  });

  test('render', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="stickyHeader">
          <header>
            <span class="controlGroup">
              <span> Edit mode </span>
              <span class="separator"> </span>
              <gr-editable-label
                id="global"
                labeltext="File path"
                placeholder="File path..."
                tabindex="0"
                title="${element.viewState?.editView?.path}"
              >
              </gr-editable-label>
            </span>
            <span class="controlGroup rightControls">
              <gr-button
                aria-disabled="false"
                id="close"
                link=""
                role="button"
                tabindex="0"
              >
                Cancel
              </gr-button>
              <gr-button
                aria-disabled="true"
                disabled=""
                id="save"
                link=""
                primary=""
                role="button"
                tabindex="-1"
                title="Save and Close the file"
              >
                Save
              </gr-button>
              <gr-button
                aria-disabled="true"
                disabled=""
                id="publish"
                link=""
                primary=""
                role="button"
                tabindex="-1"
                title="Publish your edit. A new patchset will be created."
              >
                Save & Publish
              </gr-button>
            </span>
          </header>
        </div>
        <div class="textareaWrapper">
          <gr-endpoint-decorator id="editorEndpoint" name="editor">
            <gr-endpoint-param name="fileContent"> </gr-endpoint-param>
            <gr-endpoint-param name="prefs"> </gr-endpoint-param>
            <gr-endpoint-param name="fileType"> </gr-endpoint-param>
            <gr-endpoint-param name="lineNum"> </gr-endpoint-param>
            <gr-default-editor id="file"> </gr-default-editor>
          </gr-endpoint-decorator>
        </div>
      `
    );
  });

  suite('viewStateChanged', () => {
    test('good view state proceed', async () => {
      changeDetailStub.returns(Promise.resolve({}));
      const fileStub = sinon.stub(element, 'getFileData').callsFake(() => {
        element.content = 'text';
        element.newContent = 'text';
        element.type = 'application/octet-stream';
        return Promise.resolve();
      });

      element.viewState = {...createEditViewState()};
      const promises = element.viewStateChanged();

      await element.updateComplete;

      const changeNum = 42 as NumericChangeId;
      assert.deepEqual(changeDetailStub.lastCall.args[0], changeNum);
      assert.isTrue(fileStub.called);

      return promises?.then(() => {
        assert.equal(element.content, 'text');
        assert.equal(element.newContent, 'text');
        assert.equal(element.type, 'application/octet-stream');
      });
    });
  });

  test('edit file path', () => {
    element.viewState = {...createEditViewState()};
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
    const storageStub = sinon.stub(storageService, 'setEditableContentItem');
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
      element.viewState = {...createEditViewState()};
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
      const eraseStub = sinon.stub(storageService, 'eraseEditableContentItem');
      const alertStub = sinon.stub(element, 'showAlert');
      saveFileStub.returns(Promise.resolve({ok: false}));
      element.newContent = newText;
      await element.updateComplete;

      assert.isFalse(
        query<GrButton>(element, '#save')!.hasAttribute('disabled')
      );
      assert.isFalse(element.saving);

      query<GrButton>(element, '#save')!.click();
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

      query<GrButton>(element, '#save')!.click();
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

      query<GrButton>(element, '#publish')!.click();
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

      query<GrButton>(element, '#close')!.click();
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
      sinon.stub(storageService, 'getEditableContentItem').returns(null);
    });

    test('res.ok', () => {
      stubRestApi('getFileContent').returns(
        Promise.resolve({
          ok: true,
          type: 'text/javascript',
          content: 'new content',
        })
      );
      element.viewState = {
        ...createEditViewState(),
        changeNum: 1 as NumericChangeId,
        patchNum: EDIT,
        editView: {path: 'test/path'},
      };

      // Ensure no data is set with a bad response.
      return element.getFileData().then(() => {
        assert.equal(element.newContent, 'new content');
        assert.equal(element.content, 'new content');
        assert.equal(element.type, 'text/javascript');
      });
    });

    test('!res.ok', () => {
      stubRestApi('getFileContent').returns(
        Promise.resolve(new Response(null, {status: 500}))
      );
      element.viewState = {
        ...createEditViewState(),
        changeNum: 1 as NumericChangeId,
        patchNum: EDIT,
        editView: {path: 'test/path'},
      };

      // Ensure no data is set with a bad response.
      return element.getFileData().then(() => {
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
      element.viewState = {
        ...createEditViewState(),
        changeNum: 1 as NumericChangeId,
        patchNum: EDIT,
        editView: {path: 'test/path'},
      };

      return element.getFileData().then(() => {
        assert.equal(element.newContent, '');
        assert.equal(element.content, '');
        assert.equal(element.type, 'text/javascript');
      });
    });

    test('content and type is undefined', () => {
      stubRestApi('getFileContent').returns(
        Promise.resolve({...new Response(), ok: true})
      );
      element.viewState = {
        ...createEditViewState(),
        changeNum: 1 as NumericChangeId,
        patchNum: EDIT,
        editView: {path: 'test/path'},
      };

      return element.getFileData().then(() => {
        assert.equal(element.newContent, '');
        assert.equal(element.content, '');
        assert.equal(element.type, '');
      });
    });
  });

  test('showAlert', async () => {
    const promise = mockPromise();
    element.addEventListener(EventType.SHOW_ALERT, e => {
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
    const setUrlStub = sinon.stub(testResolver(navigationToken), 'setUrl');

    element.viewEditInChangeView();

    assert.isTrue(setUrlStub.called);
    assert.equal(
      setUrlStub.lastCall.firstArg,
      '/c/test-project/+/42,edit?forceReload=true'
    );
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
        pressKey(element, 's', Modifier.CTRL_KEY);
        await element.updateComplete;

        assert.isTrue(handleSpy.calledOnce);
        assert.isTrue(saveStub.calledOnce);

        pressKey(element, 's', Modifier.META_KEY);
        await element.updateComplete;

        assert.equal(handleSpy.callCount, 2);
        assert.equal(saveStub.callCount, 2);
      });

      test('save disabled', async () => {
        pressKey(element, 's', Modifier.CTRL_KEY);
        await element.updateComplete;

        assert.isTrue(handleSpy.calledOnce);
        assert.isFalse(saveStub.called);

        pressKey(element, 's', Modifier.META_KEY);
        await element.updateComplete;

        assert.equal(handleSpy.callCount, 2);
        assert.isFalse(saveStub.called);
      });
    });
  });

  suite('gr-storage caching', () => {
    test('local edit exists', () => {
      sinon.stub(storageService, 'getEditableContentItem').returns({
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
      element.viewState = {
        ...createEditViewState(),
        changeNum: 1 as NumericChangeId,
        patchNum: 1 as RevisionPatchSetNum,
        editView: {path: 'test'},
      };

      const alertStub = sinon.stub();
      element.addEventListener(EventType.SHOW_ALERT, alertStub);

      return element.getFileData().then(async () => {
        await element.updateComplete;

        assert.isTrue(alertStub.called);
        assert.equal(element.newContent, 'pending edit');
        assert.equal(element.content, 'old content');
        assert.equal(element.type, 'text/javascript');
      });
    });

    test('local edit exists, is same as remote edit', () => {
      sinon.stub(storageService, 'getEditableContentItem').returns({
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
      element.viewState = {
        ...createEditViewState(),
        changeNum: 1 as NumericChangeId,
        patchNum: 1 as RevisionPatchSetNum,
        editView: {path: 'test'},
      };

      const alertStub = sinon.stub();
      element.addEventListener(EventType.SHOW_ALERT, alertStub);

      return element.getFileData().then(async () => {
        await element.updateComplete;

        assert.isFalse(alertStub.called);
        assert.equal(element.newContent, 'pending edit');
        assert.equal(element.content, 'pending edit');
        assert.equal(element.type, 'text/javascript');
      });
    });

    test('storage key computation', () => {
      element.viewState = {
        ...createEditViewState(),
        changeNum: 1 as NumericChangeId,
        patchNum: 1 as RevisionPatchSetNum,
        editView: {path: 'test'},
      };
      assert.equal(element.storageKey, 'c1_ps1_test');
    });
  });
});
