// Copyright (c) 2021, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package com.oracle.wls.exporter;

import java.io.IOException;

import com.oracle.wls.exporter.domain.ConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static com.oracle.wls.exporter.InvocationContextStub.HOST;
import static com.oracle.wls.exporter.InvocationContextStub.PORT;
import static com.oracle.wls.exporter.InvocationContextStub.REST_PORT;
import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ConfigurationCallTest {

  private static final String CONFIGURATION =
        "host: " + HOST + "\n" +
              "port: " + PORT + "\n" +
              "queries:\n" + "" +
              "- groups:\n" +
              "    prefix: new_\n" +
              "    key: name\n" +
              "    values: [sample1, sample2]\n";

  private static final String CONFIGURATION_WITH_REST_PORT =
        "host: " + HOST + "\n" +
              "port: " + PORT + "\n" +
              "restPort: " + REST_PORT + "\n" +
              "queries:\n" + "" +
              "- groups:\n" +
              "    prefix: new_\n" +
              "    key: name\n" +
              "    values: [sample1, sample2]\n";

  private static final String ADDED_CONFIGURATION =
        "host: localhost\n" +
              "port: 7001\n" +
              "queries:\n" + "" +
              "- people:\n" +
              "    key: name\n" +
              "    values: [age, sex]\n";

  private static final String COMBINED_CONFIGURATION =
        "host: " + HOST + "\n" +
              "port: " + PORT + "\n" +
              "queries:\n" + "" +
              "- groups:\n" +
              "    prefix: new_\n" +
              "    key: name\n" +
              "    values: [sample1, sample2]\n" +
              "- people:\n" +
              "    key: name\n" +
              "    values: [age, sex]\n";
  protected static final String BAD_BOOLEAN_STRING = "blabla";

  private final WebClientFactoryStub factory = new WebClientFactoryStub();
  private final InvocationContextStub context = InvocationContextStub.create();

  @BeforeEach
  public void setUp() {
    LiveConfiguration.setServer(HOST, PORT);
    LiveConfiguration.loadFromString(CONFIGURATION);
  }

  @Test
  public void whenNoConfigurationSpecified_reportFailure() {
    assertThrows(RuntimeException.class, () -> handleConfigurationCall(context));
  }

  private void handleConfigurationCall(InvocationContextStub context) throws IOException {
    final ConfigurationCall call = new ConfigurationCall(factory, context);

    call.doWithAuthentication();
  }

  @Test
  public void whenRequestUsesHttp_authenticateWithHttp() throws Exception {
    handleConfigurationCall(context.withConfiguration("replace", CONFIGURATION));

    assertThat(factory.getClientUrl(), startsWith("http:"));
  }

  @Test
  public void whenRequestUsesHttps_authenticateWithHttps() throws Exception {
    handleConfigurationCall(context.withHttps().withConfiguration("replace", CONFIGURATION));

    assertThat(factory.getClientUrl(), startsWith("https:"));
  }

  @Test
  public void afterUploadWithReplace_useNewConfiguration() throws Exception {
    handleConfigurationCall(context.withConfiguration("replace", CONFIGURATION));

    assertThat(LiveConfiguration.asString(), equalTo(CONFIGURATION));
  }

  @Test
  public void afterUpload_redirectToMainPage() throws Exception {
    handleConfigurationCall(context.withConfiguration("replace", CONFIGURATION));

    assertThat(context.getRedirectLocation(), equalTo(WebAppConstants.MAIN_PAGE));
  }

  @Test
  public void whenRestPortInaccessible_switchToSpecifiedPort() throws Exception {
    LiveConfiguration.loadFromString(CONFIGURATION_WITH_REST_PORT);
    factory.throwConnectionFailure("localhost", REST_PORT);

    handleConfigurationCall(context.withConfiguration("replace", CONFIGURATION));

    assertThat(factory.getClientUrl(), containsString(Integer.toString(PORT)));
  }

  @Test
  public void afterUploadWithAppend_useCombinedConfiguration() throws Exception {
    handleConfigurationCall(context.withConfiguration("append", ADDED_CONFIGURATION));

    assertThat(LiveConfiguration.asString(), equalTo(COMBINED_CONFIGURATION));
  }

  @Test
  public void whenSelectedFileIsNotYaml_reportError() throws Exception {
    handleConfigurationCall(context.withConfiguration("replace", NON_YAML));

    assertThat(context.getResponse(), containsString(ConfigurationException.NOT_YAML_FORMAT));
  }

  private static final String NON_YAML =
        "this is not yaml\n";

  @Test
  public void whenSelectedFileHasPartialYaml_reportError() throws Exception {
    handleConfigurationCall(context.withConfiguration("replace", PARTIAL_YAML));

    assertThat(context.getResponse(), containsString(ConfigurationException.BAD_YAML_FORMAT));
  }

  private static final String PARTIAL_YAML =
        "queries:\nkey name\n";

  @Test
  public void whenSelectedFileHasBadBooleanValue_reportError() throws Exception {
    handleConfigurationCall(context.withConfiguration("append", ADDED_CONFIGURATION_WITH_BAD_BOOLEAN));

    assertThat(context.getResponse(), containsString(BAD_BOOLEAN_STRING));
  }

  private static final String ADDED_CONFIGURATION_WITH_BAD_BOOLEAN =
        "host: localhost\n" +
              "port: 7001\n" +
              "metricsNameSnakeCase: " + BAD_BOOLEAN_STRING + "\n" +
              "queries:\n" + "" +
              "- people:\n" +
              "    key: name\n" +
              "    values: [age, sex]\n";

  @Test
  public void afterSelectedFileHasBadBooleanValue_configurationIsUnchanged() throws Exception {
    handleConfigurationCall(context.withConfiguration("append", ADDED_CONFIGURATION_WITH_BAD_BOOLEAN));

    assertThat(LiveConfiguration.asString(), equalTo(CONFIGURATION));
  }

  @Test
  public void whenServerSends403StatusOnGet_returnToClient() throws Exception {
    factory.reportNotAuthorized();

    handleConfigurationCall(context.withConfiguration("replace", CONFIGURATION));

    assertThat(context.getResponseStatus(), equalTo(HTTP_FORBIDDEN));
  }

  @Test
  public void whenServerSends401StatusOnGet_returnToClient() throws Exception {
    factory.reportAuthenticationRequired("Test-Realm");

    handleConfigurationCall(context.withConfiguration("replace", CONFIGURATION));

    assertThat(context.getResponseStatus(), equalTo(HTTP_UNAUTHORIZED));
    assertThat(context.getResponseHeader("WWW-Authenticate"), equalTo("Basic realm=\"Test-Realm\""));
  }
}