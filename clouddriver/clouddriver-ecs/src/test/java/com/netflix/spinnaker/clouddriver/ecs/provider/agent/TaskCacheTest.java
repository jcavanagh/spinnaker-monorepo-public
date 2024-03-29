/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASKS;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import com.amazonaws.services.ecs.model.DescribeTasksRequest;
import com.amazonaws.services.ecs.model.DescribeTasksResult;
import com.amazonaws.services.ecs.model.ListClustersRequest;
import com.amazonaws.services.ecs.model.ListClustersResult;
import com.amazonaws.services.ecs.model.ListTasksRequest;
import com.amazonaws.services.ecs.model.ListTasksResult;
import com.amazonaws.services.ecs.model.Task;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.TaskCacheClient;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import org.junit.jupiter.api.Test;
import spock.lang.Subject;

public class TaskCacheTest extends CommonCachingAgent {
  private final ObjectMapper mapper = new ObjectMapper();

  @Subject
  private final TaskCachingAgent agent =
      new TaskCachingAgent(
          netflixAmazonCredentials, REGION, clientProvider, credentialsProvider, registry);

  @Subject private final TaskCacheClient client = new TaskCacheClient(providerCache, mapper);

  @Test
  public void shouldRetrieveFromWrittenCache() {
    // Given
    String key = Keys.getTaskKey(ACCOUNT, REGION, TASK_ID_1);

    Task task = new Task();
    task.setTaskArn(TASK_ARN_1);
    task.setClusterArn(CLUSTER_ARN_1);
    task.setContainerInstanceArn(CONTAINER_INSTANCE_ARN_1);
    task.setGroup("group" + SERVICE_NAME_1);
    task.setContainers(Collections.emptyList());
    task.setLastStatus(STATUS);
    task.setDesiredStatus(STATUS);
    task.setStartedAt(new Date());
    task.setAvailabilityZone(REGION + "a");

    when(ecs.listClusters(any(ListClustersRequest.class)))
        .thenReturn(new ListClustersResult().withClusterArns(CLUSTER_ARN_1));
    when(ecs.listTasks(any(ListTasksRequest.class)))
        .thenReturn(new ListTasksResult().withTaskArns(TASK_ARN_1));
    when(ecs.describeTasks(any(DescribeTasksRequest.class)))
        .thenReturn(new DescribeTasksResult().withTasks(task));

    // When
    CacheResult cacheResult = agent.loadData(providerCache);
    when(providerCache.get(TASKS.toString(), key))
        .thenReturn(cacheResult.getCacheResults().get(TASKS.toString()).iterator().next());

    // Then
    Collection<CacheData> cacheData =
        cacheResult.getCacheResults().get(Keys.Namespace.TASKS.toString());
    com.netflix.spinnaker.clouddriver.ecs.cache.model.Task ecsTask = client.get(key);

    assertTrue(cacheData != null, "Expected CacheData to be returned but null is returned");
    assertTrue(cacheData.size() == 1, "Expected 1 CacheData but returned " + cacheData.size());
    String retrievedKey = cacheData.iterator().next().getId();
    assertTrue(
        cacheData.size() == 1,
        "Expected CacheData with ID " + key + " but retrieved ID " + retrievedKey);

    assertTrue(
        CLUSTER_ARN_1.equals(ecsTask.getClusterArn()),
        "Expected the cluster ARN to be " + CLUSTER_ARN_1 + " but got " + ecsTask.getClusterArn());

    assertTrue(
        TASK_ARN_1.equals(ecsTask.getTaskArn()),
        "Expected the task ARN to be " + TASK_ARN_1 + " but got " + ecsTask.getTaskArn());

    assertTrue(
        task.getContainerInstanceArn().equals(ecsTask.getContainerInstanceArn()),
        "Expected the container instance ARN name to be "
            + task.getContainerInstanceArn()
            + " but got "
            + ecsTask.getContainerInstanceArn());

    assertTrue(
        task.getGroup().equals(ecsTask.getGroup()),
        "Expected the group to be " + task.getGroup() + " but got " + ecsTask.getGroup());

    assertTrue(
        task.getLastStatus().equals(ecsTask.getLastStatus()),
        "Expected the last status to be "
            + task.getLastStatus()
            + " but got "
            + ecsTask.getLastStatus());

    assertTrue(
        task.getDesiredStatus().equals(ecsTask.getDesiredStatus()),
        "Expected the desired status to be "
            + task.getDesiredStatus()
            + " but got "
            + ecsTask.getDesiredStatus());

    assertTrue(
        task.getStartedAt().getTime() == ecsTask.getStartedAt(),
        "Expected the started at to be "
            + task.getStartedAt().getTime()
            + " but got "
            + ecsTask.getStartedAt());

    assertTrue(
        task.getAvailabilityZone() == ecsTask.getAvailabilityZone(),
        "Expected the availability zone to be "
            + task.getAvailabilityZone()
            + " but got "
            + ecsTask.getAvailabilityZone());

    assertTrue(
        task.getContainers().size() == 0,
        "Expected the task to have 0 containers but got " + task.getContainers().size());
  }
}
