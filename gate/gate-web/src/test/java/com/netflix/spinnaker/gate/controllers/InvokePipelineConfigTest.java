/*
 * Copyright 2023 Salesforce, Inc.
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
package com.netflix.spinnaker.gate.controllers;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.netflix.spinnaker.kork.common.Header.ACCOUNTS;
import static com.netflix.spinnaker.kork.common.Header.REQUEST_ID;
import static com.netflix.spinnaker.kork.common.Header.USER;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.matching.AnythingPattern;
import com.netflix.spinnaker.gate.Main;
import com.netflix.spinnaker.gate.health.DownstreamServicesHealthIndicator;
import com.netflix.spinnaker.gate.services.internal.ClouddriverService;
import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.web.context.WebApplicationContext;
import retrofit2.mock.Calls;

/**
 * This is a bit of an end-to-end test. It demonstrates the behavior of
 * PipelineController.invokePipelineConfig and PipelineService.trigger based on particular responses
 * (or lack thereof) from front50, clouddriver and orca. This facilitates seeing the consequences of
 * changing things like exception handlers in http clients in the code without changing the tests.
 *
 * <p>See https://github.com/spring-projects/spring-boot/issues/5574#issuecomment-506282892 for a
 * discussion of why it's difficult to assert on response bodies generated by error handlers. It's
 * not necessary in these tests, as the information we need is available elsewhere (e.g. the http
 * status line).
 */
// Without an initial delay, the ApplicationService queries for information
// before our @BeforeEach method runs and sets up the appropriate mocks.  This
// results in NullPointerExceptions.
@SpringBootTest(classes = Main.class)
@TestPropertySource(
    properties = {
      "spring.config.location=classpath:gate-test.yml",
      "services.front50.applicationRefreshInitialDelayMs=3600000"
    })
class InvokePipelineConfigTest {

  private MockMvc webAppMockMvc;

  /** See https://wiremock.org/docs/junit-jupiter/#advanced-usage---programmatic */
  @RegisterExtension
  static WireMockExtension wmFront50 =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @RegisterExtension
  static WireMockExtension wmOrca =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @Autowired private WebApplicationContext webApplicationContext;

  @Autowired ObjectMapper objectMapper;

  /**
   * This takes X-SPINNAKER-* headers from requests to gate and puts them in the MDC. This is
   * enabled when gate runs normally (by GateConfig), but needs explicit mention to function in
   * these tests.
   */
  @Autowired
  @Qualifier("authenticatedRequestFilter")
  private FilterRegistrationBean filterRegistrationBean;

  @MockBean ClouddriverServiceSelector clouddriverServiceSelector;

  @MockBean ClouddriverService clouddriverService;

  /** To prevent periodic calls to service's /health endpoints */
  @MockBean DownstreamServicesHealthIndicator downstreamServicesHealthIndicator;

  private static final String APPLICATION = "my-application";
  private static final String PIPELINE_ID = "my-pipeline-id";
  private static final String PIPELINE_NAME = "my-pipeline-name";
  private static final String USERNAME = "some user";
  private static final String ACCOUNT = "my-account";
  private static final String SUBMITTED_REQUEST_ID = "submitted-request-id";

  /**
   * Using an empty map as the trigger is mostly arbitrary. Without a user in the trigger, expect
   * the user query parameter in the request to orca to be the user we provide via the header.
   */
  private final Map<String, Object> TRIGGER = Collections.emptyMap();

  private final AnythingPattern anythingPattern = new AnythingPattern();

  @DynamicPropertySource
  static void registerUrls(DynamicPropertyRegistry registry) {
    // Configure wiremock's random ports into gate
    System.out.println("wiremock front50 url: " + wmFront50.baseUrl());
    System.out.println("wiremock orca url: " + wmOrca.baseUrl());
    registry.add("services.front50.base-url", wmFront50::baseUrl);
    registry.add("services.orca.base-url", wmOrca::baseUrl);
  }

