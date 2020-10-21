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
import '../types/globals';
import {_testOnly_resetPluginLoader} from '../elements/shared/gr-js-api-interface/gr-plugin-loader';
import {testOnly_resetInternalState} from '../elements/shared/gr-js-api-interface/gr-api-utils';
import {_testOnly_resetEndpoints} from '../elements/shared/gr-js-api-interface/gr-plugin-endpoints';
import {
  _testOnly_getShortcutManagerInstance,
  Shortcut,
} from '../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {ChangeStatus, RevisionKind} from '../constants/constants';
import {
  AccountInfo,
  BranchName,
  ChangeId,
  ChangeInfo,
  ChangeInfoId,
  ChangeMessageId,
  ChangeMessageInfo,
  CommitInfo,
  GitPersonInfo,
  GitRef,
  NumericChangeId,
  PatchSetNum,
  RepoName,
  RevisionInfo,
  Timestamp,
  TimezoneOffset,
} from '../types/common';
import {formatDate} from '../utils/date-util';

export interface MockPromise extends Promise<unknown> {
  resolve: (value?: unknown) => void;
}

export const mockPromise = () => {
  let res: (value?: unknown) => void;
  const promise: MockPromise = new Promise(resolve => {
    res = resolve;
  }) as MockPromise;
  promise.resolve = res!;
  return promise;
};
export const isHidden = (el: Element) =>
  getComputedStyle(el).display === 'none';

// Some tests/elements can define its own binding. We want to restore bindings
// at the end of the test. The TestKeyboardShortcutBinder store bindings in
// stack, so it is possible to override bindings in nested suites.
export class TestKeyboardShortcutBinder {
  private static stack: TestKeyboardShortcutBinder[] = [];

  static push() {
    const testBinder = new TestKeyboardShortcutBinder();
    this.stack.push(testBinder);
    return _testOnly_getShortcutManagerInstance();
  }

  static pop() {
    const item = this.stack.pop();
    if (!item) {
      throw new Error('stack is empty');
    }
    item._restoreShortcuts();
  }

  private readonly originalBinding: Map<Shortcut, string[]>;

  constructor() {
    this.originalBinding = new Map(
      _testOnly_getShortcutManagerInstance()._testOnly_getBindings()
    );
  }

  _restoreShortcuts() {
    const bindings = _testOnly_getShortcutManagerInstance()._testOnly_getBindings();
    bindings.clear();
    this.originalBinding.forEach((value, key) => {
      bindings.set(key, value);
    });
  }
}

// Provide reset plugins function to clear installed plugins between tests.
// No gr-app found (running tests)
export const resetPlugins = () => {
  testOnly_resetInternalState();
  _testOnly_resetEndpoints();
  const pl = _testOnly_resetPluginLoader();
  pl.loadPlugins([]);
};

export type CleanupCallback = () => void;

const cleanups: CleanupCallback[] = [];

export function getCleanupsCount() {
  return cleanups.length;
}

export function registerTestCleanup(cleanupCallback: CleanupCallback) {
  cleanups.push(cleanupCallback);
}

export function cleanupTestUtils() {
  cleanups.forEach(cleanup => cleanup());
  cleanups.splice(0);
}

export function stubBaseUrl(newUrl: string) {
  const originalCanonicalPath = window.CANONICAL_PATH;
  window.CANONICAL_PATH = newUrl;
  registerTestCleanup(() => (window.CANONICAL_PATH = originalCanonicalPath));
}

export interface GenerateChangeOptions {
  revisionsCount?: number;
  messagesCount?: number;
  status: ChangeStatus;
}

export function dateToTimestamp(date: Date): Timestamp {
  const nanosecondSuffix = '.000000000';
  return (formatDate(date, 'YYYY-MM-DD HH:mm:ss') +
    nanosecondSuffix) as Timestamp;
}

export function generateChange(options: GenerateChangeOptions) {
  const project = 'testRepo' as RepoName;
  const branch = 'test_branch' as BranchName;
  const changeId = 'abcdef' as ChangeId;
  const id = `${project}~${branch}~${changeId}` as ChangeInfoId;
  const owner: AccountInfo = {};
  const createdDate = new Date(2020, 1, 1, 1, 2, 3);

  const change: ChangeInfo = {
    _number: 42 as NumericChangeId,
    project,
    branch,
    change_id: changeId,
    created: dateToTimestamp(createdDate),
    deletions: 0,
    id,
    insertions: 0,
    owner,
    reviewers: {},
    status: options?.status ?? ChangeStatus.NEW,
    subject: '',
    submitter: owner,
    updated: dateToTimestamp(new Date(2020, 10, 5, 1, 2, 3)),
  };
  const revisionIdStart = 1;
  const messageIdStart = 1000;
  // We want to distinguish between empty arrays/objects and undefined
  // If an option is not set - the appropriate property is not set
  // If an options is set - the property always set
  if (options && typeof options.revisionsCount !== 'undefined') {
    const revisions: {[revisionId: string]: RevisionInfo} = {};
    const revisionDate = createdDate;
    for (let i = 0; i < options.revisionsCount; i++) {
      const revisionId = (i + revisionIdStart).toString(16);
      const person: GitPersonInfo = {
        name: 'Test person',
        email: 'email@google.com',
        date: dateToTimestamp(new Date(2019, 11, 6, 14, 5, 8)),
        tz: 0 as TimezoneOffset,
      };
      const commit: CommitInfo = {
        parents: [],
        author: person,
        committer: person,
        subject: 'Test commit subject',
        message: 'Test commit message',
      };
      const revision: RevisionInfo = {
        _number: (i + 1) as PatchSetNum,
        commit,
        created: dateToTimestamp(revisionDate),
        kind: RevisionKind.REWORK,
        ref: `refs/changes/5/6/${i + 1}` as GitRef,
        uploader: owner,
      };
      revisions[revisionId] = revision;
      // advance 1 day
      revisionDate.setDate(revisionDate.getDate() + 1);
    }
    change.revisions = revisions;
  }
  if (options && typeof options.messagesCount !== 'undefined') {
    const messages: ChangeMessageInfo[] = [];
    for (let i = 0; i < options.messagesCount; i++) {
      messages.push({
        id: (i + messageIdStart).toString(16) as ChangeMessageId,
        date: '2020-01-01 00:00:00.000000000' as Timestamp,
        message: `This is a message N${i + 1}`,
      });
    }
    change.messages = messages;
  }
  if (options && options.status) {
    change.status = options.status;
  }
  return change;
}

/**
 * Forcing an opacity of 0 onto the ironOverlayBackdrop is required, because
 * otherwise the backdrop stays around in the DOM for too long waiting for
 * an animation to finish. This could be considered to be moved to a
 * common-test-setup file.
 */
export function createIronOverlayBackdropStyleEl() {
  const ironOverlayBackdropStyleEl = document.createElement('style');
  document.head.appendChild(ironOverlayBackdropStyleEl);
  ironOverlayBackdropStyleEl.sheet!.insertRule(
    'body { --iron-overlay-backdrop-opacity: 0; }'
  );
  return ironOverlayBackdropStyleEl;
}
