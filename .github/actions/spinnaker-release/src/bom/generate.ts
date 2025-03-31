import * as core from '@actions/core';
import { Bom } from './bom';
import { services } from './services/all';
import * as util from '../util';
import { fromCurrent, VersionsDotYml } from './versionsDotYml';
import { forVersion, Changelog } from './changelog';

export async function generate(): Promise<void> {
  const bom = await generateBom();
  const versionsYml = await generateVersionsYml();
  const changelog = await generateChangelog();

  core.info(`Generated BoM: \n${bom.toString()}`);
  core.info(`Generated versions.yml: \n${versionsYml.toString()}`);
  await publish(bom, versionsYml, changelog);
}

async function generateBom(): Promise<Bom> {
  core.info('Running BoM generator');

  const version = util.getInput('version');
  const bom = new Bom(version);
  for (const service of services) {
    bom.setService(service);
  }

  return bom;
}

async function generateChangelog(): Promise<Changelog> {
  core.info('Generating changelog');

  const version = util.getInput('version');
  const previousVersion = util.getInput('previous-version');
  return forVersion(version, previousVersion);
}

async function generateVersionsYml(): Promise<VersionsDotYml> {
  core.info('Running versions.yml generator');
  const versionsYml = await fromCurrent();
  if (util.getInput('add-to-versions-yml') == 'true') {
    versionsYml.addVersion(util.getInput('version'));
  }
  return versionsYml;
}

async function publish(
  bom: Bom,
  versionDotYml: VersionsDotYml,
  changelog: Changelog,
) {
  const dryRun: string = util.getInput('dry-run');
  if (dryRun == 'false') {
    await bom.publish();
    await versionDotYml.publish();
    await changelog.publish();
  } else {
    core.info(
      `Not publishing - dry-run is ${dryRun}.  Set dry-run=false if you wish to publish.`,
    );
  }
}
