/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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
import {
  Action,
  Category,
  Link,
  LinkIcon,
  RunStatus,
  TagColor,
} from '../../api/checks';
import {CheckRun} from './checks-model';

// TODO(brohlfs): Eventually these fakes should be removed. But they have proven
// to be super convenient for testing, debugging and demoing, so I would like to
// keep them around for a few quarters. Maybe remove by EOY 2022?

export const fakeRun0: CheckRun = {
  pluginName: 'f0',
  internalRunId: 'f0',
  checkName: 'FAKE Error Finder Finder Finder Finder Finder Finder Finder',
  labelName: 'Presubmit',
  isSingleAttempt: true,
  isLatestAttempt: true,
  attemptDetails: [],
  results: [
    {
      internalResultId: 'f0r0',
      category: Category.ERROR,
      summary: 'I would like to point out this error: 1 is not equal to 2!',
      links: [
        {primary: true, url: 'https://www.google.com', icon: LinkIcon.EXTERNAL},
      ],
      tags: [{name: 'OBSOLETE'}, {name: 'E2E'}],
    },
    {
      internalResultId: 'f0r1',
      category: Category.ERROR,
      summary: 'Running the mighty test has failed by crashing.',
      message: 'Btw, 1 is also not equal to 3. Did you know?',
      actions: [
        {
          name: 'Ignore',
          tooltip: 'Ignore this result',
          primary: true,
          callback: () => Promise.resolve({message: 'fake "ignore" triggered'}),
        },
        {
          name: 'Flag',
          tooltip: 'Flag this result as totally absolutely really not useful',
          primary: true,
          disabled: true,
          callback: () => Promise.resolve({message: 'flag "flag" triggered'}),
        },
        {
          name: 'Upload',
          tooltip: 'Upload the result to the super cloud.',
          primary: false,
          callback: () => Promise.resolve({message: 'fake "upload" triggered'}),
        },
      ],
      tags: [{name: 'INTERRUPTED', color: TagColor.BROWN}, {name: 'WINDOWS'}],
      links: [
        {primary: false, url: 'https://google.com', icon: LinkIcon.EXTERNAL},
        {primary: true, url: 'https://google.com', icon: LinkIcon.DOWNLOAD},
        {
          primary: true,
          url: 'https://google.com',
          icon: LinkIcon.DOWNLOAD_MOBILE,
        },
        {primary: true, url: 'https://google.com', icon: LinkIcon.IMAGE},
        {primary: true, url: 'https://google.com', icon: LinkIcon.IMAGE},
        {primary: false, url: 'https://google.com', icon: LinkIcon.IMAGE},
        {primary: true, url: 'https://google.com', icon: LinkIcon.REPORT_BUG},
        {primary: true, url: 'https://google.com', icon: LinkIcon.HELP_PAGE},
        {primary: true, url: 'https://google.com', icon: LinkIcon.HISTORY},
      ],
    },
  ],
  status: RunStatus.COMPLETED,
};

export const fakeRun1: CheckRun = {
  pluginName: 'f1',
  internalRunId: 'f1',
  checkName: 'FAKE Super Check',
  statusLink: 'https://www.google.com/',
  startedTimestamp: new Date('2021-12-21T04:24:25'),
  finishedTimestamp: new Date(new Date().getTime() + 5 * 60 * 1000),
  patchset: 1,
  labelName: 'Verified',
  isSingleAttempt: true,
  isLatestAttempt: true,
  attemptDetails: [],
  results: [
    {
      internalResultId: 'f1r0',
      category: Category.WARNING,
      summary: 'We think that you could improve this.',
      message: `There is a lot to be said. A lot. I say, a lot.\n
                So please keep reading.`,
      tags: [{name: 'INTERRUPTED', color: TagColor.PURPLE}, {name: 'WINDOWS'}],
      codePointers: [
        {
          path: '/COMMIT_MSG',
          range: {
            start_line: 10,
            start_character: 0,
            end_line: 10,
            end_character: 0,
          },
        },
        {
          path: 'polygerrit-ui/app/api/checks.ts',
          range: {
            start_line: 5,
            start_character: 0,
            end_line: 7,
            end_character: 0,
          },
        },
      ],
      links: [
        {primary: true, url: 'https://google.com', icon: LinkIcon.EXTERNAL},
        {primary: true, url: 'https://google.com', icon: LinkIcon.DOWNLOAD},
        {
          primary: true,
          url: 'https://google.com',
          icon: LinkIcon.DOWNLOAD_MOBILE,
        },
        {primary: true, url: 'https://google.com', icon: LinkIcon.IMAGE},
        {
          primary: false,
          url: 'https://google.com',
          tooltip: 'look at this',
          icon: LinkIcon.IMAGE,
        },
        {
          primary: false,
          url: 'https://google.com',
          tooltip: 'not at this',
          icon: LinkIcon.IMAGE,
        },
      ],
    },
  ],
  status: RunStatus.RUNNING,
};

