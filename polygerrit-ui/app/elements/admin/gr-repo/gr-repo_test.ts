/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-repo';
import {GrRepo} from './gr-repo';
import {createChange} from '../../../test/test-data-generators';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {mockPromise} from '../../../test/test-utils';
import {
  addListenerForTest,
  queryAll,
  queryAndAssert,
  stubRestApi,
} from '../../../test/test-utils';
import {
  createInheritedBoolean,
  createServerInfo,
} from '../../../test/test-data-generators';
import {testResolver} from '../../../test/common-test-setup';
import {
  ConfigInfo,
  GitRef,
  GroupId,
  GroupName,
  InheritedBooleanInfo,
  MaxObjectSizeLimitInfo,
  PluginParameterToConfigParameterInfoMap,
  ProjectAccessInfo,
  RepoAccessGroups,
  RepoAccessInfoMap,
  RepoName,
} from '../../../types/common';
import {
  ConfigParameterInfoType,
  InheritedBooleanInfoConfiguredValue,
  PermissionAction,
  RepoState,
  SubmitType,
} from '../../../constants/constants';
import {
  createConfig,
  createDownloadSchemes,
} from '../../../test/test-data-generators';
import {PageErrorEvent} from '../../../types/events';
import {GrButton} from '../../shared/gr-button/gr-button';
import {assert, fixture, html} from '@open-wc/testing';
import {ChangeInfo} from '../../../api/rest-api';
import {MdOutlinedTextField} from '@material/web/textfield/outlined-text-field';
import {GrAutogrowTextarea} from '../../shared/gr-autogrow-textarea/gr-autogrow-textarea';
import {MdOutlinedSelect} from '@material/web/select/outlined-select';

