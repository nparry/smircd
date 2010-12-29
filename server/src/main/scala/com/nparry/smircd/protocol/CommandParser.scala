package com.nparry.smircd.protocol

import scala.annotation.tailrec
import scala.util.matching.Regex

object CommandParser {

  def parse(s: String): Either[String, ParsedCommand] = {
    val (first, rest) = splitToken(s)
    first match {
      case Left(_)    => Left(errorMsg("Unable to parse prefix", s))
      case Right("")  => Left(errorMsg("Empty input to parser", s))
      case Right(":") => Left(errorMsg("Invalid prefix", s))
      case Right(f)   => if (f.startsWith(":"))
        rest match {
          case Some(r) => parse(Some(f.substring(1)), r)
          case None    => Left(errorMsg("Invalid bare prefix", s))
        }
        else parse(None, s)
    }
  }

  private def parse(prefix: Option[String], s: String): Either[String, ParsedCommand] = {
    val (first, rest) = splitToken(s)
    first match {
      case Left(_)   => Left(errorMsg("Unable to parse command", s))
      case Right("") => Left(errorMsg("Empty command", s))
      case Right(f)  => if (isValidCommand(f))
        parse(prefix, f, rest)
        else Left(errorMsg("Invalid command", s))
    }
  }

  private def parse(prefix: Option[String], command: String, s: Option[String]): Either[String, ParsedCommand] = {
    Right(ParsedCommand(prefix, command, parseParams(List(), s)))
  }

  @tailrec private def parseParams(accum: List[String], params: Option[String]): List[String] = {
    params match {
      case None => accum.reverse
      case Some(s) => if (s.startsWith(":"))
        (s.substring(1) :: accum).reverse
        else {
          val (first, rest) = splitToken(s)
          first match {
            case Left(_) => throw new IllegalStateException("Internal error in parseParams for " + s)
            case Right(f) => parseParams(f :: accum, rest)
          }
        }
    }
  }

  val StdCmdRE = new Regex("""[a-zA-Z]+""")
  val NumericCmdRE = new Regex("""\d\d\d""")
  def isValidCommand(s: String): Boolean = {
    s match {
      case StdCmdRE() => true
      case NumericCmdRE() => true
      case _ => false
    }
  }

  private def errorMsg(m: String, s: String) = {
    m + "(" + s + ")"
  }

  private val RemainingRE = new Regex("""\s+(\S.*)""")
  def splitToken(s: String): (Either[String, String], Option[String]) = {
    s.indexOf(" ") match {
      case 0  =>  (Left(s), None)
      case -1 => (Right(s), None)
      case x  => {
        def first = s.substring(0, x)
        s.substring(x) match {
          case RemainingRE(rest) => (Right(first), Some(rest))
          case _ => (Right(first), None)
        }
      }
    }
  }

}

