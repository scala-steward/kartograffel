import sbtcrossproject.{crossProject, CrossProject}

/// variables

val projectName = "kartograffel"
val rootPkg = "kartograffel"

val circeVersion = "0.8.0"
val doobieVersion = "0.4.4"
val flywayVersion = "4.2.0"
val h2Version = "1.4.196"
val http4sVersion = "0.17.0-M3"
val logbackVersion = "1.2.3"
val refinedVersion = "0.8.2"
val scalacheckShapelessVersion = "1.1.6"
val scalajsDomVersion = "0.9.3"
val scalajsJqueryVersion = "0.9.2"
val scalajsScalaTagsVersion = "0.6.5"
val scalaTestVersion = "3.0.3"
val specs2Version = "3.8.6"
val webjarJqueryVersion = "2.1.4"

/// projects

lazy val root = project
  .in(file("."))
  .aggregate(clientJS)
  .aggregate(serverJVM)
  .aggregate(sharedJS)
  .aggregate(sharedJVM)
  .settings(commonSettings)
  .settings(noPublishSettings)

lazy val client = crossProject(JSPlatform)
  .configureCross(moduleCrossConfig("client"))
  .jsConfigure(_.dependsOn(sharedJS))
  .enablePlugins(ScalaJSWeb)
  .settings(
    libraryDependencies ++= Seq(
      "be.doeraene" %%% "scalajs-jquery" % scalajsJqueryVersion,
      "org.scala-js" %%% "scalajs-dom" % scalajsDomVersion,
      "com.lihaoyi" %%% "scalatags" % scalajsScalaTagsVersion,
      // Replace with specs2 when it supports Scala.js:
      // https://github.com/etorreborre/specs2/issues/465
      "org.scalatest" %%% "scalatest" % scalaTestVersion % Test
    ),
    jsDependencies ++= Seq(
      "org.webjars" % "jquery" % webjarJqueryVersion / s"$webjarJqueryVersion/jquery.js"
    ),
    scalaJSUseMainModuleInitializer := true
  )

lazy val clientJS = client.js

lazy val server = crossProject(JVMPlatform)
  .configureCross(moduleCrossConfig("server"))
  .jvmConfigure(_.dependsOn(sharedJVM))
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(DebianPlugin, JavaServerAppPackaging, SystemVPlugin)
  .enablePlugins(SbtWeb)
  .settings(
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % logbackVersion,
      "com.h2database" % "h2" % h2Version,
      "eu.timepit" %% "refined" % refinedVersion,
      "eu.timepit" %% "refined-pureconfig" % refinedVersion,
      "org.flywaydb" % "flyway-core" % flywayVersion,
      "org.http4s" %% "http4s-blaze-server" % http4sVersion,
      "org.http4s" %% "http4s-circe" % http4sVersion,
      "org.http4s" %% "http4s-core" % http4sVersion,
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "org.tpolecat" %% "doobie-core-cats" % doobieVersion,
      "org.tpolecat" %% "doobie-hikari-cats" % doobieVersion,
      "org.tpolecat" %% "doobie-refined-cats" % doobieVersion,
      "org.http4s" %% "http4s-testing" % http4sVersion % Test,
      "org.specs2" %% "specs2-core" % specs2Version % Test
    )
  )
  // command line options for run and reStart
  .settings(
    configDirectory := sourceDirectory.in(Universal).value / "conf",
    configFile := configDirectory.value / "application.conf",
    fork.in(run) := true,
    javaOptions ++= {
      Seq(
        s"-Dconfig.file=${configFile.value}",
        s"-Dlogback.configurationFile=${configDirectory.value}/logback.xml"
      )
    }
  )
  // sbt-buildinfo settings
  .settings(
    buildInfoKeys := {
      val assetsRoot = "assetsRoot" -> "assets"
      val assetsPath = "assetsPath" -> s"${assetsRoot._2}/${moduleName.value}/${version.value}"
      Seq[BuildInfoKey](
        name,
        version,
        moduleName,
        modulePkg,
        assetsRoot,
        assetsPath,
        BuildInfoKey.map(isDevMode.in(scalaJSPipeline)) {
          case (_, value) =>
            "jsOptPostfix" -> (if (value) "fastopt" else "opt")
        }
      )
    },
    buildInfoPackage := modulePkg.value
  )
  // sbt-heroku settings
  .settings(
    herokuAppName in Compile := name.value
  )
  // sbt-native-packager settings
  .settings(
    bashScriptExtraDefines ++= Seq(
      """addJava "-Dconfig.file=${app_home}/../conf/application.conf"""",
      """addJava "-Dlogback.configurationFile=${app_home}/../conf/logback.xml""""
    )
  )
  // sbt-web-scalajs settings
  .settings(
    scalaJSProjects := Seq(clientJS),
    pipelineStages.in(Assets) := Seq(scalaJSPipeline)
  )

