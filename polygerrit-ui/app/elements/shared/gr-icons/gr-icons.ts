/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
<<<<<<< PATCH SET (42ec6b UI: Replce @polymer/iron-iconset-svg with gr-iconset)
import '../gr-iconset/gr-iconset';
||||||| BASE
import '@polymer/iron-iconset-svg/iron-iconset-svg';
=======
import '@polymer/iron-icon/iron-icon';
import '@polymer/iron-iconset-svg/iron-iconset-svg';
>>>>>>> BASE      (fe20ba Include test name in long-running test warning)
const $_documentContainer = document.createElement('template');

$_documentContainer.innerHTML = `<gr-iconset name="gr-icons" size="24">
  <svg>
    <defs>
      <!-- This SVG is a copy from material.io https://fonts.google.com/icons?selected=Material+Icons&icon.query=swap_horiz-->
      <g id="swapHoriz"><path d="M0 0h24v24H0z" fill="none"/><path d="M6.99 11L3 15l3.99 4v-3H14v-2H6.99v-3zM21 9l-3.99-4v3H10v2h7.01v3L21 9z"/></g>
      <!-- This SVG is a copy from material.io https://fonts.google.com/icons?selected=Material%20Icons%3Aplay_arrow-->
      <g id="playArrow"><path d="M0 0h24v24H0z" fill="none"/><path d="M8 5v14l11-7z"/></g>
      <!-- This SVG is a copy from material.io https://fonts.google.com/icons?selected=Material%20Icons%3Apause-->
      <g id="pause"><path d="M0 0h24v24H0z" fill="none"/><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></g>
    </defs>
  </svg>
</gr-iconset>`;

document.head.appendChild($_documentContainer.content);
