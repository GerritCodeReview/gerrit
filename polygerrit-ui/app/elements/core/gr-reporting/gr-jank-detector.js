/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
(function() {
  'use strict';

  const JANK_SLEEP_TIME_MS = 1000;

  const GrJankDetector = {
    // Slowdowns counter.
    jank: 0,
    fps: 0,
    _lastFrameTime: 0,

    start() {
      this._requestAnimationFrame(this._detect.bind(this));
    },

    _requestAnimationFrame(callback) {
      window.requestAnimationFrame(callback);
    },

    _detect(now) {
      if (this._lastFrameTime === 0) {
        this._lastFrameTime = now;
        this.fps = 0;
        this._requestAnimationFrame(this._detect.bind(this));
        return;
      }
      const fpsNow = 1000/(now - this._lastFrameTime);
      this._lastFrameTime = now;
      // Calculate moving average within last 3 measurements.
      this.fps = this.fps === 0 ? fpsNow : ((this.fps * 2 + fpsNow) / 3);
      if (this.fps > 10) {
        this._requestAnimationFrame(this._detect.bind(this));
      } else {
        this.jank++;
        console.warn('JANK', this.jank);
        this._lastFrameTime = 0;
        window.setTimeout(
            () => this._requestAnimationFrame(this._detect.bind(this)),
            JANK_SLEEP_TIME_MS);
      }
    },
  };

  window.GrJankDetector = GrJankDetector;
})();
