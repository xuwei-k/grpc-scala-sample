import com.trueaccord.scalapb.{ScalaPbPlugin => PB}

// https://github.com/grpc/grpc-java/blob/v0.13.2/examples/build.gradle#L32
val json = libraryDependencies += "org.glassfish" % "javax.json" % "1.0.4"

val grpcVersion = "0.14.0"

val unusedWarnings = (
  "-Ywarn-unused" ::
  "-Ywarn-unused-import" ::
  Nil
)

lazy val root = project.in(file(".")).aggregate(
  grpcJavaSample, grpcScalaSample
)

val commonSettings: Seq[Def.Setting[_]] = Seq[Def.Setting[_]](
  discoveredMainClasses in Compile ~= {_.sorted}
)

lazy val grpcScalaSample = project.in(file("grpc-scala")).settings(
  commonSettings,
  PB.protobufSettings,
  PB.runProtoc in PB.protobufConfig := { args =>
    com.github.os72.protocjar.Protoc.runProtoc("-v300" +: args.toArray)
  },
  version in PB.protobufConfig := "3.0.0-beta-2",
  libraryDependencies ++= Seq(
    "com.trueaccord.scalapb" %% "scalapb-runtime-grpc" % (PB.scalapbVersion in PB.protobufConfig).value,
    "io.grpc" % "grpc-netty" % grpcVersion //dependency on the netty transport. Use grpc-okhttp for android.
  ),
  json,
  unmanagedResourceDirectories in Compile += (baseDirectory in LocalRootProject).value / "grpc-java/examples/src/main/resources",
  sourceDirectory in PB.protobufConfig := (baseDirectory in LocalRootProject).value / "grpc-java/examples/src/main/proto",
  scalaVersion := "2.11.8",
  scalacOptions ++= (
    "-deprecation" ::
    "-unchecked" ::
    "-Xlint" ::
    "-language:existentials" ::
    "-language:higherKinds" ::
    "-language:implicitConversions" ::
    "-Yno-adapted-args" ::
    Nil
  ) ::: unusedWarnings,
  Seq(Compile, Test).flatMap(c =>
    scalacOptions in (c, console) ~= {_.filterNot(unusedWarnings.toSet)}
  )
)

lazy val grpcJavaSample = project.in(file("grpc-java/examples")).settings(
  commonSettings,
  json,
  //this is a dependency for the grpc aggregation project
  libraryDependencies += "io.grpc" % "grpc-all" % grpcVersion,
  autoScalaLibrary := false,
  unmanagedSourceDirectories in Compile += baseDirectory.value / "src/generated/main/"
)
