/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import {
  createChange,
  createCommit,
  createDownloadInfo,
  createParsedChange,
  createRevision,
} from '../../../test/test-data-generators';
import {
  CommitId,
  NumericChangeId,
  PatchSetNum,
  RepoName,
} from '../../../types/common';
import './gr-download-dialog';
import {GrDownloadDialog} from './gr-download-dialog';
import {
  mockPromise,
  queryAll,
  queryAndAssert,
  waitUntil,
} from '../../../test/test-utils';
import {GrDownloadCommands} from '../../shared/gr-download-commands/gr-download-commands';
import {fixture, html, assert} from '@open-wc/testing';
import {GrButton} from '../../shared/gr-button/gr-button';

function getChangeObject() {
  return {
    ...createChange(),
    current_revision: '34685798fe548b6d17d1e8e5edc43a26d055cc72' as CommitId,
    revisions: {
      '34685798fe548b6d17d1e8e5edc43a26d055cc72': {
        ...createRevision(),
        commit: createCommit(),
        fetch: {
          repo: {
            url: 'my.url',
            ref: 'refs/changes/5/6/1',
            commands: {
              repo: 'repo download test-project 5/1',
            },
          },
          ssh: {
            url: 'my.url',
            ref: 'refs/changes/5/6/1',
            commands: {
              Checkout:
                'git fetch ' +
                'ssh://andybons@localhost:29418/test-project ' +
                'refs/changes/05/5/1 && git checkout FETCH_HEAD',
              'Cherry Pick':
                'git fetch ' +
                'ssh://andybons@localhost:29418/test-project ' +
                'refs/changes/05/5/1 && git cherry-pick FETCH_HEAD',
              'Format Patch':
                'git fetch ' +
                'ssh://andybons@localhost:29418/test-project ' +
                'refs/changes/05/5/1 ' +
                '&& git format-patch -1 --stdout FETCH_HEAD',
              Pull:
                'git pull ' +
                'ssh://andybons@localhost:29418/test-project ' +
                'refs/changes/05/5/1',
            },
          },
          http: {
            url: 'my.url',
            ref: 'refs/changes/5/6/1',
            commands: {
              Checkout:
                'git fetch ' +
                'http://andybons@localhost:8080/a/test-project ' +
                'refs/changes/05/5/1 && git checkout FETCH_HEAD',
              'Cherry Pick':
                'git fetch ' +
                'http://andybons@localhost:8080/a/test-project ' +
                'refs/changes/05/5/1 && git cherry-pick FETCH_HEAD',
              'Format Patch':
                'git fetch ' +
                'http://andybons@localhost:8080/a/test-project ' +
                'refs/changes/05/5/1 && ' +
                'git format-patch -1 --stdout FETCH_HEAD',
              Pull:
                'git pull ' +
                'http://andybons@localhost:8080/a/test-project ' +
                'refs/changes/05/5/1',
            },
          },
        },
      },
    },
  };
}

