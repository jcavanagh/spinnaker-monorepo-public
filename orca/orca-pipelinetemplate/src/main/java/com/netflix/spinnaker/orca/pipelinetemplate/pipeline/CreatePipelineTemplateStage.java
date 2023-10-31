/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.pipelinetemplate.pipeline;

import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode.Builder;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.pipelinetemplate.tasks.CreatePipelineTemplateTask;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;

@Component
public class CreatePipelineTemplateStage implements StageDefinitionBuilder {

  @Override
  public void taskGraph(@Nonnull StageExecution stage, @Nonnull Builder builder) {
    builder.withTask("createPipelineTemplate", CreatePipelineTemplateTask.class);
  }
}
