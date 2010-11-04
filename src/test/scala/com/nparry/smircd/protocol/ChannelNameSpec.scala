package com.nparry.smircd.protocol

import org.specs.Specification

class ChannelNameSpec extends Specification {

  var f = { e: IllegalArgumentException => e.getMessage() }

  "ChannelName" should {

    "acceptValidNames" in {
      ChannelName.single("#foobar", f) mustEqual Right(ChannelName("#foobar"))
      ChannelName.single("&foobar", f) mustEqual Right(ChannelName("&foobar"))
    }

    "acceptValidNamesInLists" in {
      ChannelName.list("#foobar,&blah", f).right.get.size mustEqual 2
      ChannelName.list("&foobar,#blah", f).right.get.size mustEqual 2
    }

    "rejectInvalidNames" in {
      ChannelName.single("", f).left.get mustEqual ""
      ChannelName.single("blah", f).left.get mustEqual "blah"
      ChannelName.single("blah blah", f).left.get mustEqual "blah blah"
      ChannelName.single("blah,blah", f).left.get mustEqual "blah,blah"
    }

    "handlesMixedValidity" in {
      ChannelName.listOfValid("#good,bad", { x => x}).size mustEqual 2
      ChannelName.listOfValid("#good,bad", { x => x})(0) mustEqual Right(ChannelName("#good"))
      ChannelName.listOfValid("#good,bad", { x => x})(1) mustEqual Left("bad")
    }
  }

}

