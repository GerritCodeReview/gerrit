/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-repo-detail-list';
import {GrRepoDetailList} from './gr-repo-detail-list';
import {page} from '../../../utils/page-wrapper-utils';
import {
  addListenerForTest,
  mockPromise,
  queryAll,
  queryAndAssert,
  stubRestApi,
} from '../../../test/test-utils';
import {
  BranchInfo,
  EmailAddress,
  GitRef,
  GroupId,
  GroupName,
  RepoAccessGroups,
  RepoAccessInfoMap,
  RepoName,
  TagInfo,
  Timestamp,
  TimezoneOffset,
} from '../../../types/common';
import {GerritView} from '../../../services/router/router-model';
import {GrButton} from '../../shared/gr-button/gr-button';
import {PageErrorEvent} from '../../../types/events';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import {GrListView} from '../../shared/gr-list-view/gr-list-view';
import {SHOWN_ITEMS_COUNT} from '../../../constants/constants';
import {fixture, html, assert} from '@open-wc/testing';
import {RepoDetailView} from '../../../models/views/repo';

function branchGenerator(counter: number) {
  return {
    ref: `refs/heads/test${counter}` as GitRef,
    revision: '9c9d08a438e55e52f33b608415e6dddd9b18550d',
    web_links: [
      {
        name: 'diffusion',
        url: `https://git.example.org/branch/test;refs/heads/test${counter}`,
      },
    ],
  };
}

function createBranchesList(n: number) {
  const branches = [];
  for (let i = 0; i < n; ++i) {
    branches.push(branchGenerator(i));
  }
  return branches;
}

function tagGenerator(counter: number) {
  return {
    ref: `refs/tags/test${counter}` as GitRef,
    revision: '9c9d08a438e55e52f33b608415e6dddd9b18550d',
    can_delete: false,
    web_links: [
      {
        name: 'diffusion',
        url: `https://git.example.org/tag/test;refs/tags/test${counter}`,
      },
    ],
    message: 'Annotated tag',
    tagger: {
      name: 'Test User',
      email: 'test.user@gmail.com' as EmailAddress,
      date: '2017-09-19 14:54:00.000000000' as Timestamp,
      tz: 540 as TimezoneOffset,
    },
  };
}

function createTagsList(n: number) {
  const tags = [];
  for (let i = 0; i < n; ++i) {
    tags.push(tagGenerator(i));
  }
  return tags;
}

