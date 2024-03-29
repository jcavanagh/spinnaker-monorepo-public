import { mock } from 'angular';
import { mount } from 'enzyme';
import React from 'react';

import { StageConfigField } from '../../../..';
import { BakeHelmfileConfigForm } from './BakeHelmfileConfigForm';
import { AccountService } from '../../../../../account';
import { ApplicationModelBuilder } from '../../../../../application';
import { ExpectedArtifactService } from '../../../../../artifact';
import type { IExpectedArtifact, IStage } from '../../../../../domain';
import { SpinFormik } from '../../../../../presentation';
import { REACT_MODULE } from '../../../../../reactShims';

describe('<BakeHelmfileConfigForm />', () => {
  beforeEach(mock.module(REACT_MODULE));
  beforeEach(mock.inject());

  const helmfileFilePathFieldName = 'Helmfile File Path';

  const getProps = () => {
    return {
      application: ApplicationModelBuilder.createApplicationForTests('my-application'),
      pipeline: {
        application: 'my-application',
        id: 'pipeline-id',
        limitConcurrent: true,
        keepWaitingPipelines: true,
        name: 'My Pipeline',
        parameterConfig: [],
        stages: [],
        triggers: [],
      },
    } as any;
  };

  beforeEach(() =>
    spyOn(AccountService, 'getArtifactAccounts').and.returnValue(
      Promise.resolve([
        { name: 'gitrepo', types: ['something-else', 'git/repo'] },
        { name: 'notgitrepo', types: ['something-else'] },
      ]),
    ),
  );

  it('renders the helmfile file path element when the template artifact is from an account that handles git/repo artifacts', async () => {
    const stage = ({
      inputArtifacts: [{ account: 'gitrepo' }],
    } as unknown) as IStage;

    const props = getProps();

    const component = mount(
      <SpinFormik
        initialValues={stage}
        onSubmit={() => null}
        validate={() => null}
        render={(formik) => <BakeHelmfileConfigForm {...props} formik={formik} />}
      />,
    );

    await new Promise((resolve) => setTimeout(resolve)); // wait one js tick for promise to resolve
    component.setProps({}); // force a re-render

    expect(component.find(StageConfigField).findWhere((x) => x.text() === helmfileFilePathFieldName).length).toBe(1);
  });

  it('does not render the helmfile file path element when the template artifact is from an account that does not handle git/repo artifacts', async () => {
    const stage = ({
      inputArtifacts: [{ account: 'notgitrepo' }],
    } as unknown) as IStage;

    const props = getProps();

    const component = mount(
      <SpinFormik
        initialValues={stage}
        onSubmit={() => null}
        validate={() => null}
        render={(formik) => <BakeHelmfileConfigForm {...props} formik={formik} />}
      />,
    );

    await new Promise((resolve) => setTimeout(resolve)); // wait one js tick for promise to resolve
    component.setProps({}); // force a re-render

    expect(component.find(StageConfigField).findWhere((x) => x.text() === helmfileFilePathFieldName).length).toBe(0);
  });

  it('render the helmfile file path if the id of the git artifact is given but the account value does not exist', async () => {
    const expectedArtifactDisplayName = 'test-artifact';
    const expectedArtifactId = 'test-artifact-id';
    const expectedGitArtifact: IExpectedArtifact = {
      defaultArtifact: {
        customKind: true,
        id: 'defaultArtifact-id',
      },
      displayName: expectedArtifactDisplayName,
      id: expectedArtifactId,
      matchArtifact: {
        artifactAccount: 'gitrepo',
        id: expectedArtifactId,
        reference: 'git repo',
        type: 'git/repo',
        version: 'master',
      },
      useDefaultArtifact: false,
      usePriorArtifact: false,
    };
    const stage = ({
      inputArtifacts: [{ id: expectedArtifactId }],
    } as unknown) as IStage;

    spyOn(ExpectedArtifactService, 'getExpectedArtifactsAvailableToStage').and.returnValue([expectedGitArtifact]);

    const props = getProps();

    const component = mount(
      <SpinFormik
        initialValues={stage}
        onSubmit={() => null}
        validate={() => null}
        render={(formik) => <BakeHelmfileConfigForm {...props} formik={formik} />}
      />,
    );

    await new Promise((resolve) => setTimeout(resolve)); // wait one js tick for promise to resolve
    component.setProps({}); // force a re-render

    expect(component.find('.Select-value-label > span').text().includes(expectedArtifactDisplayName)).toBe(true);
    expect(component.find(StageConfigField).findWhere((x) => x.text() === helmfileFilePathFieldName).length).toBe(1);
  });
});
