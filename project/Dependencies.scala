import sbt._

object Dependencies {

  object V {
    val Spire              = "0.14.1"
    val Shapeless          = "2.3.2"
    val Discipline         = "0.7.3"

    // Test libraries
    val ScalaTest          = "3.0.2"
    val ScalaCheck         = "1.13.4"
  }

  // Compile
  object Compile {
    val spire             = "org.typelevel"           %% "spire"                   % V.Spire
    val shapeless         = "com.chuusai"              %% "shapeless"               % V.Shapeless
  }

  // Test
  object Test {
    val scalaTest       =   "org.scalatest"         %% "scalatest"                   % V.ScalaTest     % "test"
    val scalaCheck      =   "org.scalacheck"        %% "scalacheck"                  % V.ScalaCheck    % "test"
    val spireLaws       =   "org.typelevel"        %% "spire-laws"                  % V.Spire         % "test"
    val discipline      =   "org.typelevel"         %% "discipline"                  % V.Discipline    % "test"
  }
}