  @BeforeEach
  void init(TestInfo testInfo) throws JsonProcessingException {
    System.out.println("--------------- Test " + testInfo.getDisplayName());

    webAppMockMvc =
        webAppContextSetup(webApplicationContext)
            .addFilters(filterRegistrationBean.getFilter())
            .build();

    // Keep the background thread that refreshes the applications cache in
    // ApplicationService happy.
    when(clouddriverServiceSelector.select()).thenReturn(clouddriverService);
    when(clouddriverService.getAllApplicationsUnrestricted(anyBoolean()))
        .thenReturn(Calls.response(Collections.emptyList()));

    // That background thread also calls a front50 endpoint, separate from the ones used in
    // invokePipelineConfig
    List<Map<String, Object>> allApplicationsFromFront50 = Collections.emptyList();
    String allApplicationsFromFront50Json =
        objectMapper.writeValueAsString(allApplicationsFromFront50);
    wmFront50.stubFor(
        WireMock.put(urlMatching("/v2/applications"))
            .willReturn(
                aResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withBody(allApplicationsFromFront50Json)));
  }

  @Test
  void invokePipelineConfigSuccess() throws Exception {
    // As a baseline, demonstrate the behavior when both front50 and orca respond with success.
    simulateFront50Success();
    simulateOrcaSuccess();

    // The response body from gate is the response from orca, which we simulate.
    webAppMockMvc
        .perform(invokePipelineConfigRequest())
        .andDo(print())
        .andExpect(status().isAccepted())
        .andExpect(header().string(REQUEST_ID.getHeader(), SUBMITTED_REQUEST_ID))
        .andExpect(content().string(orcaSuccessResponse()));

    verifyFront50PipelinesRequest();
    verifyOrcaOrchestrateRequest();
  }

  @Test
  void invokePipelineConfigNoPipelineConfigInFront50() throws Exception {

    // If front50 doesn't return a configuration for the test pipeline id, don't
    // expect a call to orca, so there's no mock to set up for orca.
    simulateFront50ResponseWithoutPipelineConfig();

    webAppMockMvc
        .perform(invokePipelineConfigRequest())
        .andDo(print())
        .andExpect(status().isNotFound())
        .andExpect(
            status()
                .reason(
                    "Unable to trigger pipeline (application: my-application, pipelineNameOrId: my-pipeline-name). Error: Pipeline configuration not found (id: "
                        + PIPELINE_NAME
                        + "): Status: 404, Method: GET, URL: "
                        + wmFront50.baseUrl()
                        + "/pipelines/my-pipeline-name/get, Message: message from front50"))
        .andExpect(header().string(REQUEST_ID.getHeader(), SUBMITTED_REQUEST_ID));

    verifyFront50PipelinesRequest();
    verifyNoOrcaOrchestrateRequest();
  }

  @Test
  void invokePipelineConfigFront50BadRequest() throws Exception {

    // There are two different code paths for generating 400 responses from
    // front50.  One is via spring itself if the request doesn't match what
    // spring expects.  Another is if application logic determines a request is
    // bad and throws an exception such that the exception handling code
    // generates a 400.
    //
    // Here, spring expects true/false for refresh so it generates an empty response.
    //
    // $ curl -s -i http://localhost:8080/pipelines/foo?refresh=bar | head -1
    // HTTP/1.1 400
    //
    // Here's one generated by front50:
    //
    // $ curl -s http://localhost:8080/pluginBinaries/foo/bar | python -mjson.tool
    // {
    //     "error": "Bad Request",
    //     "exception": "java.lang.IllegalArgumentException",
    //     "message": "Plugin binary storage service is yet not available for your persistence
    // backend",
    //     "status": 400,
    //     "timestamp": "2023-04-07T18:26:46.757+00:00"
    // }
    //
    // $ curl -s -i http://localhost:8080/pluginBinaries/foo/bar | head -1
    // HTTP/1.1 400
    Map<String, Object> front50Response =
        Map.of(
            "error",
            "Bad XX Request",
            "message",
            "message from front50",
            "status",
            400,
            "timestamp",
            "2023-04-07T18:26:46.757+00:00");
    String front50ResponseJson = objectMapper.writeValueAsString(front50Response);
    simulateFront50HttpResponse(HttpStatus.BAD_REQUEST, front50ResponseJson);

    webAppMockMvc
        .perform(invokePipelineConfigRequest())
        .andDo(print())
        .andExpect(status().isBadRequest())
        .andExpect(
            status()
                .reason(
                    "Unable to trigger pipeline (application: my-application, pipelineNameOrId: my-pipeline-name). Error: Status: 400, Method: GET, URL: "
                        + wmFront50.baseUrl()
                        + "/pipelines/my-application/name/my-pipeline-name?refresh=true, Message: message from front50"))
        .andExpect(header().string(REQUEST_ID.getHeader(), SUBMITTED_REQUEST_ID));

    verifyFront50PipelinesRequest();
    verifyNoOrcaOrchestrateRequest();
  }

