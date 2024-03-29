package com.netflix.spinnaker.orca.clouddriver.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Task {
  private String id;
  private Status status;
  private List<Map> resultObjects;
  private List<StatusLine> history;
  private List<Output> outputs;

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Status implements Serializable {
    private boolean completed;
    private boolean failed;
    private boolean retryable;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class StatusLine implements Serializable {
    private String phase;
    private String status;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  static class Output implements Serializable {
    private String manifest;
    private String phase;
    private String stdOut;
    private String stdError;
  }
}
