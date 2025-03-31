// import { generate } from './bom/generate';
//
// generate();

import * as util from './util';
import { forVersion } from './bom/changelog';

const version = util.getInput('version');
const previousVersion = util.getInput('previous-version');
forVersion(version, previousVersion).then((changelog) => {
  console.log(changelog.markdown);
  changelog.publish();
});
