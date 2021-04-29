/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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

/**
 * This plugin will upgrade your +1 on Code-Review label
 * to +2 and show a message below the voting labels.
 */
Gerrit.install(plugin => {
  const replyApi = plugin.changeReply();
  let wasSuggested = false;
  plugin.on('showchange', () => {
    wasSuggested = false;
  });
  const CODE_REVIEW = 'Code-Review';
  replyApi.addLabelValuesChangedCallback(({name, value}) => {
    if (wasSuggested && name === CODE_REVIEW) {
      replyApi.showMessage('');
      wasSuggested = false;
    } else if (replyApi.getLabelValue(CODE_REVIEW) === '+1' && !wasSuggested) {
      replyApi.setLabelValue(CODE_REVIEW, '+2');
      replyApi.showMessage(`Suggested ${CODE_REVIEW} upgrade: +2`);
      wasSuggested = true;
    }
  });
});
