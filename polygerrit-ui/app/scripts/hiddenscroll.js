// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

(function(window) {
  window.Gerrit = window.Gerrit || {};
  if (window.Gerrit.hasOwnProperty('hiddenscroll')) { return; }

  window.Gerrit.hiddenscroll = undefined;

  window.addEventListener('WebComponentsReady', () => {
    const elem = document.createElement('div');
    elem.setAttribute(
        'style', 'width:100px;height:100px;overflow:scroll');
    document.body.appendChild(elem);
    window.Gerrit.hiddenscroll = elem.offsetWidth === elem.clientWidth;
    elem.remove();
  });
})(window);
