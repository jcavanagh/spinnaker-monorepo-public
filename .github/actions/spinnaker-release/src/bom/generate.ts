import * as core from '@actions/core';
import { Bom } from './bom';
import { services } from './services/all';
import { fromCurrent, VersionsDotYml } from './versionsDotYml';

export async function generate(): Promise<void> {
  const bom = generateBom();
  const versionsYml = await generateVersionsYml();

  core.info(`Generated BoM: \n${bom.toString()}`);
  core.info(`Generated versions.yml: \n${versionsYml.toString()}`);
  await publish(bom, versionsYml);
}

function generateBom(): Bom {
  core.info('Running BoM generator');

  const bom = new Bom(core.getInput('version'));
  for (const service of services) {
    bom.setService(service);
  }

  return bom;
}

async function generateVersionsYml(): Promise<VersionsDotYml> {
  core.info('Running versions.yml generator');
  const versionsYml = await fromCurrent();
  if(core.getInput('add-to-versions-yml') == 'true') {
    versionsYml.addVersion(core.getInput('version'));
  }
  return versionsYml;
}

async function publish(bom: Bom, versionDotYml: VersionsDotYml) {
  const dryRun: string = core.getInput('dry-run');
  if (dryRun != 'true') {
    await bom.publish();
    await versionDotYml.publish();
  } else {
    core.info(
      `Not publishing - dry-run is true.  Set INPUT_DRY_RUN=false if you wish to publish.`,
    );
  }
}