  @Test
  void invokePipelineConfigFront50Error() throws Exception {
    // I haven't figured out how to make front50's get pipelines endpoint
    // respond with 500 (this is good).  I did find a way to make another
    // front50 endpoint respond with 500.  For example:
    //
    // $ curl -s http://localhost:8080/pipelines/foo/history?limit=-1 | python -mjson.tool
    // {
    //     "error": "Internal Server Error",
    //     "exception": "org.springframework.jdbc.BadSqlGrammarException",
    //     "message": "jOOQ; bad SQL grammar [select body from pipelines_history where id = ? order
    // by recorded_at desc limit ?]; nested exception is java.sql.SQLSyntaxErrorException: You have
    // an error in your SQL syntax; check the manual that corresponds to your MySQL server version
    // for the right syntax to use near '-1' at line 1",
    //     "status": 500,
    //     "timestamp": "2023-04-07T18:06:16.935+00:00"
    // }
    //
    // and
    //
    // $ curl -s -i http://localhost:8080/pipelines/foo/history?limit=-1 | head -1
    // HTTP/1.1 500
    Map<String, Object> front50Response =
        Map.of(
            "error",
            "Internal Server Error",
            "exception",
            "org.springframework.jdbc.BadSqlGrammarException",
            "message",
            "jOOQ; message from front50",
            "status",
            500,
            "timestamp",
            "2023-04-07T18:06:16.935+00:00");
    String front50ResponseJson = objectMapper.writeValueAsString(front50Response);
    simulateFront50HttpResponse(HttpStatus.INTERNAL_SERVER_ERROR, front50ResponseJson);

    webAppMockMvc
        .perform(invokePipelineConfigRequest())
        .andDo(print())
        .andExpect(status().isInternalServerError())
        .andExpect(
            status()
                .reason(
                    "Unable to trigger pipeline (application: my-application, pipelineNameOrId: my-pipeline-name). Error: Status: 500, Method: GET, URL: "
                        + wmFront50.baseUrl()
                        + "/pipelines/my-application/name/my-pipeline-name?refresh=true, Message: jOOQ; message from front50"))
        .andExpect(header().string(REQUEST_ID.getHeader(), SUBMITTED_REQUEST_ID));

    verifyFront50PipelinesRequest();
    verifyNoOrcaOrchestrateRequest();
  }

  @Test
  void invokePipelineConfigFront50NetworkError() throws Exception {
    simulateFront50Response(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER));

    webAppMockMvc
        .perform(invokePipelineConfigRequest())
        .andDo(print())
        .andExpect(status().isInternalServerError())
        .andExpect(
            status()
                .reason(
                    "Unable to trigger pipeline (application: my-application, pipelineNameOrId: my-pipeline-name). Error: java.net.SocketException: Connection reset"))
        .andExpect(header().string(REQUEST_ID.getHeader(), SUBMITTED_REQUEST_ID));

