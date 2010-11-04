package com.nparry.smircd.protocol

import org.specs.Specification

class CommandParserSpec extends Specification {

  "CommandParser" should {
    "handleSingleToken" in {
      CommandParser.splitToken("blah") mustEqual (Right("blah"), None)
      CommandParser.splitToken("blah ") mustEqual (Right("blah"), None)
      CommandParser.splitToken("blah      ") mustEqual (Right("blah"), None)
    }

    "tokenizeOnSpace" in {
      CommandParser.splitToken("blah blah blah") mustEqual (Right("blah"), Some("blah blah"))
      CommandParser.splitToken("blah blah blah") mustEqual (Right("blah"), Some("blah blah"))
      CommandParser.splitToken("blah      blah blah") mustEqual (Right("blah"), Some("blah blah"))
    }

    "tokenizeEmptyInput" in {
      CommandParser.splitToken("") mustEqual (Right(""), None)
    }

    "rejectTokenizingBogusInput" in {
      CommandParser.splitToken(" ") mustEqual (Left(" "), None)
      CommandParser.splitToken(" blah") mustEqual (Left(" blah"), None)
    }

    "rejectParsingEmptyInput" in {
      badParse("") must include("Empty")
    }

    "rejectParsingBogusInput" in {
      badParse("    ") must include("Unable")
      badParse(" QUIT") must include("Unable")
      badParse(" :Foo QUIT") must include("Unable")
    }

    "handleEmptyPrefix" in {
      prefixFor("PASS secretpasswordhere") mustEqual None
      prefixFor("PRIVMSG Wiz :Hello are you receiving this message ?") mustEqual None
      prefixFor("QUIT") mustEqual None
    }

    "extractValidPrefix" in {
      prefixFor(":Angel PRIVMSG Wiz :Hello are you receiving this message ?") mustEqual Some("Angel")
      prefixFor(":Wiz QUIT") mustEqual Some("Wiz")
    }

    "rejectPrefixOnlyInput" in {
      badParse(":Boom") must include("Boom")
    }

    "rejectInvalidPrefix" in {
      badParse(":") must include("Invalid prefix")
      badParse(": QUIT") must include("Invalid prefix")
    }

    "extractValidCommand" in {
      commandFor("PASS secretpasswordhere") mustEqual "PASS"
      commandFor("PRIVMSG Wiz :Hello are you receiving this message ?") mustEqual "PRIVMSG"
      commandFor("QUIT") mustEqual "QUIT"
      commandFor("QUIT     ") mustEqual "QUIT"
      commandFor(":Angel PRIVMSG Wiz :Hello are you receiving this message ?") mustEqual "PRIVMSG"
      commandFor(":Wiz QUIT") mustEqual "QUIT"
    }

    "rejectInvalidCommand" in {
      badParse("PASS123") must include("Invalid command")
      badParse(":Wiz PASS123") must include("Invalid command")
      badParse("PASS123 pw") must include("Invalid command")
      badParse(":Wiz PASS123 pw") must include("Invalid command")

      badParse("01A") must include("Invalid command")
      badParse(":Wiz 01A") must include("Invalid command")
      badParse("01A pw") must include("Invalid command")
      badParse(":Wiz 01A pw") must include("Invalid command")

      badParse("12") must include("Invalid command")
      badParse(":Wiz 12") must include("Invalid command")
      badParse("12 blah") must include("Invalid command")
      badParse(":Wiz 12 blah") must include("Invalid command")

      badParse("1234") must include("Invalid command")
      badParse(":Wiz 1234") must include("Invalid command")
      badParse("1234 blah") must include("Invalid command")
      badParse(":Wiz 1234 blah") must include("Invalid command")
    }

    "handleEmptyParams" in {
      paramsFor("ERROR") mustEqual List()
      paramsFor(":Wiz ERROR") mustEqual List()
      paramsFor(":Wiz ERROR      ") mustEqual List()
    }

    "handleSingleParam" in {
      paramsFor("ERROR Blah") mustEqual List("Blah")
      paramsFor(":Wiz ERROR Blah") mustEqual List("Blah")
      paramsFor(":Wiz ERROR      Blah") mustEqual List("Blah")
      paramsFor(":Wiz ERROR      Blah   ") mustEqual List("Blah")
    }

    "handleSingleMultiTokenParam" in {
      paramsFor("ERROR :Blah") mustEqual List("Blah")
      paramsFor(":Wiz ERROR :Blah") mustEqual List("Blah")
      paramsFor(":Wiz ERROR      :Blah") mustEqual List("Blah")
      paramsFor(":Wiz ERROR      :Blah   ") mustEqual List("Blah   ")

      paramsFor("ERROR :Blah blah") mustEqual List("Blah blah")
      paramsFor(":Wiz ERROR :Blah blah") mustEqual List("Blah blah")
      paramsFor(":Wiz ERROR      :Blah blah   ") mustEqual List("Blah blah   ")
    }

    "handleMultipleParams" in {
      paramsFor("ERROR Blah blah") mustEqual List("Blah", "blah")
      paramsFor(":Wiz ERROR Blah blah") mustEqual List("Blah", "blah")
      paramsFor(":Wiz ERROR      Blah blah") mustEqual List("Blah", "blah")
      paramsFor(":Wiz ERROR      Blah blah   ") mustEqual List("Blah", "blah")
    }

    "handleMultipleParamsWithMultiToken" in {
      paramsFor("ERROR Blah blah :Blah blah") mustEqual List("Blah", "blah", "Blah blah")
      paramsFor(":Wiz ERROR Blah blah :Blah blah") mustEqual List("Blah", "blah", "Blah blah")
      paramsFor(":Wiz ERROR      Blah blah :Blah blah") mustEqual List("Blah", "blah", "Blah blah")
      paramsFor(":Wiz ERROR      Blah blah   :Blah blah   ") mustEqual List("Blah", "blah", "Blah blah   ")
    }

    "handlesEmptyMultiTokenParam" in {
      paramsFor("ERROR :") mustEqual(List(""))
      paramsFor(":Wiz ERROR :") mustEqual(List(""))
      paramsFor(":Wiz ERROR     :") mustEqual(List(""))

      paramsFor("ERROR Blah :") mustEqual(List("Blah", ""))
      paramsFor(":Wiz ERROR Blah :") mustEqual(List("Blah", ""))
      paramsFor(":Wiz ERROR    Blah :") mustEqual(List("Blah", ""))
    }

    "handlesMultiTokenParamWithEmbeddedColon" in {
      paramsFor("Error :Msg is: blah") mustEqual(List("Msg is: blah"))
    }
  }

  def prefixFor(s: String) = {
    goodParse(s).prefix
  }

  def commandFor(s: String) = {
    goodParse(s).command
  }

  def paramsFor(s: String) = {
      goodParse(s).params
  }

  def goodParse(s: String): ParsedCommand = {
    CommandParser.parse(s).right.get
  }

  def badParse(s: String): String = {
    CommandParser.parse(s).left.get
  }
}

