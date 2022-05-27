/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  Action,
  Category,
  Link,
  LinkIcon,
  RunStatus,
  TagColor,
} from '../../api/checks';
import {CheckRun, ChecksModel, ChecksPatchset} from './checks-model';

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
  startedTimestamp: new Date(new Date().getTime() - 5 * 60 * 1000),
  finishedTimestamp: new Date(new Date().getTime() + 5 * 60 * 1000),
  patchset: 2,
  labelName: 'Verified',
  isSingleAttempt: true,
  isLatestAttempt: true,
  attemptDetails: [],
  results: [
    {
      internalResultId: 'f1r0',
      category: Category.WARNING,
      summary: 'We think that you could improve this.',
      message: `There is a lot to be said. A lot. I say, a lot.
                So please keep reading.`,
      tags: [{name: 'INTERRUPTED', color: TagColor.PURPLE}, {name: 'WINDOWS'}],
      codePointers: [
        {
          path: '/COMMIT_MSG',
          range: {
            start_line: 7,
            start_character: 5,
            end_line: 9,
            end_character: 20,
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
    {
      internalResultId: 'f1r1',
      category: Category.INFO,
      summary: 'Suspicious Author',
      message: 'Do you personally know this person?',
      codePointers: [
        {
          path: '/COMMIT_MSG',
          range: {
            start_line: 2,
            start_character: 0,
            end_line: 2,
            end_character: 0,
          },
        },
      ],
      links: [],
    },
    {
      internalResultId: 'f1r2',
      category: Category.ERROR,
      summary: 'Suspicious Date',
      message: 'That was a holiday, you know.',
      codePointers: [
        {
          path: '/COMMIT_MSG',
          range: {
            start_line: 3,
            start_character: 0,
            end_line: 3,
            end_character: 0,
          },
        },
      ],
      links: [],
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

export function clearAllFakeRuns(model: ChecksModel) {
  model.updateStateSetProvider('f0', ChecksPatchset.LATEST);
  model.updateStateSetProvider('f1', ChecksPatchset.LATEST);
  model.updateStateSetProvider('f2', ChecksPatchset.LATEST);
  model.updateStateSetProvider('f3', ChecksPatchset.LATEST);
  model.updateStateSetProvider('f4', ChecksPatchset.LATEST);
  model.updateStateSetProvider('f5', ChecksPatchset.LATEST);
  model.updateStateSetResults(
    'f0',
    [],
    [],
    [],
    undefined,
    ChecksPatchset.LATEST
  );
  model.updateStateSetResults(
    'f1',
    [],
    [],
    [],
    undefined,
    ChecksPatchset.LATEST
  );
  model.updateStateSetResults(
    'f2',
    [],
    [],
    [],
    undefined,
    ChecksPatchset.LATEST
  );
  model.updateStateSetResults(
    'f3',
    [],
    [],
    [],
    undefined,
    ChecksPatchset.LATEST
  );
  model.updateStateSetResults(
    'f4',
    [],
    [],
    [],
    undefined,
    ChecksPatchset.LATEST
  );
  model.updateStateSetResults(
    'f5',
    [],
    [],
    [],
    undefined,
    ChecksPatchset.LATEST
  );
}

export function setAllFakeRuns(model: ChecksModel) {
  model.updateStateSetProvider('f0', ChecksPatchset.LATEST);
  model.updateStateSetProvider('f1', ChecksPatchset.LATEST);
  model.updateStateSetProvider('f2', ChecksPatchset.LATEST);
  model.updateStateSetProvider('f3', ChecksPatchset.LATEST);
  model.updateStateSetProvider('f4', ChecksPatchset.LATEST);
  model.updateStateSetProvider('f5', ChecksPatchset.LATEST);
  model.updateStateSetResults(
    'f0',
    [fakeRun0],
    fakeActions,
    fakeLinks,
    'ETA: 1 min',
    ChecksPatchset.LATEST
  );
  model.updateStateSetResults(
    'f1',
    [fakeRun1],
    [],
    [],
    undefined,
    ChecksPatchset.LATEST
  );
  model.updateStateSetResults(
    'f2',
    [fakeRun2],
    [],
    [],
    undefined,
    ChecksPatchset.LATEST
  );
  model.updateStateSetResults(
    'f3',
    [fakeRun3],
    [],
    [],
    undefined,
    ChecksPatchset.LATEST
  );
  model.updateStateSetResults(
    'f4',
    fakeRun4Att,
    [],
    [],
    undefined,
    ChecksPatchset.LATEST
  );
  model.updateStateSetResults(
    'f5',
    [fakeRun5],
    [],
    [],
    undefined,
    ChecksPatchset.LATEST
  );
}
