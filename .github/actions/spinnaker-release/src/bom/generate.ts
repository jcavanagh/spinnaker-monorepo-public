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
  if(core.getInput('add_to_versions_yml') == 'true') {
    versionsYml.addVersion(core.getInput('version'));
  }
  return versionsYml;
}

async function publish(bom: Bom, versionDotYml: VersionsDotYml) {
  const dryRun: string = core.getInput('dry_run');
  if (dryRun != 'true') {
    await bom.publish();
    await versionDotYml.publish();
  } else {
    core.info(
      `Not publishing - dryRun is true.  Set INPUT_DRYRUN=false if you wish to publish.`,
    );
  }
}