lazy val serverJVM = server.jvm

lazy val shared = crossProject(JSPlatform, JVMPlatform)
  .configureCross(moduleCrossConfig("shared"))
  .settings(
    libraryDependencies ++= Seq(
      "eu.timepit" %%% "refined" % refinedVersion,
      "io.circe" %%% "circe-generic" % circeVersion,
      "io.circe" %%% "circe-refined" % circeVersion,
      "com.github.alexarchambault" %%% "scalacheck-shapeless_1.13" % scalacheckShapelessVersion % Test,
      "eu.timepit" %%% "refined-scalacheck" % refinedVersion % Test,
      "io.circe" %%% "circe-testing" % circeVersion % Test,
      "org.scalatest" %%% "scalatest" % scalaTestVersion % Test
    )
  )

lazy val sharedJS = shared.js
lazy val sharedJVM = shared.jvm

/// settings

def moduleCrossConfig(name: String): CrossProject => CrossProject =
  _.in(file(s"modules/$name"))
    .settings(
      moduleName := name,
      modulePkg := s"$rootPkg.$name"
    )
    .settings(commonSettings)

lazy val commonSettings = Def.settings(
  compileSettings,
  consoleSettings,
  metadataSettings,
  packageSettings
)

lazy val compileSettings = Def.settings(
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-language:existentials",
    "-language:experimental.macros",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-unchecked",
    "-Xfatal-warnings",
    "-Xfuture",
    "-Xlint:-unused,_",
    "-Yno-adapted-args",
    "-Ywarn-unused:implicits",
    "-Ywarn-unused:imports",
    "-Ywarn-unused:locals",
    "-Ywarn-unused:params",
    "-Ywarn-unused:patvars",
    "-Ywarn-unused:privates",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard"
  )
)

lazy val consoleSettings = Def.settings(
  scalacOptions in (Compile, console) -= "-Ywarn-unused:imports",
  scalacOptions in (Test, console) := (scalacOptions in (Compile, console)).value,
  initialCommands += s"""
    import eu.timepit.refined.auto._
    import $rootPkg.shared._
    import $rootPkg.shared.model._
    import ${modulePkg.value}._
  """
)

lazy val metadataSettings = Def.settings(
  name := projectName,
  licenses := Seq(
    "Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")),
  startYear := Some(2017)
)

lazy val packageSettings = Def.settings(
  maintainer := s"$projectName author(s) <$projectName@example.org>"
)

lazy val noPublishSettings = Def.settings(
  publish := (),
  publishLocal := (),
  publishArtifact := false
)

/// taskKeys and settingKeys

lazy val h2Console = taskKey[Unit]("Runs the H2 console.")
h2Console := {
  val cpFiles = managedClasspath.in(Compile).in(serverJVM).value.files
  val h2jar = cpFiles.find(_.toString.contains(h2Version))

  h2jar.fold {
    sys.error(s"Could not find H2 JAR for version $h2Version")
  } { h2File =>
    import com.typesafe.config.ConfigFactory
    val cfg = ConfigFactory.parseFile(configFile.in(serverJVM).value)

    val command = Seq(
      "java",
      "-jar",
      h2File.toString,
      "-url",
      cfg.getString("db.url"),
      "-driver",
      cfg.getString("db.driver"),
      "-user",
      cfg.getString("db.user"),
      "-password",
      cfg.getString("db.password")
    )
    streams.value.log.info(s"Running ${command.mkString(" ")}")
    Process(command).run()
  }
}

lazy val configDirectory = settingKey[File]("")

lazy val configFile = settingKey[File]("")

lazy val modulePkg = settingKey[String]("")
modulePkg := rootPkg

/// commands

def addCommandsAlias(name: String, cmds: Seq[String]) =
  addCommandAlias(name, cmds.mkString(";", ";", ""))

addCommandsAlias("validate",
                 Seq(
                   "clean",
                   "scalafmtTest",
                   "coverageOn",
                   "test",
                   "coverageReport",
                   "coverageOff",
                   "doc",
                   "package",
                   "packageSrc",
                   "debian:packageBin"
                 ))

addCommandsAlias("deployHerokuCmds",
                 Seq(
                   "clean",
                   "serverJVM/stage",
                   "serverJVM/deployHeroku"
                 ))
