import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrHovercardRun} from '../../../elements/checks/gr-hovercard-run';

export interface PolymerDomRepeatEventModel<T> {
  /**
   * The item corresponding to the element in the dom-repeat.
   */
  item: T;

  /**
   * The index of the element in the dom-repeat.
   */
  index: number;
  get: (name: string) => T;
  set: (name: string, val: T) => void;
}

declare function wrapInPolymerDomRepeatEvent<T, U>(event: T, item: U): T & {model: PolymerDomRepeatEventModel<U>};
declare function setTextContent(content: unknown): void;
declare function useVars(...args: unknown[]): void;

type UnionToIntersection<T> = (
  T extends any ? (v: T) => void : never
  ) extends (v: infer K) => void
  ? K
  : never;

type AddNonDefinedProperties<T, P> = {
  [K in keyof P]: K extends keyof T ? T[K] : undefined;
};

type FlatUnion<T, TIntersect> = T extends any
  ? AddNonDefinedProperties<T, TIntersect>
  : never;

type AllUndefined<T> = {
  [P in keyof T]: undefined;
}

type UnionToAllUndefined<T> = T extends any ? AllUndefined<T> : any

type Flat<T> = FlatUnion<T, UnionToIntersection<UnionToAllUndefined<T>>>;

declare function __f<T>(obj: T): Flat<NonNullable<T>>;

declare function pc<T>(obj: T): PolymerDeepPropertyChange<T, T>;

declare function convert<T, U extends T>(obj: T): U;

export class GrHovercardRunCheck extends GrHovercardRun
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('id', `container`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `section`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('hidden', `${this.hideChip(this.run)}`);
      el.setAttribute('class', `chipRow`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `chip`);
    }
    {
      const el: HTMLElementTagNameMap['iron-icon'] = null!;
      useVars(el);
      el.icon = `gr-icons:${this.computeChipIcon(this.run)}`;
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
    }
    setTextContent(`${__f(this.run)!.status}`);

    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `section`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `sectionIcon`);
      el.setAttribute('hidden', `${this.hideHeaderSectionIcon(this.run)}`);
    }
    {
      const el: HTMLElementTagNameMap['iron-icon'] = null!;
      useVars(el);
      el.setAttribute('class', `${this.computeIcon(this.run)}`);
      el.icon = `gr-icons:${this.computeIcon(this.run)}`;
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `sectionContent`);
    }
    {
      const el: HTMLElementTagNameMap['h3'] = null!;
      useVars(el);
      el.setAttribute('class', `name heading-3`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
    }
    setTextContent(`${__f(this.run)!.checkName}`);

    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `section`);
      el.setAttribute('hidden', `${this.hideStatusSection(this.run)}`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `sectionIcon`);
    }
    {
      const el: HTMLElementTagNameMap['iron-icon'] = null!;
      useVars(el);
      el.setAttribute('class', `small`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `sectionContent`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('hidden', `${!__f(this.run)!.statusLink}`);
      el.setAttribute('class', `row`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['a'] = null!;
      useVars(el);
      el.href = this._convertUndefined(__f(this.run)!.statusLink);
    }
    {
      const el: HTMLElementTagNameMap['iron-icon'] = null!;
      useVars(el);
      el.setAttribute('class', `small link`);
    }
    setTextContent(`${this.computeHostName(__f(this.run)!.statusLink)}
            `);

    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('hidden', `${!__f(this.run)!.statusDescription}`);
      el.setAttribute('class', `row`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
    }
    setTextContent(`${__f(this.run)!.statusDescription}`);

    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `section`);
      el.setAttribute('hidden', `${this.hideAttempts(this.run)}`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `sectionIcon`);
    }
    {
      const el: HTMLElementTagNameMap['iron-icon'] = null!;
      useVars(el);
      el.setAttribute('class', `small`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `sectionContent`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('hidden', `${this.hideAttempts(this.run)}`);
      el.setAttribute('class', `row`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['dom-repeat'] = null!;
      useVars(el);
    }
    {
      const index = 0;
      const itemsIndexAs = 0;
      useVars(index, itemsIndexAs);
      for(const item of __f(this.run)!.attemptDetails!)
      {
        {
          const el: HTMLElementTagNameMap['div'] = null!;
          useVars(el);
        }
        {
          const el: HTMLElementTagNameMap['div'] = null!;
          useVars(el);
          el.setAttribute('class', `attemptIcon`);
        }
        {
          const el: HTMLElementTagNameMap['iron-icon'] = null!;
          useVars(el);
          el.setAttribute('class', `${__f(item)!.icon}`);
          el.icon = `gr-icons:${__f(item)!.icon}`;
        }
        {
          const el: HTMLElementTagNameMap['div'] = null!;
          useVars(el);
          el.setAttribute('class', `attemptNumber`);
        }
        setTextContent(`${this.computeAttempt(__f(item)!.attempt)}`);

      }
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `section`);
      el.setAttribute('hidden', `${this.hideTimestampSection(this.run)}`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `sectionIcon`);
    }
    {
      const el: HTMLElementTagNameMap['iron-icon'] = null!;
      useVars(el);
      el.setAttribute('class', `small`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `sectionContent`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('hidden', `${this.hideScheduled(this.run)}`);
      el.setAttribute('class', `row`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
    }
    setTextContent(`${this.computeDuration(__f(this.run)!.scheduledTimestamp)}`);

    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('hidden', `${!__f(this.run)!.startedTimestamp}`);
      el.setAttribute('class', `row`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
    }
    setTextContent(`${this.computeDuration(__f(this.run)!.startedTimestamp)}`);

    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('hidden', `${!__f(this.run)!.finishedTimestamp}`);
      el.setAttribute('class', `row`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
    }
    setTextContent(`${this.computeDuration(__f(this.run)!.finishedTimestamp)}`);

    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('hidden', `${this.hideCompletion(this.run)}`);
      el.setAttribute('class', `row`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
    }
    setTextContent(`${this.computeCompletionDuration(this.run)}`);

    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `section`);
      el.setAttribute('hidden', `${this.hideDescriptionSection(this.run)}`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `sectionIcon`);
    }
    {
      const el: HTMLElementTagNameMap['iron-icon'] = null!;
      useVars(el);
      el.setAttribute('class', `small`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `sectionContent`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('hidden', `${!__f(this.run)!.checkDescription}`);
      el.setAttribute('class', `row`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
    }
    setTextContent(`${__f(this.run)!.checkDescription}`);

    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('hidden', `${!__f(this.run)!.checkLink}`);
      el.setAttribute('class', `row`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['a'] = null!;
      useVars(el);
      el.href = this._convertUndefined(__f(this.run)!.checkLink);
    }
    {
      const el: HTMLElementTagNameMap['iron-icon'] = null!;
      useVars(el);
      el.setAttribute('class', `small link`);
    }
    setTextContent(`${this.computeHostName(__f(this.run)!.checkLink)}
            `);

    {
      const el: HTMLElementTagNameMap['dom-repeat'] = null!;
      useVars(el);
    }
    {
      const index = 0;
      const itemsIndexAs = 0;
      useVars(index, itemsIndexAs);
      for(const item of this.computeActions(this.run)!)
      {
        {
          const el: HTMLElementTagNameMap['div'] = null!;
          useVars(el);
          el.setAttribute('class', `action`);
        }
        {
          const el: HTMLElementTagNameMap['gr-checks-action'] = null!;
          useVars(el);
          el.action = item;
        }
      }
    }
  }
}

