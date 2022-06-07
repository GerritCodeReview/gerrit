/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import {tap} from '@polymer/iron-test-helpers/mock-interactions';
import {
  createChange,
  createCommit,
  createDownloadInfo,
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
import {mockPromise, queryAll, queryAndAssert} from '../../../test/test-utils';
import {GrDownloadCommands} from '../../shared/gr-download-commands/gr-download-commands';

const basicFixture = fixtureFromElement('gr-download-dialog');

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
    element = basicFixture.instantiate();
    element.patchNum = 1 as PatchSetNum;
    element.config = createDownloadInfo();
    await element.updateComplete;
  });

  test('anchors use download attribute', () => {
    const anchors = Array.from(queryAll(element, 'a'));
    assert.isTrue(!anchors.some(a => !a.hasAttribute('download')));
  });

  suite('gr-download-dialog tests with no fetch options', () => {
    setup(async () => {
      element.change = {
        ...createChange(),
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
        ...createChange(),
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
      const closeButton = queryAndAssert(
        element,
        '.closeButtonContainer gr-button'
      );
      tap(closeButton);
      await closeCalled;
    });
  });

  test('computeHidePatchFile', () => {
    element.patchNum = 1 as PatchSetNum;

    element.change = {
      ...createChange(),
      revisions: {
        r1: {...createRevision(), commit: createCommit()},
      },
    };
    assert.isTrue(element.computeHidePatchFile());

    element.change = {
      ...createChange(),
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
      ...createChange(),
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
