const fs = require('fs');

const EXTERN_NAMES = [
  'Gerrit',
  'GrAnnotation',
  'GrAttributeHelper',
  'GrChangeActionsInterface',
  'GrChangeReplyInterface',
  'GrDiffBuilder',
  'GrDiffBuilderImage',
  'GrDiffBuilderSideBySide',
  'GrDiffBuilderUnified',
  'GrDiffGroup',
  'GrDiffLine',
  'GrDomHooks',
  'GrEtagDecorator',
  'GrGapiAuth',
  'GrGerritAuth',
  'GrLinkTextParser',
  'GrPluginEndpoints',
  'GrRangeNormalizer',
  'GrReporting',
  'GrReviewerUpdatesParser',
  'GrThemeApi',
  'moment',
  'page',
  'util'];

fs.readdir('./polygerrit-ui/temp/behaviors/', (err, data) => {
  if (err) {
    console.log('error /polygerrit-ui/temp/behaviors/ directory');
  }
  const behaviors = data;
  const externs = [];

  for (const behavior of behaviors) {
    externs.push({
      path: `./polygerrit-ui/temp/behaviors/${behavior}`,
      src: fs.readFileSync(
          `./polygerrit-ui/temp/behaviors/${behavior}`, 'utf-8'),
    });
  }

  const mappings = JSON.parse(fs.readFileSync(
      `./polygerrit-ui/temp/map.json`, 'utf-8'));


  externs.push({
    path: 'custom-externs.js',
    src: '/** @externs */' +
        EXTERN_NAMES.map( name => { return `var ${name};`; }).join(' '),
  });

  for (key of Object.keys(mappings)) {
    if (mappings[key].html && mappings[key].js) {
      require('fried-twinkie').checkTemplate(
          mappings[key].html,
          mappings[key].js,
          'polygerrit.' + mappings[key].package,
          externs
      );
    }
  }
});