    verifyFront50PipelinesRequest();
    verifyNoOrcaOrchestrateRequest();
  }

  @Test
  void invokePipelineConfigFront50NonJsonRespone() throws Exception {
    simulateFront50HttpResponse(HttpStatus.OK, "this is not json");

    webAppMockMvc
        .perform(invokePipelineConfigRequest())
        .andDo(print())
        .andExpect(status().isInternalServerError())
        .andExpect(
            status()
                .reason(
                    "Unable to trigger pipeline (application: my-application, pipelineNameOrId: my-pipeline-name). Error: Failed to process response body: Unrecognized token 'this': was expecting (JSON String, Number, Array, Object or token 'null', 'true' or 'false')\n"
                        + " at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 1, column: 5]"))
        .andExpect(header().string(REQUEST_ID.getHeader(), SUBMITTED_REQUEST_ID));

    verifyFront50PipelinesRequest();
    verifyNoOrcaOrchestrateRequest();
  }

  @Test
  void invokePipelineConfigOrcaError() throws Exception {
    // Unless front50 succeeds, gate doesn't get far enough to call orca
    simulateFront50Success();

    // Simulate a 500 response from orca, based on this real one.
    //
    // $ curl -s -H 'Content-Type: application/json' -d "{}" http://localhost:8080/orchestrate |
    // python -mjson.tool
    // {
    //     "error": "Internal Server Error",
    //     "exception": "java.lang.NullPointerException",
    //     "message": "null",
    //     "status": 500,
    //     "timestamp": 1680866655500
    // }
    //
    // And for completeness, here are the response headers:
    //
    // $ curl -s -i -H 'Content-Type: application/json' -d "{}" http://localhost:8080/orchestrate |
    // head -1
    // HTTP/1.1 500
    Map<String, Object> orcaResponse =
        Map.of(
            "error",
            "Internal XXX Server YYY Error",
            "exception",
            "java.lang.NullPointerException",
            "message",
            "message from orca",
            "status",
            500,
            "timestamp",
            1680866655500L);
    String orcaResponseJson = objectMapper.writeValueAsString(orcaResponse);
    simulateOrcaResponse(HttpStatus.INTERNAL_SERVER_ERROR, orcaResponseJson);

    webAppMockMvc
        .perform(invokePipelineConfigRequest())
        .andDo(print())
        .andExpect(status().isInternalServerError())
        .andExpect(
            status()
                .reason(
                    "Unable to trigger pipeline (application: my-application, pipelineNameOrId: my-pipeline-name). Error: Status: 500, Method: POST, URL: "
                        + wmOrca.baseUrl()
                        + "/orchestrate?user=some%20user, Message: message from orca"))
        .andExpect(header().string(REQUEST_ID.getHeader(), SUBMITTED_REQUEST_ID));

    verifyFront50PipelinesRequest();
    verifyOrcaOrchestrateRequest();
  }

  @Test
  void invokePipelineConfigOrcaBadRequest() throws Exception {
    // Unless front50 succeeds, gate doesn't get far enough to call orca
    simulateFront50Success();

    // Simulate a 400 response from orca, similar to the front50 response above
    Map<String, Object> orcaResponse =
        Map.of(
            "error",
            "Bad XX Request",
            "message",
            "message from orca",
            "status",
            400,
            "timestamp",
            1680866655500L);
    String orcaResponseJson = objectMapper.writeValueAsString(orcaResponse);
    simulateOrcaResponse(HttpStatus.BAD_REQUEST, orcaResponseJson);

    webAppMockMvc
        .perform(invokePipelineConfigRequest())
        .andDo(print())
        .andExpect(status().isBadRequest())
        .andExpect(
            status()
                .reason(
                    "Unable to trigger pipeline (application: my-application, pipelineNameOrId: my-pipeline-name). Error: Status: 400, Method: POST, URL: "
                        + wmOrca.baseUrl()
                        + "/orchestrate?user=some%20user, Message: message from orca"))
        .andExpect(header().string(REQUEST_ID.getHeader(), SUBMITTED_REQUEST_ID));

    verifyFront50PipelinesRequest();
    verifyOrcaOrchestrateRequest();
  }

  /**
   * Simulate a successful response from front50. The actual response needs to be sufficient for
   * PipelineService.trigger to get far enough to call orca, which means we need a configuration for
   * the pipeline we're triggering.
   */
  private void simulateFront50Success() throws JsonProcessingException {
    Map<String, Object> pipelineConfig = Map.of("id", PIPELINE_ID, "name", PIPELINE_NAME);
    String pipelineConfigJson = objectMapper.writeValueAsString(pipelineConfig);
    simulateFront50HttpResponse(HttpStatus.OK, pipelineConfigJson);
  }

  /**
   * Simulate a response from front50 that doesn't contain a pipeline configuration for the test id
   */
  private void simulateFront50ResponseWithoutPipelineConfig() throws JsonProcessingException {
    // Currently front50 responds with a 404 when it doesn't contain a pipeline
    // configuration for the given application + nameOrId as well as a query by
    // pipeline id.
    Map<String, Object> notFoundResponse =
        Map.of(
            "error",
            "Not Found",
            "message",
            "message from front50",
            "status",
            404,
            "timestamp",
            "2023-04-07T17:51:22.560+00:00");
    String notFoundResponseJson = objectMapper.writeValueAsString(notFoundResponse);
    simulateFront50HttpResponse(HttpStatus.NOT_FOUND, notFoundResponseJson);

    // When the query by application + nameOrId responds with 404, gate queries
    // again by id.  Simulate a 404 response from that query as well.
    wmFront50.stubFor(
        WireMock.get(urlPathEqualTo("/pipelines/" + PIPELINE_NAME + "/get"))
            .willReturn(
                aResponse()
                    .withStatus(HttpStatus.NOT_FOUND.value())
                    .withBody(notFoundResponseJson)));
  }

  /**
   * Simulate an http response from front50
   *
   * @param httpStatus the response code
   * @param body the response body
   */
  private void simulateFront50HttpResponse(HttpStatus httpStatus, String body) {
    simulateFront50Response(aResponse().withStatus(httpStatus.value()).withBody(body));
  }

  /** Simulate a response from front50 */
  private void simulateFront50Response(ResponseDefinitionBuilder responseDefinitionBuilder) {
    // This satisfies the queries that
    // ApplicationService.getPipelineConfigForApplication makes to front50
    //
    // The first query is by application and name.  Since these tests use a
    // pipeline name as the pipelineNameOrId argument to gate's
    // invokePipelineConfig endpoint, this succeeds, and we can ignore the
    // second query by pipeline id.
    //
    // What's important is that this method produces the desired return value or
    // exception from ApplicationService.getPipelineConfigForApplication.
    wmFront50.stubFor(
        WireMock.get(urlPathEqualTo("/pipelines/" + APPLICATION + "/name/" + PIPELINE_NAME))
            .withQueryParam("refresh", anythingPattern)
            .willReturn(responseDefinitionBuilder));
  }

  /** An arbitrary successful response from orca */
  private String orcaSuccessResponse() throws JsonProcessingException {
    Map<String, Object> orcaResponse = Collections.emptyMap();
    return objectMapper.writeValueAsString(orcaResponse);
  }

  /** Simulate a successful response from orca */
  private void simulateOrcaSuccess() throws JsonProcessingException {
    simulateOrcaResponse(HttpStatus.OK, orcaSuccessResponse());
  }

  /**
   * Simulate a response from orca
   *
   * @param httpStatus the response code
   * @param body the response body
   */
  private void simulateOrcaResponse(HttpStatus httpStatus, String body) {
    wmOrca.stubFor(
        WireMock.post(urlPathEqualTo("/orchestrate"))
            .withQueryParam("user", equalTo(USERNAME))
            .withHeader(USER.getHeader(), equalTo(USERNAME))
            .willReturn(aResponse().withStatus(httpStatus.value()).withBody(body)));
  }

  /** Generate a request to the endpoint that PipelineController.invokePipelineConfig serves */
  private RequestBuilder invokePipelineConfigRequest() throws JsonProcessingException {
    return post("/pipelines/" + APPLICATION + "/" + PIPELINE_NAME)
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .characterEncoding(StandardCharsets.UTF_8.toString())
        .header(USER.getHeader(), USERNAME)
        .header(REQUEST_ID.getHeader(), SUBMITTED_REQUEST_ID)
        .header(
            ACCOUNTS.getHeader(),
            ACCOUNT) // to silence warning when X-SPINNAKER-ACCOUNTS is missing
        .content(objectMapper.writeValueAsString(TRIGGER));
  }

  /**
   * Verify that a request to front50 for pipelines happened and contained the X-SPINNAKER-* header
   * passed to gate.
   */
  private void verifyFront50PipelinesRequest() {
    wmFront50.verify(
        getRequestedFor(urlPathEqualTo("/pipelines/" + APPLICATION + "/name/" + PIPELINE_NAME))
            .withQueryParam("refresh", anythingPattern)
            .withHeader(USER.getHeader(), equalTo(USERNAME)));
  }

  /**
   * Verify that a request to orca to orchestrate a pipeline happened and contained the
   * X-SPINNAKER-* header passed to gate.
   */
  private void verifyOrcaOrchestrateRequest() {
    wmOrca.verify(
        postRequestedFor(urlPathEqualTo("/orchestrate"))
            .withQueryParam("user", equalTo(USERNAME))
            .withHeader(USER.getHeader(), equalTo(USERNAME)));
  }

  /** Verify that there was no request to orca to orchestrate a pipeline */
  private void verifyNoOrcaOrchestrateRequest() {
    wmOrca.verify(exactly(0), postRequestedFor(urlPathEqualTo("/orchestrate")));
  }
}
