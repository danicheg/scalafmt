package org.scalafmt.cli

import java.nio.file.{Files, NoSuchFileException, Path, Paths}

import metaconfig.Configured.NotOk
import metaconfig.Configured.Ok
import org.scalafmt.config.{Config, ScalafmtConfig}
import FileTestOps._
import org.scalafmt.Versions

import metaconfig.Configured
import munit.FunSuite

class CliOptionsTest extends FunSuite {

  test("preset = ...") {
    import org.scalafmt.config.Config
    val NotOk(err) = Config.fromHoconString("preset = foobar")
    assert(
      "Unknown style \"foobar\". Expected one of: " +
        "Scala.js, IntelliJ, default, defaultWithAlign" == err.msg
    )

    val overrideOne = Config.fromHoconString("""|preset = defaultWithAlign
      |maxColumn = 100
      |""".stripMargin)
    assert(
      Ok(ScalafmtConfig.defaultWithAlign.copy(maxColumn = 100)) == overrideOne
    )
    assert(
      Ok(ScalafmtConfig.intellij) == Config.fromHoconString("preset = intellij")
    )
    assert(
      Ok(ScalafmtConfig.scalaJs) == Config.fromHoconString("preset = Scala.js")
    )
    assert(
      Ok(ScalafmtConfig.defaultWithAlign) == Config
        .fromHoconString("preset = defaultWithAlign")
    )
  }

  test(
    ".configPath returns a path to the temporary file that contains configuration specified by --config-str"
  ) {
    val expected = "foo bar"
    val path: Path = baseCliOptions
      .copy(
        configStr = Some(
          s"""{version="${Versions.version}", onTestFailure="$expected"}"""
        )
      )
      .configPath
    val config = Config.fromHoconFile(path).get
    assert(config.onTestFailure == expected)
  }

  test(".configPath returns path to specified configuration path") {
    val tempPath = Files.createTempFile(".scalafmt", ".conf")
    val opt = baseCliOptions.copy(config = Some(tempPath))
    assert(tempPath == opt.configPath)
  }

  test(
    ".configPath returns path to workingDirectory's .scalafmt.conf by default, if exists"
  ) {
    assertEquals(baseCliOptions.config, None)
    assertEquals(baseCliOptions.configStr, None)
    intercept[NoSuchFileException](baseCliOptions.configPath)
  }

  test(
    ".scalafmtConfig returns the configuration encoded from configStr if configStr is exists"
  ) {
    val expected = "foo bar"
    val opt = baseCliOptions.copy(
      configStr =
        Some(s"""{version="${Versions.version}", onTestFailure="$expected"}""")
    )
    assert(opt.scalafmtConfig.get.onTestFailure == expected)
  }

  test(
    ".scalafmtConfig returns the configuration read from configuration file located on configPath"
  ) {
    val expected = "foo bar"
    val configPath = Files.createTempFile(".scalafmt", ".conf")
    val config = s"""
      |version="${Versions.version}"
      |maxColumn=100
      |onTestFailure="$expected"
      |""".stripMargin
    Files.write(configPath, config.getBytes)

    val opt = baseCliOptions.copy(config = Some(configPath))
    assert(opt.scalafmtConfig.get.onTestFailure == expected)
  }

  test(
    ".scalafmtConfig returns default ScalafmtConfig if configuration file is missing"
  ) {
    val configDir = Files.createTempDirectory("temp-dir")
    val configPath = Paths.get(configDir.toString + "/.scalafmt.conf")
    val opt = baseCliOptions.copy(config = Some(configPath))
    assert(opt.scalafmtConfig.isInstanceOf[Configured.NotOk])
    val confError = opt.scalafmtConfig.asInstanceOf[Configured.NotOk].error
    assert(confError.cause.exists(_.isInstanceOf[NoSuchFileException]))
  }

  test(".scalafmtConfig returns Configured.NotOk for invalid configuration") {
    val expected = "foo bar"
    val opt = baseCliOptions.copy(
      configStr = Some(
        s"""{invalidKey="${Versions.version}", onTestFailure="$expected"}"""
      )
    )
    assert(opt.scalafmtConfig.isNotOk)
  }

  test("don't write info when writing to stdout") {
    val stdinArgs = Array("--stdin")
    val stdoutArgs = Array("--stdout")
    for (args <- Seq(stdinArgs, stdoutArgs)) {
      val options = Cli.getConfig(args, baseCliOptions).get
      assert(options.common.info == NoopOutputStream.printStream)
    }
  }
}
