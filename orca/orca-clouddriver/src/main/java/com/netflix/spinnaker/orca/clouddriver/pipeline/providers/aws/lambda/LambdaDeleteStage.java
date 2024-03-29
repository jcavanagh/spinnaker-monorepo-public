/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.lambda;

import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.LambdaCacheRefreshTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.LambdaDeleteTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.LambdaDeleteVerificationTask;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@StageDefinitionBuilder.Aliases({"Aws.LambdaDeleteStage"})
public class LambdaDeleteStage implements StageDefinitionBuilder {
  private static final Logger logger = LoggerFactory.getLogger(LambdaDeleteStage.class);

  public LambdaDeleteStage() {
    logger.debug("Constructing Aws.LambdaDeleteStage");
  }

  @Override
  public void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
    logger.debug("taskGraph for Aws.LambdaDeleteStage");
    builder.withTask("lambdaCacheRefreshTask", LambdaCacheRefreshTask.class);
    builder.withTask("lambdaDeleteTask", LambdaDeleteTask.class);
    builder.withTask("lambdaDeleteVerificationTask", LambdaDeleteVerificationTask.class);
  }
}
