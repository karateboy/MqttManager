name := """MqttManager"""

version := "2.8.0"

lazy val root = (project in file(".")).enablePlugins(PlayScala, LauncherJarPlugin)

scalaVersion := "2.12.10"

libraryDependencies ++= Seq(
  ws,
  filters,
  guice,
  "net.sf.marineapi" % "marineapi" % "0.10.0"
)

// https://mvnrepository.com/artifact/com.github.tototoshi/scala-csv
libraryDependencies += "com.github.tototoshi" %% "scala-csv" % "2.0.0"

// https://mvnrepository.com/artifact/com.typesafe.play/play-json
libraryDependencies += "com.typesafe.play" %% "play-json" % "2.8.2"

// https://mvnrepository.com/artifact/org.mongodb.scala/mongo-scala-driver
libraryDependencies += "org.mongodb.scala" %% "mongo-scala-driver" % "4.2.0"

// https://mvnrepository.com/artifact/com.github.nscala-time/nscala-time
libraryDependencies += "com.github.nscala-time" %% "nscala-time" % "2.32.0"

// https://mvnrepository.com/artifact/org.eclipse.paho/org.eclipse.paho.client.mqttv3
libraryDependencies += "org.eclipse.paho" % "org.eclipse.paho.client.mqttv3" % "1.2.5"

// https://mvnrepository.com/artifact/com.opencsv/opencsv
libraryDependencies += "com.opencsv" % "opencsv" % "5.9"

// https://mvnrepository.com/artifact/org.apache.poi/poi-ooxml
libraryDependencies += "org.apache.poi" % "poi-ooxml" % "5.0.0"

// https://mvnrepository.com/artifact/com.typesafe.play/play-mailer
libraryDependencies += "com.typesafe.play" %% "play-mailer" % "8.0.1"
libraryDependencies += "com.typesafe.play" %% "play-mailer-guice" % "8.0.1"

// https://mvnrepository.com/artifact/io.cequence/openai-scala-client
libraryDependencies += "io.cequence" %% "openai-scala-client" % "1.1.0"

mappings in Universal ++=
(baseDirectory.value / "report_template" * "*" get) map
    (x => x -> ("report_template/" + x.getName))

mappings in Universal ++=
(baseDirectory.value / "importEPA" * "*" get) map
    (x => x -> ("importEPA/" + x.getName))

mappings in Universal ++= Seq((baseDirectory.value / "cleanup.bat", "cleanup.bat"))

scalacOptions += "-feature"

routesGenerator := InjectedRoutesGenerator