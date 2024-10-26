name := """MqttManager"""

version := "1.7.0"

lazy val root = (project in file(".")).enablePlugins(PlayScala, LauncherJarPlugin)

scalaVersion := "2.12.10"

libraryDependencies ++= Seq(
  ws,
  filters,
  guice,
  "com.github.tototoshi" %% "scala-csv" % "1.3.5",
  "net.sf.marineapi" % "marineapi" % "0.10.0"
)

libraryDependencies += "com.typesafe.play" %% "play-json" % "2.6.0"

// https://mvnrepository.com/artifact/org.mongodb.scala/mongo-scala-driver
libraryDependencies += "org.mongodb.scala" %% "mongo-scala-driver" % "4.2.0"

// https://mvnrepository.com/artifact/com.github.nscala-time/nscala-time
libraryDependencies += "com.github.nscala-time" %% "nscala-time" % "2.32.0"

// https://mvnrepository.com/artifact/org.eclipse.paho/org.eclipse.paho.client.mqttv3
libraryDependencies += "org.eclipse.paho" % "org.eclipse.paho.client.mqttv3" % "1.2.5"

// https://mvnrepository.com/artifact/com.opencsv/opencsv
libraryDependencies += "com.opencsv" % "opencsv" % "5.4"

// https://mvnrepository.com/artifact/org.apache.poi/poi-ooxml
libraryDependencies += "org.apache.poi" % "poi-ooxml" % "5.0.0"

// https://mvnrepository.com/artifact/com.typesafe.play/play-mailer
libraryDependencies += "com.typesafe.play" %% "play-mailer" % "7.0.2"
libraryDependencies += "com.typesafe.play" %% "play-mailer-guice" % "7.0.2"

// https://mvnrepository.com/artifact/io.cequence/openai-scala-client
// libraryDependencies += "io.cequence" %% "openai-scala-client" % "1.1.0"

mappings in Universal ++=
(baseDirectory.value / "report_template" * "*" get) map
    (x => x -> ("report_template/" + x.getName))

mappings in Universal ++=
(baseDirectory.value / "importEPA" * "*" get) map
    (x => x -> ("importEPA/" + x.getName))

mappings in Universal ++= Seq((baseDirectory.value / "cleanup.bat", "cleanup.bat"))
 	
//libraryDependencies += "com.google.guava" % "guava" % "19.0"
scalacOptions += "-feature"

routesGenerator := InjectedRoutesGenerator