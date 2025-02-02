// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package com.oracle.wls.buildhelper;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import com.meterware.simplestub.SystemPropertySupport;
import org.apache.maven.plugin.AbstractMojo;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStub;
import static org.apache.maven.plugins.annotations.LifecyclePhase.PROCESS_RESOURCES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

class BuildHelperMojoTest {

  private final BuildHelperMojo mojo = new BuildHelperMojo();
  private final List<Memento> mementos = new ArrayList<>();
  private final CopyExecutorStub copyExecutorStub = new CopyExecutorStub();
  private MojoTestSupport mojoTestSupport;

  @BeforeEach
  public void setUp() throws Exception {
    mojoTestSupport = new MojoTestSupport(BuildHelperMojo.class);
    mementos.add(StaticStubSupport.install(BuildHelperMojo.class, "executor", copyExecutorStub));
    mementos.add(SystemPropertySupport.preserve("user.dir"));
  }

  @AfterEach
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  void helperImplementsMojo() {
    assertThat(mojo, Matchers.instanceOf(AbstractMojo.class));
  }

  @Test
  void mojoHasGoalAnnotation() {
    assertThat(mojoTestSupport.getClassAnnotation(), notNullValue());
  }

  @Test
  void mojoAnnotatedWithName() {
    assertThat(mojoTestSupport.getClassAnnotation().get("name"), equalTo("copy"));
  }

  @Test
  void mojoAnnotatedWithDefaultPhase() {
    assertThat(mojoTestSupport.getClassAnnotation().get("defaultPhase"), equalTo(PROCESS_RESOURCES));
  }

  @Test
  void hasRequiredSourceFileParameter() throws NoSuchFieldException {
    assertThat(mojoTestSupport.getParameterField("sourceFile").getType(), equalTo(String.class));
    assertThat(mojoTestSupport.getParameterAnnotation("sourceFile").get("required"), is(true));
  }

  @Test
  void hasAnnotatedTargetFileField_withNoDefault() throws NoSuchFieldException {
    assertThat(mojoTestSupport.getParameterField("targetFile").getType(), equalTo(File.class));
    assertThat(mojoTestSupport.getParameterAnnotation("targetFile").get("defaultValue"), nullValue());
  }

  @Test
  void targetFileField_isRequired() throws NoSuchFieldException {
    assertThat(mojoTestSupport.getParameterAnnotation("targetFile").get("required"), is(true));
  }

  @Test
  void hasAnnotatedUserDirFileField_withNullDefault() throws NoSuchFieldException {
    assertThat(mojoTestSupport.getParameterField("userDir").getType(), equalTo(File.class));
    assertThat(mojoTestSupport.getParameterAnnotation("userDir").get("defaultValue"), nullValue());
  }

  @Test
  void userDirField_isNotRequired() throws NoSuchFieldException {
    assertThat(mojoTestSupport.getParameterAnnotation("userDir").get("required"), nullValue());
  }

  @Test
  void whenSourceAndTargetAbsolute_useAbsolutePaths() throws Exception {
    setMojoParameter("sourceFile", "/root/source");
    setMojoParameter("targetFile", new File("/root/target"));

    mojo.execute();

    assertThat(copyExecutorStub.sourcePath, equalTo("/root/source"));
    assertThat(copyExecutorStub.targetPath, equalTo("/root/target"));
  }

  @Test
  void whenSourcePathIsRelative_computeAbsoluteRelativeToUserDir() throws Exception {
    setMojoParameter("sourceFile", "source");
    setMojoParameter("targetFile", new File("/root/target"));
    setMojoParameter("userDir", new File("/root/nested"));

    mojo.execute();

    assertThat(copyExecutorStub.sourcePath, equalTo("/root/nested/source"));
    assertThat(copyExecutorStub.targetPath, equalTo("/root/target"));
  }

  @Test
  void whenSourcePathIsRelativeAndNoUserDir_useSystemProperty() throws Exception {
    System.setProperty("user.dir", "/user");
    setMojoParameter("sourceFile", "source");
    setMojoParameter("targetFile", new File("/root/target"));

    mojo.execute();

    assertThat(copyExecutorStub.sourcePath, equalTo("/user/source"));
    assertThat(copyExecutorStub.targetPath, equalTo("/root/target"));
  }

  private void setMojoParameter(String fieldName, Object value) throws Exception {
    Field field = mojo.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(mojo, value);
  }

  static class CopyExecutorStub implements CopyExecutor {

    private String sourcePath;
    private String targetPath;

    @Override
    public Path toPath(File file) {
      return createStub(PathStub.class, file);
    }

    @Override
    public void copyFile(Path sourcePath, Path targetPath) {
      this.sourcePath = getPathString(sourcePath);
      this.targetPath = getPathString(targetPath);
    }

    protected String getPathString(Path path) {
      return ((PathStub) path).path;
    }
  }

  static abstract class PathStub implements Path {
    private final String path;

    PathStub(File file) {
      path = file.getPath();
    }

  }

}
