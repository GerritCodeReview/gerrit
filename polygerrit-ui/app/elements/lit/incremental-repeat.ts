/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {directive, AsyncDirective} from 'lit/async-directive.js';
import {DirectiveParameters, ChildPart} from 'lit/directive.js';

interface RepeatOptions<T> {
  values: T[];
  mapFn?: (val: T, idx: number) => unknown;
  initialCount: number;
  targetFrameRate?: number;
}

interface RepeatState<T> {
  values: T[];
  mapFn?: (val: T, idx: number) => unknown;
  numToRender: number;
  incrementAmount: number;
  lastRenderedAt: number;
  targetFrameRate: number;
}

class IncrementalRepeat<T> extends AsyncDirective {
  private state?: RepeatState<T>;

  render(options: RepeatOptions<T>) {
    const values = options.values.slice(0, options.initialCount);
    if (options.mapFn) {
      return values.map(options.mapFn)
    }
    return values;
  }

  override update(_part: ChildPart, [options]: DirectiveParameters<this>) {
    if (options.values !== this.state?.values) {
      if (this.nextScheduledFrameWork)
        cancelAnimationFrame(this.nextScheduledFrameWork);

      this.state = {
        values: options.values,
        numToRender: options.initialCount,
        incrementAmount: options.initialCount,
        lastRenderedAt: performance.now(),
        targetFrameRate: options.targetFrameRate ?? 30,
      };
      this.nextScheduledFrameWork = requestAnimationFrame(
        this.animationFrameHandler
      );
    }
    return this.render(options);
  }

  private nextScheduledFrameWork: number | undefined;

  private animationFrameHandler = () => {
    this.nextScheduledFrameWork = undefined;
    if (!this.state) return;
    const now = performance.now();
    const frameRate = 1000 / (now - this.state.lastRenderedAt);
    if (frameRate < this.state.targetFrameRate) {
      this.state.incrementAmount = Math.max(
        1,
        Math.floor(this.state.incrementAmount / 2)
      );
    } else {
      this.state.incrementAmount = this.state.incrementAmount + 1;
    }
    this.state.lastRenderedAt = now;
    this.state.numToRender += this.state.incrementAmount;
    this.setValue(
      this.render({
        mapFn: this.state.mapFn,
        values: this.state.values,
        initialCount: this.state.numToRender,
        targetFrameRate: this.state.targetFrameRate,
      })
    );
    if (this.state.numToRender < this.state.values.length) {
      this.nextScheduledFrameWork = requestAnimationFrame(
        this.animationFrameHandler
      );
    }
  };
}

export const incrementalRepeat = directive(IncrementalRepeat);