suite('gr-repo tests', () => {
  let element: GrRepo;
  let loggedInStub: sinon.SinonStub;
  let repoStub: sinon.SinonStub;

  const repoConf: ConfigInfo = {
    description: 'Access inherited by all other repositories.',
    use_contributor_agreements: {
      value: false,
      configured_value: InheritedBooleanInfoConfiguredValue.FALSE,
    },
    use_content_merge: {
      value: false,
      configured_value: InheritedBooleanInfoConfiguredValue.FALSE,
    },
    use_signed_off_by: {
      value: false,
      configured_value: InheritedBooleanInfoConfiguredValue.FALSE,
    },
    create_new_change_for_all_not_in_target: {
      value: false,
      configured_value: InheritedBooleanInfoConfiguredValue.FALSE,
    },
    require_change_id: {
      value: false,
      configured_value: InheritedBooleanInfoConfiguredValue.FALSE,
    },
    enable_signed_push: {
      value: false,
      configured_value: InheritedBooleanInfoConfiguredValue.FALSE,
    },
    require_signed_push: {
      value: false,
      configured_value: InheritedBooleanInfoConfiguredValue.FALSE,
    },
    reject_implicit_merges: {
      value: false,
      configured_value: InheritedBooleanInfoConfiguredValue.FALSE,
    },
    match_author_to_committer_date: {
      value: false,
      configured_value: InheritedBooleanInfoConfiguredValue.FALSE,
    },
    reject_empty_commit: {
      value: false,
      configured_value: InheritedBooleanInfoConfiguredValue.FALSE,
    },
    enable_reviewer_by_email: {
      value: false,
      configured_value: InheritedBooleanInfoConfiguredValue.FALSE,
    },
    private_by_default: {
      value: false,
      configured_value: InheritedBooleanInfoConfiguredValue.FALSE,
    },
    work_in_progress_by_default: {
      value: false,
      configured_value: InheritedBooleanInfoConfiguredValue.FALSE,
    },
    max_object_size_limit: {},
    commentlinks: {},
    submit_type: SubmitType.MERGE_IF_NECESSARY,
    default_submit_type: {
      value: SubmitType.MERGE_IF_NECESSARY,
      configured_value: SubmitType.INHERIT,
      inherited_value: SubmitType.MERGE_IF_NECESSARY,
    },
  };

  const REPO = 'test-repo';
  const SCHEMES = {
    ...createDownloadSchemes(),
    http: {
      url: 'test',
      is_auth_required: false,
      is_auth_supported: false,
      commands: 'test',
      clone_commands: {clone: 'test'},
    },
    repo: {
      url: 'test',
      is_auth_required: false,
      is_auth_supported: false,
      commands: 'test',
      clone_commands: {clone: 'test'},
    },
    ssh: {
      url: 'test',
      is_auth_required: false,
      is_auth_supported: false,
      commands: 'test',
      clone_commands: {clone: 'test'},
    },
  };

  function getFormFields() {
    const selects = Array.from(queryAll(element, 'select'));
    const textareas = Array.from(queryAll(element, 'gr-autogrow-textarea'));
    const inputs = Array.from(queryAll(element, 'input'));
    return inputs.concat(textareas).concat(selects);
  }

  setup(async () => {
    loggedInStub = stubRestApi('getLoggedIn').returns(Promise.resolve(false));
    stubRestApi('getConfig').returns(Promise.resolve(createServerInfo()));
    repoStub = stubRestApi('getProjectConfig').returns(
      Promise.resolve(repoConf)
    );
    element = await fixture(html`<gr-repo></gr-repo>`);
  });

  test('render', async () => {
    element.repo = REPO as RepoName;
    await element.loadRepo();
    await element.updateComplete;
    // prettier and shadowDom assert do not agree about span.title wrapping
    assert.shadowDom.equal(
      element,
      /* prettier-ignore */ /* HTML */ `
       <div class="gr-form-styles main read-only">
         <div class="info">
           <h1
             class="heading-1"
             id="Title"
           >
             test-repo
           </h1>
           <hr>
           <div>
             <a href="">
               <gr-button
                 aria-disabled="true"
                 disabled=""
                 link=""
                 role="button"
                 tabindex="-1"
               >
                 Browse
               </gr-button>
             </a>
             <a href="/q/project:test-repo">
               <gr-button
                 aria-disabled="false"
                 link=""
                 role="button"
                 tabindex="0"
               >
                 View Changes
               </gr-button>
             </a>
           </div>
         </div>
         <div id="loadedContent">
           <h2
             class="heading-2"
             id="configurations"
           >
             Configurations
           </h2>
           <div id="form">
             <fieldset>
               <h3
                 class="heading-3"
                 id="Description"
               >
                 Description
               </h3>
               <fieldset>
                 <gr-autogrow-textarea
                   autocomplete="on"
                   class="description"
                   disabled=""
                   id="descriptionInput"
                   placeholder="<Insert repo description here>"
                 >
                 </gr-autogrow-textarea>
               </fieldset>
               <h3
                 class="heading-3"
                 id="Options"
               >
                 Repository Options
               </h3>
               <fieldset id="options">
                 <section>
                   <span class="title">
                     State
                   </span>
                   <span class="value">
                     <md-outlined-select
                       disabled=""
                       id="stateSelect"
                       value="ACTIVE"
                     >
                       <md-select-option
                         md-menu-item=""
                         tabindex="0"
                         value="ACTIVE"
                       >
                         <div slot="headline">
                           Active
                         </div>
                       </md-select-option>
                       <md-select-option
                         md-menu-item=""
                         tabindex="-1"
                         value="READ_ONLY"
                       >
                         <div slot="headline">
                           Read Only
                         </div>
                       </md-select-option>
                       <md-select-option
                         md-menu-item=""
                         tabindex="-1"
                         value="HIDDEN"
                       >
                         <div slot="headline">
                           Hidden
                         </div>
                       </md-select-option>
                     </md-outlined-select>
                   </span>
                 </section>
                 <section>
                   <span class="title">
                     Submit type
                   </span>
                   <span class="value">
                     <md-outlined-select
                       disabled=""
                       id="submitTypeSelect"
                       value="INHERIT"
                     >
                       <md-select-option
                         md-menu-item=""
                         tabindex="0"
                         value="INHERIT"
                       >
                         <div slot="headline">
                           Inherit (Merge if necessary)
                         </div>
                       </md-select-option>
                       <md-select-option
                         md-menu-item=""
                         tabindex="-1"
                         value="MERGE_IF_NECESSARY"
                       >
                         <div slot="headline">
                           Merge if necessary
                         </div>
                       </md-select-option>
                       <md-select-option
                         md-menu-item=""
                         tabindex="-1"
                         value="FAST_FORWARD_ONLY"
                       >
                         <div slot="headline">
                           Fast forward only
                         </div>
                       </md-select-option>
                       <md-select-option
                         md-menu-item=""
                         tabindex="-1"
                         value="REBASE_ALWAYS"
                       >
                         <div slot="headline">
                           Rebase Always
                         </div>
                       </md-select-option>
                       <md-select-option
                         md-menu-item=""
                         tabindex="-1"
                         value="REBASE_IF_NECESSARY"
                       >
                         <div slot="headline">
                           Rebase if necessary
                         </div>
                       </md-select-option>
                       <md-select-option
                         md-menu-item=""
                         tabindex="-1"
                         value="MERGE_ALWAYS"
                       >
                         <div slot="headline">
                           Merge always
                         </div>
                       </md-select-option>
                       <md-select-option
                         md-menu-item=""
                         tabindex="-1"
                         value="CHERRY_PICK"
                       >
                         <div slot="headline">
                           Cherry pick
                         </div>
                       </md-select-option>
                     </md-outlined-select>
                   </span>
                 </section>
                 <section>
                   <span class="title">
                     Allow content merges
                   </span>
                   <span class="value">
                     <md-outlined-select
                       disabled=""
                       id="contentMergeSelect"
                       value="FALSE"
                     >
                       <md-select-option
                         md-menu-item=""
                         tabindex="0"
                         value="INHERIT"
                       >
                         <div slot="headline">
                           Inherit
                         </div>
                       </md-select-option>
                       <md-select-option
                         md-menu-item=""
                         tabindex="-1"
                         value="TRUE"
                       >
                         <div slot="headline">
                           True
                         </div>
                       </md-select-option>
                       <md-select-option
                         md-menu-item=""
                         tabindex="-1"
                         value="FALSE"
                       >
                         <div slot="headline">
                           False
                         </div>
                       </md-select-option>
                     </md-outlined-select>
                   </span>
                 </section>
                 <section>
                   <span class="title">
                     Create a new change for every commit not in the target branch
                   </span>
                   <span class="value">
                     <md-outlined-select
                       disabled=""
                       id="newChangeSelect"
                       value="FALSE"
                     >
                       <md-select-option
                         md-menu-item=""
                         tabindex="0"
                         value="INHERIT"
                       >
                         <div slot="headline">
                           Inherit
                         </div>
                       </md-select-option>
                       <md-select-option
                         md-menu-item=""
                         tabindex="-1"
                         value="TRUE"
                       >
                         <div slot="headline">
                           True
                         </div>
                       </md-select-option>
                       <md-select-option
                         md-menu-item=""
                         tabindex="-1"
                         value="FALSE"
                       >
                         <div slot="headline">
                           False
                         </div>
                       </md-select-option>
                     </md-outlined-select>
                   </span>
                 </section>
                 <section>
                   <span class="title">
                     Require Change-Id in commit message
                   </span>
                   <span class="value">
                     <md-outlined-select
                       disabled=""
                       id="requireChangeIdSelect"
                       value="FALSE"
                     >
                       <md-select-option
                         md-menu-item=""
                         tabindex="0"
                         value="INHERIT"
                       >
                         <div slot="headline">
                           Inherit
                         </div>
                       </md-select-option>
                       <md-select-option
                         md-menu-item=""
                         tabindex="-1"
                         value="TRUE"
                       >
                         <div slot="headline">
                           True
                         </div>
                       </md-select-option>
                       <md-select-option
                         md-menu-item=""
                         tabindex="-1"
                         value="FALSE"
                       >
                         <div slot="headline">
                           False
                         </div>
                       </md-select-option>
                     </md-outlined-select>
                   </span>
                 </section>
                 <section
                   class="repositorySettings showConfig"
                   id="enableSignedPushSettings"
                 >
                   <span class="title">
                     Enable signed push
                   </span>
                   <span class="value">
                     <md-outlined-select
                       disabled=""
                       id="enableSignedPush"
                       value="FALSE"
                     >
                       <md-select-option
                         md-menu-item=""
                         tabindex="0"
                         value="INHERIT"
                       >
                         <div slot="headline">
                           Inherit
                         </div>
                       </md-select-option>
                       <md-select-option
                         md-menu-item=""
                         tabindex="-1"
                         value="TRUE"
                       >
                         <div slot="headline">
                           True
                         </div>
                       </md-select-option>
                       <md-select-option
                         md-menu-item=""
                         tabindex="-1"
                         value="FALSE"
                       >
                         <div slot="headline">
                           False
                         </div>
                       </md-select-option>
                     </md-outlined-select>
                   </span>
                 </section>
                 <section
                   class="repositorySettings showConfig"
                   id="requireSignedPushSettings"
                 >
                   <span class="title">
                     Require signed push
                   </span>
                   <span class="value">
                     <md-outlined-select
                       disabled=""
                       id="requireSignedPush"
                       value="FALSE"
                     >
                       <md-select-option
                         md-menu-item=""
                         tabindex="0"
                         value="INHERIT"
                       >
                         <div slot="headline">
                           Inherit
                         </div>
                       </md-select-option>
                       <md-select-option
                         md-menu-item=""
                         tabindex="-1"
                         value="TRUE"
                       >
                         <div slot="headline">
                           True
                         </div>
                       </md-select-option>
                       <md-select-option
                         md-menu-item=""
                         tabindex="-1"
                         value="FALSE"
                       >
                         <div slot="headline">
                           False
                         </div>
                       </md-select-option>
                     </md-outlined-select>
                   </span>
                 </section>
                 <section>
                   <span class="title">
                     Reject implicit merges when changes are pushed for review
                   </span>
                   <span class="value">
                     <md-outlined-select
                       disabled=""
                       id="rejectImplicitMergesSelect"
                       value="FALSE"
                     >
                       <md-select-option
                         md-menu-item=""
                         tabindex="0"
                         value="INHERIT"
                       >
                         <div slot="headline">
                           Inherit
                         </div>
                       </md-select-option>
                       <md-select-option
                         md-menu-item=""
                         tabindex="-1"
                         value="TRUE"
                       >
                         <div slot="headline">
                           True
                         </div>
                       </md-select-option>
                       <md-select-option
                         md-menu-item=""
                         tabindex="-1"
                         value="FALSE"
                       >
                         <div slot="headline">
                           False
                         </div>
                       </md-select-option>
                     </md-outlined-select>
                   </span>
                 </section>
                 <section>
                   <span class="title">
                     Enable adding unregistered users as reviewers and CCs on changes
                   </span>
                   <span class="value">
                     <md-outlined-select
                       disabled=""
                       id="unRegisteredCcSelect"
                       value="FALSE"
                     >
                       <md-select-option
                         md-menu-item=""
                         tabindex="0"
                         value="INHERIT"
                       >
                         <div slot="headline">
                           Inherit
                         </div>
                       </md-select-option>
                       <md-select-option
                         md-menu-item=""
                         tabindex="-1"
                         value="TRUE"
                       >
                         <div slot="headline">
                           True
                         </div>
                       </md-select-option>
                       <md-select-option
                         md-menu-item=""
                         tabindex="-1"
                         value="FALSE"
                       >
                         <div slot="headline">
                           False
                         </div>
                       </md-select-option>
                     </md-outlined-select>
                   </span>
                 </section>
                 <section>
                   <span class="title">
                     Set all new changes private by default
                   </span>
                   <span class="value">
                     <md-outlined-select
                       disabled=""
                       id="setAllnewChangesPrivateByDefaultSelect"
                       value="FALSE"
                     >
                       <md-select-option
                         md-menu-item=""
                         tabindex="0"
                         value="INHERIT"
                       >
                         <div slot="headline">
                           Inherit
                         </div>
                       </md-select-option>
                       <md-select-option
                         md-menu-item=""
                         tabindex="-1"
                         value="TRUE"
                       >
                         <div slot="headline">
                           True
                         </div>
                       </md-select-option>
                       <md-select-option
                         md-menu-item=""
                         tabindex="-1"
                         value="FALSE"
                       >
                         <div slot="headline">
                           False
                         </div>
                       </md-select-option>
                     </md-outlined-select>
                   </span>
                 </section>
                 <section>
                   <span class="title">
                     Set new changes to "work in progress" by default
                   </span>
                   <span class="value">
                     <md-outlined-select
                       disabled=""
                       id="setAllNewChangesWorkInProgressByDefaultSelect"
                       value="FALSE"
                     >
                       <md-select-option
                         md-menu-item=""
                         tabindex="0"
                         value="INHERIT"
                       >
                         <div slot="headline">
                           Inherit
                         </div>
                       </md-select-option>
                       <md-select-option
                         md-menu-item=""
                         tabindex="-1"
                         value="TRUE"
                       >
                         <div slot="headline">
                           True
                         </div>
                       </md-select-option>
                       <md-select-option
                         md-menu-item=""
                         tabindex="-1"
                         value="FALSE"
                       >
                         <div slot="headline">
                           False
                         </div>
                       </md-select-option>
                     </md-outlined-select>
                   </span>
                 </section>
                 <section>
                   <span class="title">
                     Maximum Git object size limit
                   </span>
                   <span class="value">
                     <md-outlined-text-field
                       autocomplete=""
                       class="showBlueFocusBorder"
                       disabled=""
                       id="maxGitObjSizeInput"
                       inputmode=""
                       min="0"
                       type="number"
                     >
                     </md-outlined-text-field>
                   </span>
                 </section>
                 <section>
                   <span class="title">
                     Match authored date with committer date upon submit
                   </span>
                   <span class="value">
                     <md-outlined-select
                       disabled=""
                       id="matchAuthoredDateWithCommitterDateSelect"
                       value="FALSE"
                     >
                       <md-select-option
                         md-menu-item=""
                         tabindex="0"
                         value="INHERIT"
                       >
                         <div slot="headline">
                           Inherit
                         </div>
                       </md-select-option>
                       <md-select-option
                         md-menu-item=""
                         tabindex="-1"
                         value="TRUE"
                       >
                         <div slot="headline">
                           True
                         </div>
                       </md-select-option>
                       <md-select-option
                         md-menu-item=""
                         tabindex="-1"
                         value="FALSE"
                       >
                         <div slot="headline">
                           False
                         </div>
                       </md-select-option>
                     </md-outlined-select>
                   </span>
                 </section>
                 <section>
                   <span class="title">
                     Reject empty commit upon submit
                   </span>
                   <span class="value">
                     <md-outlined-select
                       disabled=""
                       id="rejectEmptyCommitSelect"
                       value="FALSE"
                     >
                       <md-select-option
                         md-menu-item=""
                         tabindex="0"
                         value="INHERIT"
                       >
                         <div slot="headline">
                           Inherit
                         </div>
                       </md-select-option>
                       <md-select-option
                         md-menu-item=""
                         tabindex="-1"
                         value="TRUE"
                       >
                         <div slot="headline">
                           True
                         </div>
                       </md-select-option>
                       <md-select-option
                         md-menu-item=""
                         tabindex="-1"
                         value="FALSE"
                       >
                         <div slot="headline">
                           False
                         </div>
                       </md-select-option>
                     </md-outlined-select>
                   </span>
                 </section>
               </fieldset>
               <h3
                 class="heading-3"
                 id="Options"
               >
                 Contributor Agreements
               </h3>
               <fieldset id="agreements">
                 <section>
                   <span class="title">
                     Require a valid contributor agreement to upload
                   </span>
                   <span class="value">
                     <md-outlined-select
                       disabled=""
                       id="contributorAgreementSelect"
                       value="FALSE"
                     >
                       <md-select-option
                         md-menu-item=""
                         tabindex="0"
                         value="INHERIT"
                       >
                         <div slot="headline">
                           Inherit
                         </div>
                       </md-select-option>
                       <md-select-option
                         md-menu-item=""
                         tabindex="-1"
                         value="TRUE"
                       >
                         <div slot="headline">
                           True
                         </div>
                       </md-select-option>
                       <md-select-option
                         md-menu-item=""
                         tabindex="-1"
                         value="FALSE"
                       >
                         <div slot="headline">
                           False
                         </div>
                       </md-select-option>
                     </md-outlined-select>
                   </span>
                 </section>
                 <section>
                   <span class="title">
                     Require Signed-off-by in commit message
                   </span>
                   <span class="value">
                     <md-outlined-select
                       disabled=""
                       id="useSignedOffBySelect"
                       value="FALSE"
                     >
                       <md-select-option
                         md-menu-item=""
                         tabindex="0"
                         value="INHERIT"
                       >
                         <div slot="headline">
                           Inherit
                         </div>
                       </md-select-option>
                       <md-select-option
                         md-menu-item=""
                         tabindex="-1"
                         value="TRUE"
                       >
                         <div slot="headline">
                           True
                         </div>
                       </md-select-option>
                       <md-select-option
                         md-menu-item=""
                         tabindex="-1"
                         value="FALSE"
                       >
                         <div slot="headline">
                           False
                         </div>
                       </md-select-option>
                     </md-outlined-select>
                   </span>
                 </section>
               </fieldset>
               <gr-button
                 aria-disabled="true"
                 disabled=""
                 id="saveBtn"
                 role="button"
                 tabindex="-1"
               >
                 Save Changes
               </gr-button>
               <gr-button
                 aria-disabled="true"
                 disabled=""
                 id="saveReviewBtn"
                 role="button"
                 tabindex="-1"
               >
                 Save For Review
               </gr-button>
             </fieldset>
             <gr-endpoint-decorator name="repo-config">
               <gr-endpoint-param name="repoName">
               </gr-endpoint-param>
               <gr-endpoint-param name="readOnly">
               </gr-endpoint-param>
             </gr-endpoint-decorator>
           </div>
         </div>
       </div>
    `,
      {ignoreTags: ['option']}
    );
  });

  test('render loading', async () => {
    element.repo = REPO as RepoName;
    element.loading = true;
    await element.updateComplete;
    // prettier and shadowDom assert do not agree about span.title wrapping
    assert.shadowDom.equal(
      element,
      /* prettier-ignore */ /* HTML */ `
      <div class="gr-form-styles main read-only">
        <div class="info">
          <h1 class="heading-1" id="Title">test-repo</h1>
          <hr />
          <div>
            <a href="">
              <gr-button
                aria-disabled="true"
                disabled=""
                link=""
                role="button"
                tabindex="-1"
              >
                Browse
              </gr-button>
            </a>
            <a href="/q/project:test-repo">
              <gr-button
                aria-disabled="false"
                link=""
                role="button"
                tabindex="0"
              >
                View Changes
              </gr-button>
            </a>
          </div>
        </div>
        <div id="loading">Loading...</div>
      </div>
    `,
      {ignoreTags: ['option']}
    );
  });

  test('_computePluginData', async () => {
    element.repoConfig = {
      ...createConfig(),
      plugin_config: {},
    };
    await element.updateComplete;
    assert.deepEqual(element.computePluginData(), []);

    element.repoConfig.plugin_config = {
      'test-plugin': {
        test: {display_name: 'test plugin', type: 'STRING'},
      } as PluginParameterToConfigParameterInfoMap,
    };
    await element.updateComplete;
    assert.deepEqual(element.computePluginData(), [
      {
        name: 'test-plugin',
        config: {
          test: {
            display_name: 'test plugin',
            type: 'STRING' as ConfigParameterInfoType,
          },
        },
      },
    ]);
  });

  test('handlePluginConfigChanged', async () => {
    const requestUpdateStub = sinon.stub(element, 'requestUpdate');
    element.repoConfig = {
      ...createConfig(),
      plugin_config: {},
    };
    element.handlePluginConfigChanged({
      detail: {
        name: 'test',
        config: {
          test: {display_name: 'test plugin', type: 'STRING'},
        } as PluginParameterToConfigParameterInfoMap,
      },
    });
    await element.updateComplete;

    assert.deepEqual(element.repoConfig.plugin_config!.test, {
      test: {display_name: 'test plugin', type: 'STRING'},
    } as PluginParameterToConfigParameterInfoMap);
    assert.isTrue(requestUpdateStub.called);
  });

  test('render download commands', async () => {
    element.repo = REPO as RepoName;
    await element.loadRepo();
    element.schemesObj = SCHEMES;
    await element.updateComplete;
    const content = queryAndAssert<HTMLDivElement>(element, '#downloadContent');
    assert.dom.equal(
      content,
      /* HTML */ `
        <div id="downloadContent">
          <h2 class="heading-2" id="download">Download</h2>
          <fieldset>
            <gr-download-commands id="downloadCommands"></gr-download-commands>
          </fieldset>
        </div>
      `
    );
  });

  test('form defaults to read only', () => {
    assert.isTrue(element.readOnly);
  });

  test('form defaults to read only when not logged in', async () => {
    element.repo = REPO as RepoName;
    await element.loadRepo();
    assert.isTrue(element.readOnly);
  });

  test('form defaults to read only when logged in and not admin', async () => {
    element.repo = REPO as RepoName;

    stubRestApi('getRepoAccess').callsFake(() =>
      Promise.resolve({
        'test-repo': {
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
    await element.loadRepo();
    assert.isTrue(element.readOnly);
  });

  test('all form elements are disabled when not admin', async () => {
    element.repo = REPO as RepoName;
    await element.loadRepo();
    await element.updateComplete;
    const formFields = getFormFields();
    for (const field of formFields) {
      assert.isTrue(field.hasAttribute('disabled'));
    }
  });

  test('formatBooleanSelect', () => {
    let item: InheritedBooleanInfo = {
      ...createInheritedBoolean(true),
      inherited_value: true,
    };
    assert.deepEqual(element.formatBooleanSelect(item), [
      {
        label: 'Inherit (true)',
        value: 'INHERIT',
      },
      {
        label: 'True',
        value: 'TRUE',
      },
      {
        label: 'False',
        value: 'FALSE',
      },
    ]);

    item = {...createInheritedBoolean(false), inherited_value: false};
    assert.deepEqual(element.formatBooleanSelect(item), [
      {
        label: 'Inherit (false)',
        value: 'INHERIT',
      },
      {
        label: 'True',
        value: 'TRUE',
      },
      {
        label: 'False',
        value: 'FALSE',
      },
    ]);

    // For items without inherited values
    item = createInheritedBoolean(false);
    assert.deepEqual(element.formatBooleanSelect(item), [
      {
        label: 'Inherit',
        value: 'INHERIT',
      },
      {
        label: 'True',
        value: 'TRUE',
      },
      {
        label: 'False',
        value: 'FALSE',
      },
    ]);
  });

  test('fires page-error', async () => {
    repoStub.restore();

    element.repo = 'test' as RepoName;

    const pageErrorFired = mockPromise();
    const response = {...new Response(), status: 404};
    stubRestApi('getProjectConfig').callsFake((_: any, errFn: any) => {
      if (errFn !== undefined) {
        errFn(response);
      }
      return Promise.resolve(undefined);
    });
    addListenerForTest(document, 'page-error', e => {
      assert.deepEqual((e as PageErrorEvent).detail.response, response);
      pageErrorFired.resolve();
    });

    element.loadRepo();
    await pageErrorFired;
  });

  suite('admin', () => {
    const testRepoAccess: ProjectAccessInfo = {
      revision: 'xxxx',
      local: {
        'refs/*': {
          permissions: {
            owner: {
              rules: {xxx: {action: PermissionAction.ALLOW, force: false}},
            },
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
    };
    let getRepoAccessStub: sinon.SinonStub;
    setup(() => {
      element.repo = REPO as RepoName;
      loggedInStub.returns(Promise.resolve(true));
      getRepoAccessStub = stubRestApi('getRepoAccess').callsFake(() =>
        Promise.resolve({
          'test-repo': testRepoAccess,
        } as RepoAccessInfoMap)
      );
    });

    test('all form elements are enabled', async () => {
      await element.loadRepo();
      await element.updateComplete;
      const formFields = getFormFields();
      for (const field of formFields) {
        assert.isFalse(field.hasAttribute('disabled'));
      }
      assert.isFalse(element.loading);
    });

    test('state gets set correctly', async () => {
      await element.loadRepo();
      await element.updateComplete;
      assert.equal(element.repoConfig!.state, RepoState.ACTIVE);
      assert.equal(
        queryAndAssert<MdOutlinedSelect>(element, '#stateSelect').value,
        RepoState.ACTIVE
      );
    });

    test('inherited submit type value is calculated correctly', async () => {
      await element.loadRepo();
      await element.updateComplete;
      const sel = queryAndAssert<MdOutlinedSelect>(
        element,
        '#submitTypeSelect'
      );
      assert.equal(sel.value, 'INHERIT');
      assert.equal(
        sel.querySelector('md-select-option')!.textContent?.trim(),
        'Inherit (Merge if necessary)'
      );
    });

    test('fields update and save correctly', async () => {
      const configInputObj = {
        description: 'new description',
        use_contributor_agreements: InheritedBooleanInfoConfiguredValue.TRUE,
        use_content_merge: InheritedBooleanInfoConfiguredValue.TRUE,
        use_signed_off_by: InheritedBooleanInfoConfiguredValue.TRUE,
        create_new_change_for_all_not_in_target:
          InheritedBooleanInfoConfiguredValue.TRUE,
        require_change_id: InheritedBooleanInfoConfiguredValue.TRUE,
        enable_signed_push: InheritedBooleanInfoConfiguredValue.TRUE,
        require_signed_push: InheritedBooleanInfoConfiguredValue.TRUE,
        reject_implicit_merges: InheritedBooleanInfoConfiguredValue.TRUE,
        private_by_default: InheritedBooleanInfoConfiguredValue.TRUE,
        work_in_progress_by_default: InheritedBooleanInfoConfiguredValue.TRUE,
        match_author_to_committer_date:
          InheritedBooleanInfoConfiguredValue.TRUE,
        reject_empty_commit: InheritedBooleanInfoConfiguredValue.TRUE,
        max_object_size_limit: '10' as MaxObjectSizeLimitInfo,
        submit_type: SubmitType.FAST_FORWARD_ONLY,
        state: RepoState.READ_ONLY,
        enable_reviewer_by_email: InheritedBooleanInfoConfiguredValue.TRUE,
      };

      const saveStub = stubRestApi('saveRepoConfig').callsFake(() =>
        Promise.resolve(new Response())
      );

      await element.loadRepo();

      const button = queryAndAssert<GrButton>(element, 'gr-button#saveBtn');
      assert.isTrue(button.hasAttribute('disabled'));
      assert.isFalse(
        queryAndAssert<HTMLHeadingElement>(
          element,
          '#Title'
        ).classList.contains('edited')
      );
      const descriptionInput = queryAndAssert<GrAutogrowTextarea>(
        element,
        '#descriptionInput'
      );
      descriptionInput.value = configInputObj.description;
      descriptionInput.dispatchEvent(
        new Event('input', {bubbles: true, composed: true})
      );
      const stateSelect = queryAndAssert<MdOutlinedSelect>(
        element,
        '#stateSelect'
      );
      stateSelect.value = configInputObj.state;
      stateSelect.dispatchEvent(
        new CustomEvent('change', {
          composed: true,
          bubbles: true,
        })
      );
      const submitTypeSelect = queryAndAssert<MdOutlinedSelect>(
        element,
        '#submitTypeSelect'
      );
      submitTypeSelect.value = configInputObj.submit_type;
      submitTypeSelect.dispatchEvent(
        new CustomEvent('change', {
          composed: true,
          bubbles: true,
        })
      );
      const contentMergeSelect = queryAndAssert<MdOutlinedSelect>(
        element,
        '#contentMergeSelect'
      );
      contentMergeSelect.value = configInputObj.use_content_merge;
      contentMergeSelect.dispatchEvent(
        new CustomEvent('change', {
          composed: true,
          bubbles: true,
        })
      );
      const newChangeSelect = queryAndAssert<MdOutlinedSelect>(
        element,
        '#newChangeSelect'
      );
      newChangeSelect.value =
        configInputObj.create_new_change_for_all_not_in_target;
      newChangeSelect.dispatchEvent(
        new CustomEvent('change', {
          composed: true,
          bubbles: true,
        })
      );
      const requireChangeIdSelect = queryAndAssert<MdOutlinedSelect>(
        element,
        '#requireChangeIdSelect'
      );
      requireChangeIdSelect.value = configInputObj.require_change_id;
      requireChangeIdSelect.dispatchEvent(
        new CustomEvent('change', {
          composed: true,
          bubbles: true,
        })
      );
      const enableSignedPush = queryAndAssert<MdOutlinedSelect>(
        element,
        '#enableSignedPush'
      );
      enableSignedPush.value = configInputObj.enable_signed_push;
      enableSignedPush.dispatchEvent(
        new CustomEvent('change', {
          composed: true,
          bubbles: true,
        })
      );
      const requireSignedPush = queryAndAssert<MdOutlinedSelect>(
        element,
        '#requireSignedPush'
      );
      requireSignedPush.value = configInputObj.require_signed_push;
      requireSignedPush.dispatchEvent(
        new CustomEvent('change', {
          composed: true,
          bubbles: true,
        })
      );
      const rejectImplicitMergesSelect = queryAndAssert<MdOutlinedSelect>(
        element,
        '#rejectImplicitMergesSelect'
      );
      rejectImplicitMergesSelect.value = configInputObj.reject_implicit_merges;
      rejectImplicitMergesSelect.dispatchEvent(
        new CustomEvent('change', {
          composed: true,
          bubbles: true,
        })
      );
      const setAllnewChangesPrivateByDefaultSelect =
        queryAndAssert<MdOutlinedSelect>(
          element,
          '#setAllnewChangesPrivateByDefaultSelect'
        );
      setAllnewChangesPrivateByDefaultSelect.value =
        configInputObj.private_by_default;
      setAllnewChangesPrivateByDefaultSelect.dispatchEvent(
        new CustomEvent('change', {
          composed: true,
          bubbles: true,
        })
      );
      const setAllNewChangesWorkInProgressByDefaultSelect =
        queryAndAssert<MdOutlinedSelect>(
          element,
          '#setAllNewChangesWorkInProgressByDefaultSelect'
        );
      setAllNewChangesWorkInProgressByDefaultSelect.value =
        configInputObj.work_in_progress_by_default;
      setAllNewChangesWorkInProgressByDefaultSelect.dispatchEvent(
        new CustomEvent('change', {
          composed: true,
          bubbles: true,
        })
      );
      const matchAuthoredDateWithCommitterDateSelect =
        queryAndAssert<MdOutlinedSelect>(
          element,
          '#matchAuthoredDateWithCommitterDateSelect'
        );
      matchAuthoredDateWithCommitterDateSelect.value =
        configInputObj.match_author_to_committer_date;
      matchAuthoredDateWithCommitterDateSelect.dispatchEvent(
        new CustomEvent('change', {
          composed: true,
          bubbles: true,
        })
      );
      const maxGitObjSizeInput = queryAndAssert<MdOutlinedTextField>(
        element,
        '#maxGitObjSizeInput'
      );
      maxGitObjSizeInput.value = String(configInputObj.max_object_size_limit);
      maxGitObjSizeInput.dispatchEvent(
        new Event('input', {
          composed: true,
          bubbles: true,
        })
      );
      const contributorAgreementSelect = queryAndAssert<MdOutlinedSelect>(
        element,
        '#contributorAgreementSelect'
      );
      contributorAgreementSelect.value =
        configInputObj.use_contributor_agreements;
      contributorAgreementSelect.dispatchEvent(
        new CustomEvent('change', {
          composed: true,
          bubbles: true,
        })
      );
      const useSignedOffBySelect = queryAndAssert<MdOutlinedSelect>(
        element,
        '#useSignedOffBySelect'
      );
      useSignedOffBySelect.value = configInputObj.use_signed_off_by;
      useSignedOffBySelect.dispatchEvent(
        new CustomEvent('change', {
          composed: true,
          bubbles: true,
        })
      );
      const rejectEmptyCommitSelect = queryAndAssert<MdOutlinedSelect>(
        element,
        '#rejectEmptyCommitSelect'
      );
      rejectEmptyCommitSelect.value = configInputObj.reject_empty_commit;
      rejectEmptyCommitSelect.dispatchEvent(
        new CustomEvent('change', {
          composed: true,
          bubbles: true,
        })
      );
      const unRegisteredCcSelect = queryAndAssert<MdOutlinedSelect>(
        element,
        '#unRegisteredCcSelect'
      );
      unRegisteredCcSelect.value = configInputObj.enable_reviewer_by_email;
      unRegisteredCcSelect.dispatchEvent(
        new CustomEvent('change', {
          composed: true,
          bubbles: true,
        })
      );

      await element.updateComplete;

      assert.isFalse(button.hasAttribute('disabled'));
      assert.isTrue(
        queryAndAssert<HTMLHeadingElement>(
          element,
          '#configurations'
        ).classList.contains('edited')
      );

      const formattedObj = element.formatRepoConfigForSave(element.repoConfig);
      assert.deepEqual(formattedObj, configInputObj);

      await element.handleSaveRepoConfig();
      await element.updateComplete;
      assert.isTrue(button.hasAttribute('disabled'));
      assert.isFalse(
        queryAndAssert<HTMLHeadingElement>(
          element,
          '#Title'
        ).classList.contains('edited')
      );
      assert.isTrue(
        saveStub.lastCall.calledWithExactly(REPO as RepoName, configInputObj)
      );
    });

    test('saveReviewBtn visible', async () => {
      await element.loadRepo();
      await element.updateComplete;
      const button = queryAndAssert<GrButton>(
        element,
        'gr-button#saveReviewBtn'
      );
      assert.isFalse(button.hasAttribute('hidden'));
    });

    test('saveBtn is disabled', async () => {
      getRepoAccessStub.callsFake(() =>
        Promise.resolve({
          'test-repo': {
            ...testRepoAccess,
            require_change_for_config_update: true,
          },
        } as RepoAccessInfoMap)
      );
      await element.loadRepo();
      await element.updateComplete;
      const button = queryAndAssert<GrButton>(element, 'gr-button#saveBtn');
      assert.isTrue(button.hasAttribute('disabled'));
    });

    test('saveReviewBtn', async () => {
      let resolver: (value: ChangeInfo | PromiseLike<ChangeInfo>) => void;
      const saveForReviewStub = stubRestApi('saveRepoConfigForReview').returns(
        new Promise(r => (resolver = r))
      );
      resolver!(createChange());
      const setUrlStub = sinon.stub(testResolver(navigationToken), 'setUrl');

      await element.loadRepo();
      await element.updateComplete;
      const input = queryAndAssert<GrAutogrowTextarea>(
        element,
        '#descriptionInput'
      );
      input.value = 'New description';
      input.dispatchEvent(new Event('input', {bubbles: true, composed: true}));
      await input.updateComplete;
      await element.updateComplete;
      const button = queryAndAssert<GrButton>(element, 'gr-button#saveBtn');
      assert.isFalse(button.hasAttribute('disabled'));
      queryAndAssert<GrButton>(element, 'gr-button#saveReviewBtn').click();
      await element.updateComplete;
      assert.isTrue(saveForReviewStub.called);
      assert.isTrue(setUrlStub.called);
      assert.isTrue(
        setUrlStub.lastCall.args?.[0]?.includes(`${createChange()._number}`)
      );
    });
  });
});
