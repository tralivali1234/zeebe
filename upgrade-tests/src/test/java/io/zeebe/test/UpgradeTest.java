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

  private static final String PROCESS_ID = "process";
  private static final String TASK = "task";
  private static String lastVersion = "0.22.1";

  @Rule public Timeout timeout = new Timeout(60, TimeUnit.SECONDS);
  @Rule public TemporaryFolder temp = new TemporaryFolder();

  private ZeebeBrokerContainer container;
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
            LOG.error(container.getLogs());
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
  public void shouldCompleteJob() {
    startZeebe(lastVersion);

    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(TASK, t -> t.zeebeTaskType("test"))
            .endEvent()
            .done();
    // when
    client.newDeployCommand().addWorkflowModel(workflow, PROCESS_ID + ".bpmn").send().join();
    client.newCreateInstanceCommand().bpmnProcessId(PROCESS_ID).latestVersion().send().join();

    final ActivateJobsResponse jobsResponse =
        client.newActivateJobsCommand().jobType("test").maxJobsToActivate(1).send().join();

    TestUtil.waitUntil(() -> findElementInState(TASK, "ACTIVATED"));
    close();

    startZeebe("current-test");
    client.newCompleteCommand(jobsResponse.getJobs().get(0).getKey()).send().join();

    TestUtil.waitUntil(() -> findElementInState(PROCESS_ID, "ELEMENT_COMPLETED"));
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

  private void startZeebe(final String version) {
    network = Network.newNetwork();

    container =
        new ZeebeBrokerContainer(version)
            .withFileSystemBind(temp.getRoot().getPath(), "/usr/local/zeebe/data")
            .withNetwork(network)
            .withDebug(true)
            .withLogLevel(Level.DEBUG);
    container.start();

    client =
        ZeebeClient.newClientBuilder()
            .brokerContactPoint(container.getExternalAddress(ZeebePort.GATEWAY))
            .usePlaintext()
            .build();
  }

  private void close() {
    if (client != null) {
      client.close();
      client = null;
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