suite('gr-repo-detail-list', () => {
  suite('Branches', () => {
    let element: GrRepoDetailList;
    let branches: BranchInfo[];

    setup(async () => {
      element = await fixture(
        html`<gr-repo-detail-list></gr-repo-detail-list>`
      );
      element.detailType = RepoDetailView.BRANCHES;
      sinon.stub(page, 'show');
    });

    suite('list of repo branches', () => {
      setup(async () => {
        branches = [
          {
            ref: 'HEAD' as GitRef,
            revision: 'master',
          },
        ].concat(createBranchesList(25));
        stubRestApi('getRepoBranches').returns(Promise.resolve(branches));

        element.params = {
          view: GerritView.REPO,
          repo: 'test' as RepoName,
          detail: RepoDetailView.BRANCHES,
        };
        await element.paramsChanged();
        await element.updateComplete;
      });

      test('render', () => {
        assert.shadowDom.equal(
          element,
          /* HTML */ `
            <gr-list-view>
              <table class="genericList gr-form-styles" id="list">
                <tbody>
                  <tr class="headerRow">
                    <th class="name topHeader">Name</th>
                    <th class="revision topHeader">Revision</th>
                    <th class="hideItem message topHeader">Message</th>
                    <th class="hideItem tagger topHeader">Tagger</th>
                    <th class="repositoryBrowser topHeader">
                      Repository Browser
                    </th>
                    <th class="delete topHeader"></th>
                  </tr>
                  <tr class="loadingMsg" id="loading">
                    <td>Loading...</td>
                  </tr>
                </tbody>
                <tbody>
                  <tr class="table">
                    <td class="branches name">
                      <a> HEAD </a>
                    </td>
                    <td class="branches revision">
                      <span class="revisionNoEditing"> master </span>
                      <span class="revisionEdit">
                        <span class="revisionWithEditing"> master </span>
                        <gr-button
                          aria-disabled="false"
                          class="editBtn"
                          data-index="0"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          edit
                        </gr-button>
                        <iron-input class="editItem">
                          <input />
                        </iron-input>
                        <gr-button
                          aria-disabled="false"
                          class="cancelBtn editItem"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          Cancel
                        </gr-button>
                        <gr-button
                          aria-disabled="true"
                          class="editItem saveBtn"
                          data-index="0"
                          disabled=""
                          link=""
                          role="button"
                          tabindex="-1"
                        >
                          Save
                        </gr-button>
                      </span>
                    </td>
                    <td class="hideItem message"></td>
                    <td class="hideItem tagger"></td>
                    <td class="repositoryBrowser"></td>
                    <td class="delete">
                      <gr-button
                        aria-disabled="false"
                        class="deleteButton"
                        data-index="0"
                        link=""
                        role="button"
                        tabindex="0"
                      >
                        Delete
                      </gr-button>
                    </td>
                  </tr>
                  <tr class="table">
                    <td class="branches name">
                      <a
                        href="https://git.example.org/branch/test;refs/heads/test0"
                      >
                        test0
                      </a>
                    </td>
                    <td class="branches revision">
                      <span class="revisionNoEditing">
                        9c9d08a438e55e52f33b608415e6dddd9b18550d
                      </span>
                      <span class="revisionEdit">
                        <span class="revisionWithEditing">
                          9c9d08a438e55e52f33b608415e6dddd9b18550d
                        </span>
                        <gr-button
                          aria-disabled="false"
                          class="editBtn"
                          data-index="1"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          edit
                        </gr-button>
                        <iron-input class="editItem">
                          <input />
                        </iron-input>
                        <gr-button
                          aria-disabled="false"
                          class="cancelBtn editItem"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          Cancel
                        </gr-button>
                        <gr-button
                          aria-disabled="true"
                          class="editItem saveBtn"
                          data-index="1"
                          disabled=""
                          link=""
                          role="button"
                          tabindex="-1"
                        >
                          Save
                        </gr-button>
                      </span>
                    </td>
                    <td class="hideItem message"></td>
                    <td class="hideItem tagger"></td>
                    <td class="repositoryBrowser">
                      <a
                        class="webLink"
                        href="https://git.example.org/branch/test;refs/heads/test0"
                        rel="noopener"
                        target="_blank"
                      >
                        (diffusion)
                      </a>
                    </td>
                    <td class="delete">
                      <gr-button
                        aria-disabled="false"
                        class="deleteButton"
                        data-index="1"
                        link=""
                        role="button"
                        tabindex="0"
                      >
                        Delete
                      </gr-button>
                    </td>
                  </tr>
                  <tr class="table">
                    <td class="branches name">
                      <a
                        href="https://git.example.org/branch/test;refs/heads/test1"
                      >
                        test1
                      </a>
                    </td>
                    <td class="branches revision">
                      <span class="revisionNoEditing">
                        9c9d08a438e55e52f33b608415e6dddd9b18550d
                      </span>
                      <span class="revisionEdit">
                        <span class="revisionWithEditing">
                          9c9d08a438e55e52f33b608415e6dddd9b18550d
                        </span>
                        <gr-button
                          aria-disabled="false"
                          class="editBtn"
                          data-index="2"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          edit
                        </gr-button>
                        <iron-input class="editItem">
                          <input />
                        </iron-input>
                        <gr-button
                          aria-disabled="false"
                          class="cancelBtn editItem"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          Cancel
                        </gr-button>
                        <gr-button
                          aria-disabled="true"
                          class="editItem saveBtn"
                          data-index="2"
                          disabled=""
                          link=""
                          role="button"
                          tabindex="-1"
                        >
                          Save
                        </gr-button>
                      </span>
                    </td>
                    <td class="hideItem message"></td>
                    <td class="hideItem tagger"></td>
                    <td class="repositoryBrowser">
                      <a
                        class="webLink"
                        href="https://git.example.org/branch/test;refs/heads/test1"
                        rel="noopener"
                        target="_blank"
                      >
                        (diffusion)
                      </a>
                    </td>
                    <td class="delete">
                      <gr-button
                        aria-disabled="false"
                        class="deleteButton"
                        data-index="2"
                        link=""
                        role="button"
                        tabindex="0"
                      >
                        Delete
                      </gr-button>
                    </td>
                  </tr>
                  <tr class="table">
                    <td class="branches name">
                      <a
                        href="https://git.example.org/branch/test;refs/heads/test2"
                      >
                        test2
                      </a>
                    </td>
                    <td class="branches revision">
                      <span class="revisionNoEditing">
                        9c9d08a438e55e52f33b608415e6dddd9b18550d
                      </span>
                      <span class="revisionEdit">
                        <span class="revisionWithEditing">
                          9c9d08a438e55e52f33b608415e6dddd9b18550d
                        </span>
                        <gr-button
                          aria-disabled="false"
                          class="editBtn"
                          data-index="3"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          edit
                        </gr-button>
                        <iron-input class="editItem">
                          <input />
                        </iron-input>
                        <gr-button
                          aria-disabled="false"
                          class="cancelBtn editItem"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          Cancel
                        </gr-button>
                        <gr-button
                          aria-disabled="true"
                          class="editItem saveBtn"
                          data-index="3"
                          disabled=""
                          link=""
                          role="button"
                          tabindex="-1"
                        >
                          Save
                        </gr-button>
                      </span>
                    </td>
                    <td class="hideItem message"></td>
                    <td class="hideItem tagger"></td>
                    <td class="repositoryBrowser">
                      <a
                        class="webLink"
                        href="https://git.example.org/branch/test;refs/heads/test2"
                        rel="noopener"
                        target="_blank"
                      >
                        (diffusion)
                      </a>
                    </td>
                    <td class="delete">
                      <gr-button
                        aria-disabled="false"
                        class="deleteButton"
                        data-index="3"
                        link=""
                        role="button"
                        tabindex="0"
                      >
                        Delete
                      </gr-button>
                    </td>
                  </tr>
                  <tr class="table">
                    <td class="branches name">
                      <a
                        href="https://git.example.org/branch/test;refs/heads/test3"
                      >
                        test3
                      </a>
                    </td>
                    <td class="branches revision">
                      <span class="revisionNoEditing">
                        9c9d08a438e55e52f33b608415e6dddd9b18550d
                      </span>
                      <span class="revisionEdit">
                        <span class="revisionWithEditing">
                          9c9d08a438e55e52f33b608415e6dddd9b18550d
                        </span>
                        <gr-button
                          aria-disabled="false"
                          class="editBtn"
                          data-index="4"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          edit
                        </gr-button>
                        <iron-input class="editItem">
                          <input />
                        </iron-input>
                        <gr-button
                          aria-disabled="false"
                          class="cancelBtn editItem"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          Cancel
                        </gr-button>
                        <gr-button
                          aria-disabled="true"
                          class="editItem saveBtn"
                          data-index="4"
                          disabled=""
                          link=""
                          role="button"
                          tabindex="-1"
                        >
                          Save
                        </gr-button>
                      </span>
                    </td>
                    <td class="hideItem message"></td>
                    <td class="hideItem tagger"></td>
                    <td class="repositoryBrowser">
                      <a
                        class="webLink"
                        href="https://git.example.org/branch/test;refs/heads/test3"
                        rel="noopener"
                        target="_blank"
                      >
                        (diffusion)
                      </a>
                    </td>
                    <td class="delete">
                      <gr-button
                        aria-disabled="false"
                        class="deleteButton"
                        data-index="4"
                        link=""
                        role="button"
                        tabindex="0"
                      >
                        Delete
                      </gr-button>
                    </td>
                  </tr>
                  <tr class="table">
                    <td class="branches name">
                      <a
                        href="https://git.example.org/branch/test;refs/heads/test4"
                      >
                        test4
                      </a>
                    </td>
                    <td class="branches revision">
                      <span class="revisionNoEditing">
                        9c9d08a438e55e52f33b608415e6dddd9b18550d
                      </span>
                      <span class="revisionEdit">
                        <span class="revisionWithEditing">
                          9c9d08a438e55e52f33b608415e6dddd9b18550d
                        </span>
                        <gr-button
                          aria-disabled="false"
                          class="editBtn"
                          data-index="5"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          edit
                        </gr-button>
                        <iron-input class="editItem">
                          <input />
                        </iron-input>
                        <gr-button
                          aria-disabled="false"
                          class="cancelBtn editItem"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          Cancel
                        </gr-button>
                        <gr-button
                          aria-disabled="true"
                          class="editItem saveBtn"
                          data-index="5"
                          disabled=""
                          link=""
                          role="button"
                          tabindex="-1"
                        >
                          Save
                        </gr-button>
                      </span>
                    </td>
                    <td class="hideItem message"></td>
                    <td class="hideItem tagger"></td>
                    <td class="repositoryBrowser">
                      <a
                        class="webLink"
                        href="https://git.example.org/branch/test;refs/heads/test4"
                        rel="noopener"
                        target="_blank"
                      >
                        (diffusion)
                      </a>
                    </td>
                    <td class="delete">
                      <gr-button
                        aria-disabled="false"
                        class="deleteButton"
                        data-index="5"
                        link=""
                        role="button"
                        tabindex="0"
                      >
                        Delete
                      </gr-button>
                    </td>
                  </tr>
                  <tr class="table">
                    <td class="branches name">
                      <a
                        href="https://git.example.org/branch/test;refs/heads/test5"
                      >
                        test5
                      </a>
                    </td>
                    <td class="branches revision">
                      <span class="revisionNoEditing">
                        9c9d08a438e55e52f33b608415e6dddd9b18550d
                      </span>
                      <span class="revisionEdit">
                        <span class="revisionWithEditing">
                          9c9d08a438e55e52f33b608415e6dddd9b18550d
                        </span>
                        <gr-button
                          aria-disabled="false"
                          class="editBtn"
                          data-index="6"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          edit
                        </gr-button>
                        <iron-input class="editItem">
                          <input />
                        </iron-input>
                        <gr-button
                          aria-disabled="false"
                          class="cancelBtn editItem"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          Cancel
                        </gr-button>
                        <gr-button
                          aria-disabled="true"
                          class="editItem saveBtn"
                          data-index="6"
                          disabled=""
                          link=""
                          role="button"
                          tabindex="-1"
                        >
                          Save
                        </gr-button>
                      </span>
                    </td>
                    <td class="hideItem message"></td>
                    <td class="hideItem tagger"></td>
                    <td class="repositoryBrowser">
                      <a
                        class="webLink"
                        href="https://git.example.org/branch/test;refs/heads/test5"
                        rel="noopener"
                        target="_blank"
                      >
                        (diffusion)
                      </a>
                    </td>
                    <td class="delete">
                      <gr-button
                        aria-disabled="false"
                        class="deleteButton"
                        data-index="6"
                        link=""
                        role="button"
                        tabindex="0"
                      >
                        Delete
                      </gr-button>
                    </td>
                  </tr>
                  <tr class="table">
                    <td class="branches name">
                      <a
                        href="https://git.example.org/branch/test;refs/heads/test6"
                      >
                        test6
                      </a>
                    </td>
                    <td class="branches revision">
                      <span class="revisionNoEditing">
                        9c9d08a438e55e52f33b608415e6dddd9b18550d
                      </span>
                      <span class="revisionEdit">
                        <span class="revisionWithEditing">
                          9c9d08a438e55e52f33b608415e6dddd9b18550d
                        </span>
                        <gr-button
                          aria-disabled="false"
                          class="editBtn"
                          data-index="7"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          edit
                        </gr-button>
                        <iron-input class="editItem">
                          <input />
                        </iron-input>
                        <gr-button
                          aria-disabled="false"
                          class="cancelBtn editItem"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          Cancel
                        </gr-button>
                        <gr-button
                          aria-disabled="true"
                          class="editItem saveBtn"
                          data-index="7"
                          disabled=""
                          link=""
                          role="button"
                          tabindex="-1"
                        >
                          Save
                        </gr-button>
                      </span>
                    </td>
                    <td class="hideItem message"></td>
                    <td class="hideItem tagger"></td>
                    <td class="repositoryBrowser">
                      <a
                        class="webLink"
                        href="https://git.example.org/branch/test;refs/heads/test6"
                        rel="noopener"
                        target="_blank"
                      >
                        (diffusion)
                      </a>
                    </td>
                    <td class="delete">
                      <gr-button
                        aria-disabled="false"
                        class="deleteButton"
                        data-index="7"
                        link=""
                        role="button"
                        tabindex="0"
                      >
                        Delete
                      </gr-button>
                    </td>
                  </tr>
                  <tr class="table">
                    <td class="branches name">
                      <a
                        href="https://git.example.org/branch/test;refs/heads/test7"
                      >
                        test7
                      </a>
                    </td>
                    <td class="branches revision">
                      <span class="revisionNoEditing">
                        9c9d08a438e55e52f33b608415e6dddd9b18550d
                      </span>
                      <span class="revisionEdit">
                        <span class="revisionWithEditing">
                          9c9d08a438e55e52f33b608415e6dddd9b18550d
                        </span>
                        <gr-button
                          aria-disabled="false"
                          class="editBtn"
                          data-index="8"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          edit
                        </gr-button>
                        <iron-input class="editItem">
                          <input />
                        </iron-input>
                        <gr-button
                          aria-disabled="false"
                          class="cancelBtn editItem"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          Cancel
                        </gr-button>
                        <gr-button
                          aria-disabled="true"
                          class="editItem saveBtn"
                          data-index="8"
                          disabled=""
                          link=""
                          role="button"
                          tabindex="-1"
                        >
                          Save
                        </gr-button>
                      </span>
                    </td>
                    <td class="hideItem message"></td>
                    <td class="hideItem tagger"></td>
                    <td class="repositoryBrowser">
                      <a
                        class="webLink"
                        href="https://git.example.org/branch/test;refs/heads/test7"
                        rel="noopener"
                        target="_blank"
                      >
                        (diffusion)
                      </a>
                    </td>
                    <td class="delete">
                      <gr-button
                        aria-disabled="false"
                        class="deleteButton"
                        data-index="8"
                        link=""
                        role="button"
                        tabindex="0"
                      >
                        Delete
                      </gr-button>
                    </td>
                  </tr>
                  <tr class="table">
                    <td class="branches name">
                      <a
                        href="https://git.example.org/branch/test;refs/heads/test8"
                      >
                        test8
                      </a>
                    </td>
                    <td class="branches revision">
                      <span class="revisionNoEditing">
                        9c9d08a438e55e52f33b608415e6dddd9b18550d
                      </span>
                      <span class="revisionEdit">
                        <span class="revisionWithEditing">
                          9c9d08a438e55e52f33b608415e6dddd9b18550d
                        </span>
                        <gr-button
                          aria-disabled="false"
                          class="editBtn"
                          data-index="9"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          edit
                        </gr-button>
                        <iron-input class="editItem">
                          <input />
                        </iron-input>
                        <gr-button
                          aria-disabled="false"
                          class="cancelBtn editItem"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          Cancel
                        </gr-button>
                        <gr-button
                          aria-disabled="true"
                          class="editItem saveBtn"
                          data-index="9"
                          disabled=""
                          link=""
                          role="button"
                          tabindex="-1"
                        >
                          Save
                        </gr-button>
                      </span>
                    </td>
                    <td class="hideItem message"></td>
                    <td class="hideItem tagger"></td>
                    <td class="repositoryBrowser">
                      <a
                        class="webLink"
                        href="https://git.example.org/branch/test;refs/heads/test8"
                        rel="noopener"
                        target="_blank"
                      >
                        (diffusion)
                      </a>
                    </td>
                    <td class="delete">
                      <gr-button
                        aria-disabled="false"
                        class="deleteButton"
                        data-index="9"
                        link=""
                        role="button"
                        tabindex="0"
                      >
                        Delete
                      </gr-button>
                    </td>
                  </tr>
                  <tr class="table">
                    <td class="branches name">
                      <a
                        href="https://git.example.org/branch/test;refs/heads/test9"
                      >
                        test9
                      </a>
                    </td>
                    <td class="branches revision">
                      <span class="revisionNoEditing">
                        9c9d08a438e55e52f33b608415e6dddd9b18550d
                      </span>
                      <span class="revisionEdit">
                        <span class="revisionWithEditing">
                          9c9d08a438e55e52f33b608415e6dddd9b18550d
                        </span>
                        <gr-button
                          aria-disabled="false"
                          class="editBtn"
                          data-index="10"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          edit
                        </gr-button>
                        <iron-input class="editItem">
                          <input />
                        </iron-input>
                        <gr-button
                          aria-disabled="false"
                          class="cancelBtn editItem"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          Cancel
                        </gr-button>
                        <gr-button
                          aria-disabled="true"
                          class="editItem saveBtn"
                          data-index="10"
                          disabled=""
                          link=""
                          role="button"
                          tabindex="-1"
                        >
                          Save
                        </gr-button>
                      </span>
                    </td>
                    <td class="hideItem message"></td>
                    <td class="hideItem tagger"></td>
                    <td class="repositoryBrowser">
                      <a
                        class="webLink"
                        href="https://git.example.org/branch/test;refs/heads/test9"
                        rel="noopener"
                        target="_blank"
                      >
                        (diffusion)
                      </a>
                    </td>
                    <td class="delete">
                      <gr-button
                        aria-disabled="false"
                        class="deleteButton"
                        data-index="10"
                        link=""
                        role="button"
                        tabindex="0"
                      >
                        Delete
                      </gr-button>
                    </td>
                  </tr>
                  <tr class="table">
                    <td class="branches name">
                      <a
                        href="https://git.example.org/branch/test;refs/heads/test10"
                      >
                        test10
                      </a>
                    </td>
                    <td class="branches revision">
                      <span class="revisionNoEditing">
                        9c9d08a438e55e52f33b608415e6dddd9b18550d
                      </span>
                      <span class="revisionEdit">
                        <span class="revisionWithEditing">
                          9c9d08a438e55e52f33b608415e6dddd9b18550d
                        </span>
                        <gr-button
                          aria-disabled="false"
                          class="editBtn"
                          data-index="11"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          edit
                        </gr-button>
                        <iron-input class="editItem">
                          <input />
                        </iron-input>
                        <gr-button
                          aria-disabled="false"
                          class="cancelBtn editItem"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          Cancel
                        </gr-button>
                        <gr-button
                          aria-disabled="true"
                          class="editItem saveBtn"
                          data-index="11"
                          disabled=""
                          link=""
                          role="button"
                          tabindex="-1"
                        >
                          Save
                        </gr-button>
                      </span>
                    </td>
                    <td class="hideItem message"></td>
                    <td class="hideItem tagger"></td>
                    <td class="repositoryBrowser">
                      <a
                        class="webLink"
                        href="https://git.example.org/branch/test;refs/heads/test10"
                        rel="noopener"
                        target="_blank"
                      >
                        (diffusion)
                      </a>
                    </td>
                    <td class="delete">
                      <gr-button
                        aria-disabled="false"
                        class="deleteButton"
                        data-index="11"
                        link=""
                        role="button"
                        tabindex="0"
                      >
                        Delete
                      </gr-button>
                    </td>
                  </tr>
                  <tr class="table">
                    <td class="branches name">
                      <a
                        href="https://git.example.org/branch/test;refs/heads/test11"
                      >
                        test11
                      </a>
                    </td>
                    <td class="branches revision">
                      <span class="revisionNoEditing">
                        9c9d08a438e55e52f33b608415e6dddd9b18550d
                      </span>
                      <span class="revisionEdit">
                        <span class="revisionWithEditing">
                          9c9d08a438e55e52f33b608415e6dddd9b18550d
                        </span>
                        <gr-button
                          aria-disabled="false"
                          class="editBtn"
                          data-index="12"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          edit
                        </gr-button>
                        <iron-input class="editItem">
                          <input />
                        </iron-input>
                        <gr-button
                          aria-disabled="false"
                          class="cancelBtn editItem"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          Cancel
                        </gr-button>
                        <gr-button
                          aria-disabled="true"
                          class="editItem saveBtn"
                          data-index="12"
                          disabled=""
                          link=""
                          role="button"
                          tabindex="-1"
                        >
                          Save
                        </gr-button>
                      </span>
                    </td>
                    <td class="hideItem message"></td>
                    <td class="hideItem tagger"></td>
                    <td class="repositoryBrowser">
                      <a
                        class="webLink"
                        href="https://git.example.org/branch/test;refs/heads/test11"
                        rel="noopener"
                        target="_blank"
                      >
                        (diffusion)
                      </a>
                    </td>
                    <td class="delete">
                      <gr-button
                        aria-disabled="false"
                        class="deleteButton"
                        data-index="12"
                        link=""
                        role="button"
                        tabindex="0"
                      >
                        Delete
                      </gr-button>
                    </td>
                  </tr>
                  <tr class="table">
                    <td class="branches name">
                      <a
                        href="https://git.example.org/branch/test;refs/heads/test12"
                      >
                        test12
                      </a>
                    </td>
                    <td class="branches revision">
                      <span class="revisionNoEditing">
                        9c9d08a438e55e52f33b608415e6dddd9b18550d
                      </span>
                      <span class="revisionEdit">
                        <span class="revisionWithEditing">
                          9c9d08a438e55e52f33b608415e6dddd9b18550d
                        </span>
                        <gr-button
                          aria-disabled="false"
                          class="editBtn"
                          data-index="13"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          edit
                        </gr-button>
                        <iron-input class="editItem">
                          <input />
                        </iron-input>
                        <gr-button
                          aria-disabled="false"
                          class="cancelBtn editItem"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          Cancel
                        </gr-button>
                        <gr-button
                          aria-disabled="true"
                          class="editItem saveBtn"
                          data-index="13"
                          disabled=""
                          link=""
                          role="button"
                          tabindex="-1"
                        >
                          Save
                        </gr-button>
                      </span>
                    </td>
                    <td class="hideItem message"></td>
                    <td class="hideItem tagger"></td>
                    <td class="repositoryBrowser">
                      <a
                        class="webLink"
                        href="https://git.example.org/branch/test;refs/heads/test12"
                        rel="noopener"
                        target="_blank"
                      >
                        (diffusion)
                      </a>
                    </td>
                    <td class="delete">
                      <gr-button
                        aria-disabled="false"
                        class="deleteButton"
                        data-index="13"
                        link=""
                        role="button"
                        tabindex="0"
                      >
                        Delete
                      </gr-button>
                    </td>
                  </tr>
                  <tr class="table">
                    <td class="branches name">
                      <a
                        href="https://git.example.org/branch/test;refs/heads/test13"
                      >
                        test13
                      </a>
                    </td>
                    <td class="branches revision">
                      <span class="revisionNoEditing">
                        9c9d08a438e55e52f33b608415e6dddd9b18550d
                      </span>
                      <span class="revisionEdit">
                        <span class="revisionWithEditing">
                          9c9d08a438e55e52f33b608415e6dddd9b18550d
                        </span>
                        <gr-button
                          aria-disabled="false"
                          class="editBtn"
                          data-index="14"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          edit
                        </gr-button>
                        <iron-input class="editItem">
                          <input />
                        </iron-input>
                        <gr-button
                          aria-disabled="false"
                          class="cancelBtn editItem"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          Cancel
                        </gr-button>
                        <gr-button
                          aria-disabled="true"
                          class="editItem saveBtn"
                          data-index="14"
                          disabled=""
                          link=""
                          role="button"
                          tabindex="-1"
                        >
                          Save
                        </gr-button>
                      </span>
                    </td>
                    <td class="hideItem message"></td>
                    <td class="hideItem tagger"></td>
                    <td class="repositoryBrowser">
                      <a
                        class="webLink"
                        href="https://git.example.org/branch/test;refs/heads/test13"
                        rel="noopener"
                        target="_blank"
                      >
                        (diffusion)
                      </a>
                    </td>
                    <td class="delete">
                      <gr-button
                        aria-disabled="false"
                        class="deleteButton"
                        data-index="14"
                        link=""
                        role="button"
                        tabindex="0"
                      >
                        Delete
                      </gr-button>
                    </td>
                  </tr>
                  <tr class="table">
                    <td class="branches name">
                      <a
                        href="https://git.example.org/branch/test;refs/heads/test14"
                      >
                        test14
                      </a>
                    </td>
                    <td class="branches revision">
                      <span class="revisionNoEditing">
                        9c9d08a438e55e52f33b608415e6dddd9b18550d
                      </span>
                      <span class="revisionEdit">
                        <span class="revisionWithEditing">
                          9c9d08a438e55e52f33b608415e6dddd9b18550d
                        </span>
                        <gr-button
                          aria-disabled="false"
                          class="editBtn"
                          data-index="15"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          edit
                        </gr-button>
                        <iron-input class="editItem">
                          <input />
                        </iron-input>
                        <gr-button
                          aria-disabled="false"
                          class="cancelBtn editItem"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          Cancel
                        </gr-button>
                        <gr-button
                          aria-disabled="true"
                          class="editItem saveBtn"
                          data-index="15"
                          disabled=""
                          link=""
                          role="button"
                          tabindex="-1"
                        >
                          Save
                        </gr-button>
                      </span>
                    </td>
                    <td class="hideItem message"></td>
                    <td class="hideItem tagger"></td>
                    <td class="repositoryBrowser">
                      <a
                        class="webLink"
                        href="https://git.example.org/branch/test;refs/heads/test14"
                        rel="noopener"
                        target="_blank"
                      >
                        (diffusion)
                      </a>
                    </td>
                    <td class="delete">
                      <gr-button
                        aria-disabled="false"
                        class="deleteButton"
                        data-index="15"
                        link=""
                        role="button"
                        tabindex="0"
                      >
                        Delete
                      </gr-button>
                    </td>
                  </tr>
                  <tr class="table">
                    <td class="branches name">
                      <a
                        href="https://git.example.org/branch/test;refs/heads/test15"
                      >
                        test15
                      </a>
                    </td>
                    <td class="branches revision">
                      <span class="revisionNoEditing">
                        9c9d08a438e55e52f33b608415e6dddd9b18550d
                      </span>
                      <span class="revisionEdit">
                        <span class="revisionWithEditing">
                          9c9d08a438e55e52f33b608415e6dddd9b18550d
                        </span>
                        <gr-button
                          aria-disabled="false"
                          class="editBtn"
                          data-index="16"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          edit
                        </gr-button>
                        <iron-input class="editItem">
                          <input />
                        </iron-input>
                        <gr-button
                          aria-disabled="false"
                          class="cancelBtn editItem"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          Cancel
                        </gr-button>
                        <gr-button
                          aria-disabled="true"
                          class="editItem saveBtn"
                          data-index="16"
                          disabled=""
                          link=""
                          role="button"
                          tabindex="-1"
                        >
                          Save
                        </gr-button>
                      </span>
                    </td>
                    <td class="hideItem message"></td>
                    <td class="hideItem tagger"></td>
                    <td class="repositoryBrowser">
                      <a
                        class="webLink"
                        href="https://git.example.org/branch/test;refs/heads/test15"
                        rel="noopener"
                        target="_blank"
                      >
                        (diffusion)
                      </a>
                    </td>
                    <td class="delete">
                      <gr-button
                        aria-disabled="false"
                        class="deleteButton"
                        data-index="16"
                        link=""
                        role="button"
                        tabindex="0"
                      >
                        Delete
                      </gr-button>
                    </td>
                  </tr>
                  <tr class="table">
                    <td class="branches name">
                      <a
                        href="https://git.example.org/branch/test;refs/heads/test16"
                      >
                        test16
                      </a>
                    </td>
                    <td class="branches revision">
                      <span class="revisionNoEditing">
                        9c9d08a438e55e52f33b608415e6dddd9b18550d
                      </span>
                      <span class="revisionEdit">
                        <span class="revisionWithEditing">
                          9c9d08a438e55e52f33b608415e6dddd9b18550d
                        </span>
                        <gr-button
                          aria-disabled="false"
                          class="editBtn"
                          data-index="17"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          edit
                        </gr-button>
                        <iron-input class="editItem">
                          <input />
                        </iron-input>
                        <gr-button
                          aria-disabled="false"
                          class="cancelBtn editItem"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          Cancel
                        </gr-button>
                        <gr-button
                          aria-disabled="true"
                          class="editItem saveBtn"
                          data-index="17"
                          disabled=""
                          link=""
                          role="button"
                          tabindex="-1"
                        >
                          Save
                        </gr-button>
                      </span>
                    </td>
                    <td class="hideItem message"></td>
                    <td class="hideItem tagger"></td>
                    <td class="repositoryBrowser">
                      <a
                        class="webLink"
                        href="https://git.example.org/branch/test;refs/heads/test16"
                        rel="noopener"
                        target="_blank"
                      >
                        (diffusion)
                      </a>
                    </td>
                    <td class="delete">
                      <gr-button
                        aria-disabled="false"
                        class="deleteButton"
                        data-index="17"
                        link=""
                        role="button"
                        tabindex="0"
                      >
                        Delete
                      </gr-button>
                    </td>
                  </tr>
                  <tr class="table">
                    <td class="branches name">
                      <a
                        href="https://git.example.org/branch/test;refs/heads/test17"
                      >
                        test17
                      </a>
                    </td>
                    <td class="branches revision">
                      <span class="revisionNoEditing">
                        9c9d08a438e55e52f33b608415e6dddd9b18550d
                      </span>
                      <span class="revisionEdit">
                        <span class="revisionWithEditing">
                          9c9d08a438e55e52f33b608415e6dddd9b18550d
                        </span>
                        <gr-button
                          aria-disabled="false"
                          class="editBtn"
                          data-index="18"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          edit
                        </gr-button>
                        <iron-input class="editItem">
                          <input />
                        </iron-input>
                        <gr-button
                          aria-disabled="false"
                          class="cancelBtn editItem"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          Cancel
                        </gr-button>
                        <gr-button
                          aria-disabled="true"
                          class="editItem saveBtn"
                          data-index="18"
                          disabled=""
                          link=""
                          role="button"
                          tabindex="-1"
                        >
                          Save
                        </gr-button>
                      </span>
                    </td>
                    <td class="hideItem message"></td>
                    <td class="hideItem tagger"></td>
                    <td class="repositoryBrowser">
                      <a
                        class="webLink"
                        href="https://git.example.org/branch/test;refs/heads/test17"
                        rel="noopener"
                        target="_blank"
                      >
                        (diffusion)
                      </a>
                    </td>
                    <td class="delete">
                      <gr-button
                        aria-disabled="false"
                        class="deleteButton"
                        data-index="18"
                        link=""
                        role="button"
                        tabindex="0"
                      >
                        Delete
                      </gr-button>
                    </td>
                  </tr>
                  <tr class="table">
                    <td class="branches name">
                      <a
                        href="https://git.example.org/branch/test;refs/heads/test18"
                      >
                        test18
                      </a>
                    </td>
                    <td class="branches revision">
                      <span class="revisionNoEditing">
                        9c9d08a438e55e52f33b608415e6dddd9b18550d
                      </span>
                      <span class="revisionEdit">
                        <span class="revisionWithEditing">
                          9c9d08a438e55e52f33b608415e6dddd9b18550d
                        </span>
                        <gr-button
                          aria-disabled="false"
                          class="editBtn"
                          data-index="19"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          edit
                        </gr-button>
                        <iron-input class="editItem">
                          <input />
                        </iron-input>
                        <gr-button
                          aria-disabled="false"
                          class="cancelBtn editItem"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          Cancel
                        </gr-button>
                        <gr-button
                          aria-disabled="true"
                          class="editItem saveBtn"
                          data-index="19"
                          disabled=""
                          link=""
                          role="button"
                          tabindex="-1"
                        >
                          Save
                        </gr-button>
                      </span>
                    </td>
                    <td class="hideItem message"></td>
                    <td class="hideItem tagger"></td>
                    <td class="repositoryBrowser">
                      <a
                        class="webLink"
                        href="https://git.example.org/branch/test;refs/heads/test18"
                        rel="noopener"
                        target="_blank"
                      >
                        (diffusion)
                      </a>
                    </td>
                    <td class="delete">
                      <gr-button
                        aria-disabled="false"
                        class="deleteButton"
                        data-index="19"
                        link=""
                        role="button"
                        tabindex="0"
                      >
                        Delete
                      </gr-button>
                    </td>
                  </tr>
                  <tr class="table">
                    <td class="branches name">
                      <a
                        href="https://git.example.org/branch/test;refs/heads/test19"
                      >
                        test19
                      </a>
                    </td>
                    <td class="branches revision">
                      <span class="revisionNoEditing">
                        9c9d08a438e55e52f33b608415e6dddd9b18550d
                      </span>
                      <span class="revisionEdit">
                        <span class="revisionWithEditing">
                          9c9d08a438e55e52f33b608415e6dddd9b18550d
                        </span>
                        <gr-button
                          aria-disabled="false"
                          class="editBtn"
                          data-index="20"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          edit
                        </gr-button>
                        <iron-input class="editItem">
                          <input />
                        </iron-input>
                        <gr-button
                          aria-disabled="false"
                          class="cancelBtn editItem"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          Cancel
                        </gr-button>
                        <gr-button
                          aria-disabled="true"
                          class="editItem saveBtn"
                          data-index="20"
                          disabled=""
                          link=""
                          role="button"
                          tabindex="-1"
                        >
                          Save
                        </gr-button>
                      </span>
                    </td>
                    <td class="hideItem message"></td>
                    <td class="hideItem tagger"></td>
                    <td class="repositoryBrowser">
                      <a
                        class="webLink"
                        href="https://git.example.org/branch/test;refs/heads/test19"
                        rel="noopener"
                        target="_blank"
                      >
                        (diffusion)
                      </a>
                    </td>
                    <td class="delete">
                      <gr-button
                        aria-disabled="false"
                        class="deleteButton"
                        data-index="20"
                        link=""
                        role="button"
                        tabindex="0"
                      >
                        Delete
                      </gr-button>
                    </td>
                  </tr>
                  <tr class="table">
                    <td class="branches name">
                      <a
                        href="https://git.example.org/branch/test;refs/heads/test20"
                      >
                        test20
                      </a>
                    </td>
                    <td class="branches revision">
                      <span class="revisionNoEditing">
                        9c9d08a438e55e52f33b608415e6dddd9b18550d
                      </span>
                      <span class="revisionEdit">
                        <span class="revisionWithEditing">
                          9c9d08a438e55e52f33b608415e6dddd9b18550d
                        </span>
                        <gr-button
                          aria-disabled="false"
                          class="editBtn"
                          data-index="21"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          edit
                        </gr-button>
                        <iron-input class="editItem">
                          <input />
                        </iron-input>
                        <gr-button
                          aria-disabled="false"
                          class="cancelBtn editItem"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          Cancel
                        </gr-button>
                        <gr-button
                          aria-disabled="true"
                          class="editItem saveBtn"
                          data-index="21"
                          disabled=""
                          link=""
                          role="button"
                          tabindex="-1"
                        >
                          Save
                        </gr-button>
                      </span>
                    </td>
                    <td class="hideItem message"></td>
                    <td class="hideItem tagger"></td>
                    <td class="repositoryBrowser">
                      <a
                        class="webLink"
                        href="https://git.example.org/branch/test;refs/heads/test20"
                        rel="noopener"
                        target="_blank"
                      >
                        (diffusion)
                      </a>
                    </td>
                    <td class="delete">
                      <gr-button
                        aria-disabled="false"
                        class="deleteButton"
                        data-index="21"
                        link=""
                        role="button"
                        tabindex="0"
                      >
                        Delete
                      </gr-button>
                    </td>
                  </tr>
                  <tr class="table">
                    <td class="branches name">
                      <a
                        href="https://git.example.org/branch/test;refs/heads/test21"
                      >
                        test21
                      </a>
                    </td>
                    <td class="branches revision">
                      <span class="revisionNoEditing">
                        9c9d08a438e55e52f33b608415e6dddd9b18550d
                      </span>
                      <span class="revisionEdit">
                        <span class="revisionWithEditing">
                          9c9d08a438e55e52f33b608415e6dddd9b18550d
                        </span>
                        <gr-button
                          aria-disabled="false"
                          class="editBtn"
                          data-index="22"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          edit
                        </gr-button>
                        <iron-input class="editItem">
                          <input />
                        </iron-input>
                        <gr-button
                          aria-disabled="false"
                          class="cancelBtn editItem"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          Cancel
                        </gr-button>
                        <gr-button
                          aria-disabled="true"
                          class="editItem saveBtn"
                          data-index="22"
                          disabled=""
                          link=""
                          role="button"
                          tabindex="-1"
                        >
                          Save
                        </gr-button>
                      </span>
                    </td>
                    <td class="hideItem message"></td>
                    <td class="hideItem tagger"></td>
                    <td class="repositoryBrowser">
                      <a
                        class="webLink"
                        href="https://git.example.org/branch/test;refs/heads/test21"
                        rel="noopener"
                        target="_blank"
                      >
                        (diffusion)
                      </a>
                    </td>
                    <td class="delete">
                      <gr-button
                        aria-disabled="false"
                        class="deleteButton"
                        data-index="22"
                        link=""
                        role="button"
                        tabindex="0"
                      >
                        Delete
                      </gr-button>
                    </td>
                  </tr>
                  <tr class="table">
                    <td class="branches name">
                      <a
                        href="https://git.example.org/branch/test;refs/heads/test22"
                      >
                        test22
                      </a>
                    </td>
                    <td class="branches revision">
                      <span class="revisionNoEditing">
                        9c9d08a438e55e52f33b608415e6dddd9b18550d
                      </span>
                      <span class="revisionEdit">
                        <span class="revisionWithEditing">
                          9c9d08a438e55e52f33b608415e6dddd9b18550d
                        </span>
                        <gr-button
                          aria-disabled="false"
                          class="editBtn"
                          data-index="23"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          edit
                        </gr-button>
                        <iron-input class="editItem">
                          <input />
                        </iron-input>
                        <gr-button
                          aria-disabled="false"
                          class="cancelBtn editItem"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          Cancel
                        </gr-button>
                        <gr-button
                          aria-disabled="true"
                          class="editItem saveBtn"
                          data-index="23"
                          disabled=""
                          link=""
                          role="button"
                          tabindex="-1"
                        >
                          Save
                        </gr-button>
                      </span>
                    </td>
                    <td class="hideItem message"></td>
                    <td class="hideItem tagger"></td>
                    <td class="repositoryBrowser">
                      <a
                        class="webLink"
                        href="https://git.example.org/branch/test;refs/heads/test22"
                        rel="noopener"
                        target="_blank"
                      >
                        (diffusion)
                      </a>
                    </td>
                    <td class="delete">
                      <gr-button
                        aria-disabled="false"
                        class="deleteButton"
                        data-index="23"
                        link=""
                        role="button"
                        tabindex="0"
                      >
                        Delete
                      </gr-button>
                    </td>
                  </tr>
                  <tr class="table">
                    <td class="branches name">
                      <a
                        href="https://git.example.org/branch/test;refs/heads/test23"
                      >
                        test23
                      </a>
                    </td>
                    <td class="branches revision">
                      <span class="revisionNoEditing">
                        9c9d08a438e55e52f33b608415e6dddd9b18550d
                      </span>
                      <span class="revisionEdit">
                        <span class="revisionWithEditing">
                          9c9d08a438e55e52f33b608415e6dddd9b18550d
                        </span>
                        <gr-button
                          aria-disabled="false"
                          class="editBtn"
                          data-index="24"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          edit
                        </gr-button>
                        <iron-input class="editItem">
                          <input />
                        </iron-input>
                        <gr-button
                          aria-disabled="false"
                          class="cancelBtn editItem"
                          link=""
                          role="button"
                          tabindex="0"
                        >
                          Cancel
                        </gr-button>
                        <gr-button
                          aria-disabled="true"
                          class="editItem saveBtn"
                          data-index="24"
                          disabled=""
                          link=""
                          role="button"
                          tabindex="-1"
                        >
                          Save
                        </gr-button>
                      </span>
                    </td>
                    <td class="hideItem message"></td>
                    <td class="hideItem tagger"></td>
                    <td class="repositoryBrowser">
                      <a
                        class="webLink"
                        href="https://git.example.org/branch/test;refs/heads/test23"
                        rel="noopener"
                        target="_blank"
                      >
                        (diffusion)
                      </a>
                    </td>
                    <td class="delete">
                      <gr-button
                        aria-disabled="false"
                        class="deleteButton"
                        data-index="24"
                        link=""
                        role="button"
                        tabindex="0"
                      >
                        Delete
                      </gr-button>
                    </td>
                  </tr>
                </tbody>
              </table>
              <dialog id="modal" tabindex="-1">
                <gr-confirm-delete-item-dialog class="confirmDialog">
                </gr-confirm-delete-item-dialog>
              </dialog>
            </gr-list-view>
            <dialog id="createModal" tabindex="-1">
              <gr-dialog
                confirm-label="Create"
                disabled=""
                id="createDialog"
                role="dialog"
              >
                <div class="header" slot="header">Create Branch</div>
                <div class="main" slot="main">
                  <gr-create-pointer-dialog id="createNewModal">
                  </gr-create-pointer-dialog>
                </div>
              </gr-dialog>
            </dialog>
          `
        );
      });

      test('test for branch in the list', () => {
        assert.equal(element.items![3].ref, 'refs/heads/test2');
      });

      test('test for web links in the branches list', () => {
        assert.equal(
          element.items![3].web_links![0].url,
          'https://git.example.org/branch/test;refs/heads/test2'
        );
      });

      test('test for refs/heads/ being striped from ref', () => {
        assert.equal(
          element.stripRefs(element.items![3].ref, element.detailType),
          'test2'
        );
      });

      test('items', () => {
        assert.equal(queryAll<HTMLTableElement>(element, '.table').length, 25);
      });

      test('Edit HEAD button not admin', async () => {
        sinon.stub(element, 'getLoggedIn').returns(Promise.resolve(true));
        stubRestApi('getRepoAccess').returns(
          Promise.resolve({
            test: {
              revision: 'xxxx',
              local: {
                'refs/*': {
                  permissions: {
                    owner: {rules: {xxx: {action: 'ALLOW', force: false}}},
                  },
                },
              },
              owner_of: ['refs/*'] as GitRef[],
              groups: {
                xxxx: {
                  id: 'xxxx' as GroupId,
                  url: 'test',
                  name: 'test' as GroupName,
                },
              } as RepoAccessGroups,
              config_web_links: [{name: 'gitiles', url: 'test'}],
            },
          } as RepoAccessInfoMap)
        );
        await element.determineIfOwner('test' as RepoName);
        assert.equal(element.isOwner, false);
        assert.equal(
          getComputedStyle(
            queryAndAssert<HTMLSpanElement>(element, '.revisionNoEditing')
          ).display,
          'inline'
        );
        assert.equal(
          getComputedStyle(
            queryAndAssert<HTMLSpanElement>(element, '.revisionEdit')
          ).display,
          'none'
        );
      });

      test('Edit HEAD button admin', async () => {
        const saveBtn = queryAndAssert<GrButton>(element, '.saveBtn');
        const cancelBtn = queryAndAssert<GrButton>(element, '.cancelBtn');
        const editBtn = queryAndAssert<GrButton>(element, '.editBtn');
        const revisionNoEditing = queryAndAssert<HTMLSpanElement>(
          element,
          '.revisionNoEditing'
        );
        const revisionWithEditing = queryAndAssert<HTMLSpanElement>(
          element,
          '.revisionWithEditing'
        );

        sinon.stub(element, 'getLoggedIn').returns(Promise.resolve(true));
        stubRestApi('getRepoAccess').returns(
          Promise.resolve({
            test: {
              revision: 'xxxx',
              local: {
                'refs/*': {
                  permissions: {
                    owner: {rules: {xxx: {action: 'ALLOW', force: false}}},
                  },
                },
              },
              is_owner: true,
              owner_of: ['refs/*'] as GitRef[],
              groups: {
                xxxx: {
                  id: 'xxxx' as GroupId,
                  url: 'test',
                  name: 'test' as GroupName,
                },
              } as RepoAccessGroups,
              config_web_links: [{name: 'gitiles', url: 'test'}],
            },
          } as RepoAccessInfoMap)
        );
        const handleSaveRevisionStub = sinon.stub(
          element,
          'handleSaveRevision'
        );
        await element.determineIfOwner('test' as RepoName);
        assert.equal(element.isOwner, true);
        // The revision container for non-editing enabled row is not visible.
        assert.equal(getComputedStyle(revisionNoEditing).display, 'none');

        // The revision container for editing enabled row is visible.
        assert.notEqual(
          getComputedStyle(
            queryAndAssert<HTMLSpanElement>(element, '.revisionEdit')
          ).display,
          'none'
        );

        // The revision and edit button are visible.
        assert.notEqual(getComputedStyle(revisionWithEditing).display, 'none');
        assert.notEqual(getComputedStyle(editBtn).display, 'none');

        // The input, cancel, and save buttons are not visible.
        const hiddenElements = queryAll<HTMLTableElement>(
          element,
          '.canEdit .editItem'
        );

        for (const item of hiddenElements) {
          assert.equal(getComputedStyle(item).display, 'none');
        }

        editBtn.click();
        await element.updateComplete;
        // The revision and edit button are not visible.
        assert.equal(getComputedStyle(revisionWithEditing).display, 'none');
        assert.equal(getComputedStyle(editBtn).display, 'none');

        // The input, cancel, and save buttons are not visible.
        for (const item of hiddenElements) {
          assert.notEqual(getComputedStyle(item).display, 'none');
        }

        // The revised ref was set correctly
        assert.equal(element.revisedRef, 'master' as GitRef);

        assert.isFalse(saveBtn.disabled);

        // Delete the ref.
        element.revisedRef = '' as GitRef;
        await element.updateComplete;
        assert.isTrue(saveBtn.disabled);

        // Change the ref to something else
        element.revisedRef = 'newRef' as GitRef;
        element.repo = 'test' as RepoName;
        await element.updateComplete;
        assert.isFalse(saveBtn.disabled);

        // Save button calls handleSave. since this is stubbed, the edit
        // section remains open.
        saveBtn.click();
        assert.isTrue(handleSaveRevisionStub.called);

        // When cancel is tapped, the edit section closes.
        cancelBtn.click();
        await element.updateComplete;

        // The revision and edit button are visible.
        assert.notEqual(getComputedStyle(revisionWithEditing).display, 'none');
        assert.notEqual(getComputedStyle(editBtn).display, 'none');

        // The input, cancel, and save buttons are not visible.
        for (const item of hiddenElements) {
          assert.equal(getComputedStyle(item).display, 'none');
        }
      });

      test('handleSaveRevision with invalid rev', async () => {
        element.isEditing = true;
        stubRestApi('setRepoHead').returns(
          Promise.resolve({
            status: 400,
          } as Response)
        );

        await element.setRepoHead('test' as RepoName, 'newRef' as GitRef, 1);
        assert.isTrue(element.isEditing);
      });

      test('handleSaveRevision with valid rev', async () => {
        element.isEditing = true;
        stubRestApi('setRepoHead').returns(
          Promise.resolve({
            status: 200,
          } as Response)
        );

        await element.setRepoHead('test' as RepoName, 'newRef' as GitRef, 1);
        assert.isFalse(element.isEditing);
      });

      test('test computeItemName', () => {
        assert.deepEqual(
          element.computeItemName(RepoDetailView.BRANCHES),
          'Branch'
        );
        assert.deepEqual(element.computeItemName(RepoDetailView.TAGS), 'Tag');
      });
    });

    suite('list with less then 25 branches', () => {
      setup(async () => {
        branches = createBranchesList(25);
        stubRestApi('getRepoBranches').returns(Promise.resolve(branches));

        element.params = {
          view: GerritView.REPO,
          repo: 'test' as RepoName,
          detail: RepoDetailView.BRANCHES,
        };

        await element.paramsChanged();
        await element.updateComplete;
      });

      test('items', () => {
        assert.equal(queryAll<HTMLTableElement>(element, '.table').length, 25);
      });
    });

    suite('filter', () => {
      test('paramsChanged', async () => {
        const stub = stubRestApi('getRepoBranches').returns(
          Promise.resolve(branches)
        );
        element.params = {
          view: GerritView.REPO,
          repo: 'test' as RepoName,
          detail: RepoDetailView.BRANCHES,
          filter: 'test',
          offset: 25,
        };
        await element.paramsChanged();
        assert.equal(stub.lastCall.args[0], 'test');
        assert.equal(stub.lastCall.args[1], 'test');
        assert.equal(stub.lastCall.args[2], 25);
        assert.equal(stub.lastCall.args[3], 25);
      });
    });

    suite('404', () => {
      test('fires page-error', async () => {
        const response = {status: 404} as Response;
        stubRestApi('getRepoBranches').callsFake(
          (_filter, _repo, _reposBranchesPerPage, _opt_offset, errFn) => {
            if (errFn !== undefined) {
              errFn(response);
            }
            return Promise.resolve([]);
          }
        );

        const promise = mockPromise();
        addListenerForTest(document, 'page-error', e => {
          assert.deepEqual((e as PageErrorEvent).detail.response, response);
          promise.resolve();
        });

        element.params = {
          view: GerritView.REPO,
          repo: 'test' as RepoName,
          detail: RepoDetailView.BRANCHES,
          filter: 'test',
          offset: 25,
        };
        element.paramsChanged();
        await promise;
      });
    });
  });

  suite('Tags', () => {
    let element: GrRepoDetailList;
    let tags: TagInfo[];

    setup(async () => {
      element = await fixture(
        html`<gr-repo-detail-list></gr-repo-detail-list>`
      );
      element.detailType = RepoDetailView.TAGS;
      sinon.stub(page, 'show');
    });

    suite('list of repo tags', () => {
      setup(async () => {
        tags = createTagsList(26);
        stubRestApi('getRepoTags').returns(Promise.resolve(tags));

        element.params = {
          view: GerritView.REPO,
          repo: 'test' as RepoName,
          detail: RepoDetailView.TAGS,
        };

        await element.paramsChanged();
        await element.updateComplete;
      });

      test('test for tag in the list', async () => {
        assert.equal(element.items![2].ref, 'refs/tags/test2');
      });

      test('test for tag message in the list', async () => {
        assert.equal((element.items as TagInfo[])![2].message, 'Annotated tag');
      });

      test('test for tagger in the tag list', async () => {
        const tagger = {
          name: 'Test User',
          email: 'test.user@gmail.com' as EmailAddress,
          date: '2017-09-19 14:54:00.000000000' as Timestamp,
          tz: 540 as TimezoneOffset,
        };

        assert.deepEqual((element.items as TagInfo[])![2].tagger, tagger);
      });

      test('test for web links in the tags list', async () => {
        assert.equal(
          element.items![2].web_links![0].url,
          'https://git.example.org/tag/test;refs/tags/test2'
        );
      });

      test('test for refs/tags/ being striped from ref', async () => {
        assert.equal(
          element.stripRefs(element.items![2].ref, element.detailType),
          'test2'
        );
      });

      test('items', () => {
        assert.equal(element.items!.slice(0, SHOWN_ITEMS_COUNT)!.length, 25);
      });
    });

    suite('list with less then 25 tags', () => {
      setup(async () => {
        tags = createTagsList(25);
        stubRestApi('getRepoTags').returns(Promise.resolve(tags));

        element.params = {
          view: GerritView.REPO,
          repo: 'test' as RepoName,
          detail: RepoDetailView.TAGS,
        };

        await element.paramsChanged();
        await element.updateComplete;
      });

      test('items', () => {
        assert.equal(element.items!.slice(0, SHOWN_ITEMS_COUNT)!.length, 25);
      });
    });

    suite('filter', () => {
      test('paramsChanged', async () => {
        const stub = stubRestApi('getRepoTags').returns(Promise.resolve(tags));
        element.params = {
          view: GerritView.REPO,
          repo: 'test' as RepoName,
          detail: RepoDetailView.TAGS,
          filter: 'test',
          offset: 25,
        };
        await element.paramsChanged();
        assert.equal(stub.lastCall.args[0], 'test');
        assert.equal(stub.lastCall.args[1], 'test');
        assert.equal(stub.lastCall.args[2], 25);
        assert.equal(stub.lastCall.args[3], 25);
      });
    });

    suite('create new', () => {
      test('handleCreateClicked called when create-click fired', () => {
        const handleCreateClickedStub = sinon.stub(
          element,
          'handleCreateClicked'
        );
        queryAndAssert<GrListView>(element, 'gr-list-view').dispatchEvent(
          new CustomEvent('create-clicked', {
            composed: true,
            bubbles: true,
          })
        );
        assert.isTrue(handleCreateClickedStub.called);
      });

      test('handleCreateClicked opens modal', () => {
        queryAndAssert<HTMLDialogElement>(element, '#createModal');
        const openStub = sinon.stub(
          queryAndAssert<HTMLDialogElement>(element, '#createModal'),
          'showModal'
        );
        element.handleCreateClicked();
        assert.isTrue(openStub.called);
      });

      test('handleCreateItem called when confirm fired', () => {
        const handleCreateItemStub = sinon.stub(element, 'handleCreateItem');
        queryAndAssert<GrDialog>(element, '#createDialog').dispatchEvent(
          new CustomEvent('confirm', {
            composed: true,
            bubbles: true,
          })
        );
        assert.isTrue(handleCreateItemStub.called);
      });

      test('handleCloseCreate called when cancel fired', () => {
        const handleCloseCreateStub = sinon.stub(element, 'handleCloseCreate');
        queryAndAssert<GrDialog>(element, '#createDialog').dispatchEvent(
          new CustomEvent('cancel', {
            composed: true,
            bubbles: true,
          })
        );
        assert.isTrue(handleCloseCreateStub.called);
      });
    });

    suite('404', () => {
      test('fires page-error', async () => {
        const response = {status: 404} as Response;
        stubRestApi('getRepoTags').callsFake(
          (_filter, _repo, _reposTagsPerPage, _opt_offset, errFn) => {
            if (errFn !== undefined) {
              errFn(response);
            }
            return Promise.resolve([]);
          }
        );

        const promise = mockPromise();
        addListenerForTest(document, 'page-error', e => {
          assert.deepEqual((e as PageErrorEvent).detail.response, response);
          promise.resolve();
        });

        element.params = {
          view: GerritView.REPO,
          repo: 'test' as RepoName,
          detail: RepoDetailView.TAGS,
          filter: 'test',
          offset: 25,
        };
        element.paramsChanged();
        await promise;
      });
    });

    test('computeItemName', () => {
      assert.equal(element.computeItemName(RepoDetailView.BRANCHES), 'Branch');
      assert.equal(element.computeItemName(RepoDetailView.TAGS), 'Tag');
    });
  });
});
