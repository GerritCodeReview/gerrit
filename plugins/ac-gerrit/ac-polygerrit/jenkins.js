const trackQueue = function(url, interval, retries) {
  const instance = axios.create();

  const sleepRequest = (milliseconds, originalRequest) => {
    return new Promise((resolve, reject) => {
      setTimeout(() => resolve(instance(originalRequest)), milliseconds);
    });
  };

  let attempt = 0;
  const onError = error => {
    const { config } = error;
    const originalRequest = config;
    ++attempt;

    if (attempt > retries)
      return Promise.reject(error);
    else
      return sleepRequest(interval, originalRequest);
  };
  instance.interceptors.response.use(response => {
    if (response.data.executable)
      return response.data;
    else
      return onError({config: response.config});
  }, onError);
  return instance(url);
};

const jenkinsHeaders = async function(jenkins) {
  const headers = {
    'Content-type': 'application/x-www-form-urlencoded',
  };
  try {
    const crumbRes = await axios({
      url: jenkins + '/crumbIssuer/api/xml?xpath=concat(//crumbRequestField,":",//crumb)',
      withCredentials: true,
    });
    const crumb = crumbRes.data.split(':');
    headers[crumb[0]] = crumb[1];
  } catch (err) {
    // console.warn('Failed to generate Jenkins-Crumb');
  }
  return headers;
};

const jenkinsBuild = async function(widget, jenkins, job, params, linkTitle, queuedTitle) {
  let formData = '';
  for (let p in params) {
    formData += '&' + encodeURIComponent(p) + "=";
    if (params[p])
      formData += encodeURIComponent(params[p]);
  }
  formData = formData.substring(1); // Remove leading &
  widget.buttonText = 'Working...';
  const jobLink = jenkins + '/job/' + job;
  try {
    const headers = await jenkinsHeaders(jenkins);
    const response = await axios({
      url: jobLink + '/buildWithParameters?delay=0sec',
      withCredentials: true,
      method: 'post',
      data: formData,
      headers
    });
    widget.buttonText = 'Build Started ';
    try {
      const queueData = await trackQueue(response.headers.location + 'api/json', 500, 6);
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
};