suite('gr-download-dialog', () => {
  let element: GrDownloadDialog;

  setup(async () => {
    element = await fixture(html`<gr-download-dialog></gr-download-dialog>`);
    element.patchNum = 1 as PatchSetNum;
    element.config = createDownloadInfo();
    await element.updateComplete;
  });

  test('render', () => {
    // prettier and shadowDom string don't agree on the long text in the h3
    assert.shadowDom.equal(
      element,
      /* prettier-ignore */ /* HTML */ `
      <section>
        <h3 class="heading-3">
          Patch set 1 of
          0
        </h3>
      </section>
      <section class="hidden">
        <gr-download-commands
          id="downloadCommands"
          show-keyboard-shortcut-tooltips=""
        >
        </gr-download-commands>
      </section>
      <section class="flexContainer">
        <div class="patchFiles">
          <label> Patch file </label>
          <div>
            <a download="" href="" id="download"> </a>
            <a download="" href=""> </a>
            <a download="" href=""> </a>
          </div>
        </div>
        <div class="archivesContainer">
          <label> Archive </label>
          <div class="archives" id="archives">
            <a download="" href=""> tgz </a>
            <a download="" href=""> tar </a>
          </div>
        </div>
      </section>
      <section class="footer">
        <span class="closeButtonContainer">
          <gr-button
            aria-disabled="false"
            id="closeButton"
            link=""
            role="button"
            tabindex="0"
          >
            Close
          </gr-button>
        </span>
      </section>
    `
    );
  });

  test('closes when gr-download-commands fires item-selected', async () => {
    const fireStub = sinon.stub(element, 'dispatchEvent');
    const commands = queryAndAssert<GrDownloadCommands>(
      element,
      'gr-download-commands'
    );
    commands.dispatchEvent(new CustomEvent('item-copied'));

    await waitUntil(() => fireStub.called);

    const events = fireStub.args.map(arg => arg[0].type || '');
    assert.isTrue(events.includes('close'));
  });

  test('anchors use download attribute', () => {
    const anchors = Array.from(queryAll(element, 'a'));
    assert.isTrue(!anchors.some(a => !a.hasAttribute('download')));
  });

  suite('gr-download-dialog tests with no fetch options', () => {
    setup(async () => {
      element.change = {
        ...createParsedChange(),
        revisions: {
          r1: {
            ...createRevision(),
            commit: {
              ...createCommit(),
              parents: [{commit: 'p1' as CommitId, subject: 'subject1'}],
            },
          },
        },
      };
      await element.updateComplete;
    });

    test('focuses on first download link if no copy links', () => {
      const focusStub = sinon.stub(
        queryAndAssert<HTMLAnchorElement>(element, '#download'),
        'focus'
      );
      element.focus();
      assert.isTrue(focusStub.called);
      focusStub.restore();
    });
  });

  suite('gr-download-dialog with fetch options', () => {
    setup(async () => {
      element.change = getChangeObject();
      await element.updateComplete;
    });

    test('focuses on first copy link', async () => {
      const focusStub = sinon.stub(
        queryAndAssert<GrDownloadCommands>(element, '#downloadCommands'),
        'focusOnCopy'
      );
      element.focus();
      await element.updateComplete;
      assert.isTrue(focusStub.called);
      focusStub.restore();
    });

    test('computed fields', () => {
      element.change = {
        ...createParsedChange(),
        project: 'test/project' as RepoName,
        _number: 123 as NumericChangeId,
      };
      element.patchNum = 2 as PatchSetNum;
      assert.equal(
        element.computeArchiveDownloadLink('tgz'),
        '/changes/test%2Fproject~123/revisions/2/archive?format=tgz'
      );
    });

    test('close event', async () => {
      const closeCalled = mockPromise();
      element.addEventListener('close', () => {
        closeCalled.resolve();
      });
      const closeButton = queryAndAssert<GrButton>(
        element,
        '.closeButtonContainer gr-button'
      );
      closeButton.click();
      await closeCalled;
    });
  });

  test('computeHidePatchFile', () => {
    element.patchNum = 1 as PatchSetNum;

    element.change = {
      ...createParsedChange(),
      revisions: {
        r1: {...createRevision(), commit: createCommit()},
      },
    };
    assert.isTrue(element.computeHidePatchFile());

    element.change = {
      ...createParsedChange(),
      revisions: {
        r1: {
          ...createRevision(),
          commit: {
            ...createCommit(),
            parents: [{commit: 'p1' as CommitId, subject: 'subject1'}],
          },
        },
      },
    };
    assert.isFalse(element.computeHidePatchFile());

    element.change = {
      ...createParsedChange(),
      revisions: {
        r1: {
          ...createRevision(),
          commit: {
            ...createCommit(),
            parents: [
              {commit: 'p1' as CommitId, subject: 'subject1'},
              {commit: 'p2' as CommitId, subject: 'subject2'},
            ],
          },
        },
      },
    };
    assert.isTrue(element.computeHidePatchFile());
  });
});