export const fakeRun2: CheckRun = {
  pluginName: 'f2',
  internalRunId: 'f2',
  checkName: 'FAKE Mega Analysis',
  statusDescription: 'This run is nearly completed, but not quite.',
  statusLink: 'https://www.google.com/',
  checkDescription:
    'From what the title says you can tell that this check analyses.',
  checkLink: 'https://www.google.com/',
  scheduledTimestamp: new Date('2021-04-01T03:14:15'),
  startedTimestamp: new Date('2021-04-01T04:24:25'),
  finishedTimestamp: new Date('2021-04-01T04:44:44'),
  isSingleAttempt: true,
  isLatestAttempt: true,
  attemptDetails: [],
  actions: [
    {
      name: 'Re-Run',
      tooltip: 'More powerful run than before',
      primary: true,
      callback: () => Promise.resolve({message: 'fake "re-run" triggered'}),
    },
    {
      name: 'Monetize',
      primary: true,
      disabled: true,
      callback: () => Promise.resolve({message: 'fake "monetize" triggered'}),
    },
    {
      name: 'Delete',
      primary: true,
      callback: () => Promise.resolve({message: 'fake "delete" triggered'}),
    },
  ],
  results: [
    {
      internalResultId: 'f2r0',
      category: Category.INFO,
      summary: 'This is looking a bit too large.',
      message: `We are still looking into how large exactly. Stay tuned.
And have a look at https://www.google.com!

Or have a look at change 30000.
Example code:
  const constable = '';
  var variable = '';`,
      tags: [{name: 'FLAKY'}, {name: 'MAC-OS'}],
    },
  ],
  status: RunStatus.COMPLETED,
};

export const fakeRun3: CheckRun = {
  pluginName: 'f3',
  internalRunId: 'f3',
  checkName: 'FAKE Critical Observations',
  status: RunStatus.RUNNABLE,
  isSingleAttempt: true,
  isLatestAttempt: true,
  attemptDetails: [],
};

export const fakeRun4_1: CheckRun = {
  pluginName: 'f4',
  internalRunId: 'f4',
  checkName: 'FAKE Elimination Long Long Long Long Long',
  status: RunStatus.RUNNABLE,
  attempt: 1,
  isSingleAttempt: false,
  isLatestAttempt: false,
  attemptDetails: [],
};

export const fakeRun4_2: CheckRun = {
  pluginName: 'f4',
  internalRunId: 'f4',
  checkName: 'FAKE Elimination Long Long Long Long Long',
  status: RunStatus.COMPLETED,
  attempt: 2,
  isSingleAttempt: false,
  isLatestAttempt: false,
  attemptDetails: [],
  results: [
    {
      internalResultId: 'f42r0',
      category: Category.INFO,
      summary: 'Please eliminate all the TODOs!',
    },
  ],
};

export const fakeRun4_3: CheckRun = {
  pluginName: 'f4',
  internalRunId: 'f4',
  checkName: 'FAKE Elimination Long Long Long Long Long',
  status: RunStatus.COMPLETED,
  attempt: 3,
  isSingleAttempt: false,
  isLatestAttempt: false,
  attemptDetails: [],
  results: [
    {
      internalResultId: 'f43r0',
      category: Category.ERROR,
      summary: 'Without eliminating all the TODOs your change will break!',
    },
  ],
};

