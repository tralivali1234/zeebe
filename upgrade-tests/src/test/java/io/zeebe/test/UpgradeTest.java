/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.response.ActivateJobsResponse;
import io.zeebe.containers.ZeebePort;
import io.zeebe.containers.broker.ZeebeBrokerContainer;
import io.zeebe.containers.gateway.ZeebeGatewayContainer;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.test.util.TestUtil;
import java.util.concurrent.TimeUnit;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestWatcher;
import org.junit.rules.Timeout;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.testcontainers.containers.Network;

public class UpgradeTest {

  public static final Logger LOG = LoggerFactory.getLogger(UpgradeTest.class);
  private static final String CURRENT_VERSION = "current-test";
  private static final String PROCESS_ID = "process";
  private static final String TASK = "task";

  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask(TASK, t -> t.zeebeTaskType(TASK))
          .endEvent()
          .done();

  private static String lastVersion = "0.22.1";
  @Rule public Timeout timeout = new Timeout(60, TimeUnit.SECONDS);
  @Rule public TemporaryFolder temp = new TemporaryFolder();

  private ZeebeBrokerContainer container;
  private ZeebeGatewayContainer gateway;
  private ZeebeClient client;
  private Network network;

  @Rule(order = Integer.MIN_VALUE)
  public TestWatcher watchman =
      new TestWatcher() {
        @Override
        protected void succeeded(Description description) {
          close();
        }

        @Override
        protected void failed(Throwable e, Description description) {
          if (container != null) {
            LOG.error(
                "===============================================\n"
                    + "Broker logs\n"
                    + "==============================================="
                    + container.getLogs());
          }
          if (gateway != null) {
            LOG.error(
                "===============================================\n"
                    + "Gateway logs\n"
                    + "==============================================="
                    + gateway.getLogs());
          }

          close();
        }
      };

  @BeforeClass
  public static void beforeClass() {
    final String version = System.getProperty("lastVersion");
    if (version != null) {
      lastVersion = version;
    } else {
      LOG.info(
          "Expected last version property to be set but none was found. Running test with default version {}",
          lastVersion);
    }
  }

  @Test
  public void shouldCompleteJobAfterUpgrade() {
    // given
    startZeebe(lastVersion);

    // when
    client.newDeployCommand().addWorkflowModel(WORKFLOW, PROCESS_ID + ".bpmn").send().join();
    client.newCreateInstanceCommand().bpmnProcessId(PROCESS_ID).latestVersion().send().join();

    final ActivateJobsResponse jobsResponse =
        client.newActivateJobsCommand().jobType(TASK).maxJobsToActivate(1).send().join();

    TestUtil.waitUntil(() -> findElementInState(TASK, "ACTIVATED"));
    close();

    startZeebe(CURRENT_VERSION);
    client.newCompleteCommand(jobsResponse.getJobs().get(0).getKey()).send().join();

    TestUtil.waitUntil(() -> findElementInState(PROCESS_ID, "ELEMENT_COMPLETED"));
  }

  @Test
  public void shouldSupportOlderVersionedGateway() {
    // given
    startZeebe(false, CURRENT_VERSION, lastVersion);

    // when
    client.newDeployCommand().addWorkflowModel(WORKFLOW, PROCESS_ID + ".bpmn").send().join();
    client.newCreateInstanceCommand().bpmnProcessId(PROCESS_ID).latestVersion().send().join();

    final ActivateJobsResponse jobsResponse =
        client.newActivateJobsCommand().jobType(TASK).maxJobsToActivate(1).send().join();

    client.newCompleteCommand(jobsResponse.getJobs().get(0).getKey()).send().join();

    // then
    TestUtil.waitUntil(() -> findElementInState(PROCESS_ID, "ELEMENT_COMPLETED"));
  }

  private void startZeebe(final String version) {
    startZeebe(true, version, null);
  }

  private void startZeebe(
      final boolean embeddedGateway, final String brokerVersion, final String gatewayVersion) {
    network = Network.newNetwork();

    container =
        new ZeebeBrokerContainer(brokerVersion)
            .withFileSystemBind(temp.getRoot().getPath(), "/usr/local/zeebe/data")
            .withNetwork(network)
            .withEmbeddedGateway(embeddedGateway)
            .withDebug(true)
            .withLogLevel(Level.DEBUG);
    container.start();

    String contactPoint = container.getExternalAddress(ZeebePort.GATEWAY);

    if (!embeddedGateway) {
      gateway =
          new ZeebeGatewayContainer(gatewayVersion)
              .withContactPoint(container.getContactPoint())
              .withNetwork(network)
              .withLogLevel(Level.DEBUG);
      gateway.start();
      contactPoint = gateway.getExternalAddress(ZeebePort.GATEWAY);
    }

    client = ZeebeClient.newClientBuilder().brokerContactPoint(contactPoint).usePlaintext().build();
  }

  private boolean findElementInState(final String element, final String intent) {
    final String[] lines = container.getLogs().split("\n");

    for (int i = lines.length - 1; i >= 0; --i) {
      if (lines[i].contains(String.format("\"elementId\":\"%s\"", element))
          && lines[i].contains(String.format("\"intent\":\"%s\"", intent))) {
        return true;
      }
    }

    return false;
  }

  private void close() {
    if (client != null) {
      client.close();
      client = null;
    }

    if (gateway != null) {
      gateway.close();
      gateway = null;
    }

    if (container != null) {
      container.close();
      container = null;
    }

    if (network != null) {
      network.close();
      network = null;
    }
  }
}
