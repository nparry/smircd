package com.nparry.smircd.protocol

import org.specs2.mutable._

class ParsedCommandSpec extends Specification {

  "ParsedCommand" should {
    "produceProperRawFormWithoutPrefix" in {
      ParsedCommand(None, "FOO", List()).raw mustEqual "FOO"
    }

    "produceProperRawFormWithPrefix" in {
      ParsedCommand(Some("PFIX"), "FOO", List()).raw mustEqual ":PFIX FOO"
    }

    "produceProperRawFormForOneParam" in {
      ParsedCommand(None, "FOO", List("P1")).raw mustEqual "FOO P1"
      ParsedCommand(None, "FOO", List("P1 with spaces")).raw mustEqual "FOO :P1 with spaces"
      ParsedCommand(None, "FOO", List(" ")).raw mustEqual "FOO : "
      ParsedCommand(None, "FOO", List("")).raw mustEqual "FOO :"
    }

    "produceProperRawFormForMultipleParam" in {
      ParsedCommand(None, "FOO", List("P1", "P2")).raw mustEqual "FOO P1 P2"
      ParsedCommand(None, "FOO", List("P1", "P2 with spaces")).raw mustEqual "FOO P1 :P2 with spaces"
      ParsedCommand(None, "FOO", List("P1", " ")).raw mustEqual "FOO P1 : "
      ParsedCommand(None, "FOO", List("P1", "")).raw mustEqual "FOO P1 :"
    }
  }

}

