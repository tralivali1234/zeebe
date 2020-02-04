/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.client.impl.response;

import io.zeebe.client.api.response.CreateWorkflowInstanceResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass;

public final class CreateCreateWorkflowInstanceResponseImpl implements
  CreateWorkflowInstanceResponse {

  private final long workflowKey;
  private final String bpmnProcessId;
  private final int version;
  private final long workflowInstanceKey;

  public CreateCreateWorkflowInstanceResponseImpl(
      final GatewayOuterClass.CreateWorkflowInstanceResponse response) {
    this.workflowKey = response.getWorkflowKey();
    this.bpmnProcessId = response.getBpmnProcessId();
    this.version = response.getVersion();
    this.workflowInstanceKey = response.getWorkflowInstanceKey();
  }

  @Override
  public long getWorkflowKey() {
    return workflowKey;
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  @Override
  public int getVersion() {
    return version;
  }

  @Override
  public long getWorkflowInstanceKey() {
    return workflowInstanceKey;
  }

  @Override
  public String toString() {
    return "CreateWorkflowInstanceResponseImpl{"
        + "workflowKey="
        + workflowKey
        + ", bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", version="
        + version
        + ", workflowInstanceKey="
        + workflowInstanceKey
        + '}';
  }
}