export const fakeRun4_4: CheckRun = {
  pluginName: 'f4',
  internalRunId: 'f4',
  checkName: 'FAKE Elimination Long Long Long Long Long',
  checkDescription: 'Shows you the possible eliminations.',
  checkLink: 'https://www.google.com',
  status: RunStatus.COMPLETED,
  statusDescription: 'Everything was eliminated already.',
  statusLink: 'https://www.google.com',
  attempt: 40,
  scheduledTimestamp: new Date('2021-04-02T03:14:15'),
  startedTimestamp: new Date('2021-04-02T04:24:25'),
  finishedTimestamp: new Date('2021-04-02T04:25:44'),
  isSingleAttempt: false,
  isLatestAttempt: true,
  attemptDetails: [],
  results: [
    {
      internalResultId: 'f44r0',
      category: Category.INFO,
      summary: 'Dont be afraid. All TODOs will be eliminated.',
      actions: [
        {
          name: 'Re-Run',
          tooltip: 'More powerful run than before with a long tooltip, really.',
          primary: true,
          callback: () => Promise.resolve({message: 'fake "re-run" triggered'}),
        },
      ],
    },
  ],
  actions: [
    {
      name: 'Re-Run',
      tooltip: 'small',
      primary: true,
      callback: () => Promise.resolve({message: 'fake "re-run" triggered'}),
    },
  ],
};

export function fakeRun4CreateAttempts(from: number, to: number): CheckRun[] {
  const runs: CheckRun[] = [];
  for (let i = from; i < to; i++) {
    runs.push(fakeRun4CreateAttempt(i));
  }
  return runs;
}

export function fakeRun4CreateAttempt(attempt: number): CheckRun {
  return {
    pluginName: 'f4',
    internalRunId: 'f4',
    checkName: 'FAKE Elimination Long Long Long Long Long',
    status: RunStatus.COMPLETED,
    attempt,
    isSingleAttempt: false,
    isLatestAttempt: false,
    attemptDetails: [],
    results:
      attempt % 2 === 0
        ? [
            {
              internalResultId: 'f43r0',
              category: Category.ERROR,
              summary:
                'Without eliminating all the TODOs your change will break!',
            },
          ]
        : [],
  };
}

export const fakeRun4Att = [
  fakeRun4_1,
  fakeRun4_2,
  fakeRun4_3,
  ...fakeRun4CreateAttempts(5, 40),
  fakeRun4_4,
];

export const fakeActions: Action[] = [
  {
    name: 'Fake Action 1',
    primary: true,
    disabled: true,
    tooltip: 'Tooltip for Fake Action 1',
    callback: () => Promise.resolve({message: 'fake action 1 triggered'}),
  },
  {
    name: 'Fake Action 2',
    primary: false,
    disabled: true,
    tooltip: 'Tooltip for Fake Action 2',
    callback: () => Promise.resolve({message: 'fake action 2 triggered'}),
  },
  {
    name: 'Fake Action 3',
    summary: true,
    primary: false,
    tooltip: 'Tooltip for Fake Action 3',
    callback: () => Promise.resolve({message: 'fake action 3 triggered'}),
  },
];

export const fakeLinks: Link[] = [
  {
    url: 'https://www.google.com',
    primary: true,
    tooltip: 'Fake Bug Report 1',
    icon: LinkIcon.REPORT_BUG,
  },
  {
    url: 'https://www.google.com',
    primary: true,
    tooltip: 'Fake Bug Report 2',
    icon: LinkIcon.REPORT_BUG,
  },
  {
    url: 'https://www.google.com',
    primary: true,
    tooltip: 'Fake Link 1',
    icon: LinkIcon.EXTERNAL,
  },
  {
    url: 'https://www.google.com',
    primary: false,
    tooltip: 'Fake Link 2',
    icon: LinkIcon.EXTERNAL,
  },
  {
    url: 'https://www.google.com',
    primary: true,
    tooltip: 'Fake Code Link',
    icon: LinkIcon.CODE,
  },
  {
    url: 'https://www.google.com',
    primary: true,
    tooltip: 'Fake Image Link',
    icon: LinkIcon.IMAGE,
  },
  {
    url: 'https://www.google.com',
    primary: true,
    tooltip: 'Fake Help Link',
    icon: LinkIcon.HELP_PAGE,
  },
];

export const fakeRun5: CheckRun = {
  pluginName: 'f5',
  internalRunId: 'f5',
  checkName: 'FAKE Of Tomorrow',
  status: RunStatus.SCHEDULED,
  isSingleAttempt: true,
  isLatestAttempt: true,
  attemptDetails: [],
};
