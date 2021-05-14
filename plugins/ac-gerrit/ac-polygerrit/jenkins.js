/**
 * @license
 * Copyright (C) 2021 AudioCodes Ltd.
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

export class Jenkins {
  sleep(interval) {
    return new Promise((resolve, reject) => {
      setTimeout(() => resolve(), interval);
    });
  }

  async trackQueue(url, interval, retries) {
    let error;
    for (let attempt = 0; attempt < retries; ++attempt) {
      try {
        const res = await ((await fetch(url)).json());
        if (res.executable)
          return res;
      } catch (err) {
        error = err;
      }
      await this.sleep(interval);
    }
    return Promise.reject(error);
  }

  async headers(jenkins) {
    const headers = {
      'Content-type': 'application/x-www-form-urlencoded',
    };
    try {
      const crumbRes = await fetch(jenkins + '/crumbIssuer/api/xml?xpath=concat(//crumbRequestField,":",//crumb)', {
        credentials: 'include',
      });
      const res = await crumbRes.text();
      const crumb = res.split(':');
      headers[crumb[0]] = crumb[1];
    } catch (err) {
      // console.warn('Failed to generate Jenkins-Crumb');
    }
    return headers;
  }

  async build(widget, jenkins, job, params, linkTitle, queuedTitle) {
    let formData = '';
    for (const p in params) {
      if (!params.hasOwnProperty(p))
        continue;
      formData += '&' + encodeURIComponent(p) + '=';
      if (params[p])
        formData += encodeURIComponent(params[p]);
    }
    formData = formData.substring(1); // Remove leading &
    widget.buttonText = 'Working...';
    const jobLink = jenkins + '/job/' + job;
    try {
      const headers = await this.headers(jenkins);
      const response = await fetch(jobLink + '/buildWithParameters?delay=0sec', {
        credentials: 'include',
        mode: 'cors',
        method: 'post',
        body: formData,
        headers,
      });
      if (!response.ok)
        throw new Error();
      widget.buttonText = 'Build Started ';
      try {
        const queueData = await this.trackQueue(response.headers.get('location') + 'api/json', 500, 6);
        widget.url = queueData.executable.url;
        widget.link = linkTitle;
      } catch (err) {
        widget.url = jenkins + '/job/' + job;
        widget.link = queuedTitle;
      }
    } catch (err) {
      widget.errorMessage = 'Not signed in to Jenkins. Please sign in and try again. ';
      widget.url = jenkins + '/login';
      widget.link = 'login';
      widget._enableStart(true);
    }
  }
}
