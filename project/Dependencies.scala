import sbt._

object Dependencies {

  object V {
    val Play_22            = "2.2.3"
    val Play_23            = "2.3.5"

    val Spire              = "0.8.2"
    val Shapeless          = "2.3.0"
    val Discipline         = "0.2.1"

    // Test libraries
    val Specs2             = "2.4.2"
    val ScalaCheck         = "1.11.6"
  }

  // Compile
  object Compile {
    val playJson_22       = "com.typesafe.play"        %% "play-json"               % V.Play_22 % "provided"
    val playJson_23       = "com.typesafe.play"        %% "play-json"               % V.Play_23 % "provided"

    val spire             = "org.spire-math"           %% "spire"                   % V.Spire
    val shapeless         = "com.chuusai"              %% "shapeless"               % V.Shapeless
  }

  // Test
  object Test {
    val specs2          =   "org.specs2"            %% "specs2"                      % V.Specs2        % "test"
    val scalaCheck      =   "org.scalacheck"        %% "scalacheck"                  % V.ScalaCheck    % "test"
    val spireLaws       =   "org.spire-math"        %% "spire-scalacheck-binding"    % V.Spire         % "test"
    val discipline      =   "org.typelevel"         %% "discipline"                  % V.Discipline    % "test"
  }
}
