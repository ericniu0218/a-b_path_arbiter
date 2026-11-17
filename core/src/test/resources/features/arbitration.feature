Feature: A/B line arbitration
  The same sequenced stream arrives over a fast wireless line (A) and a slower fiber line (B).
  The arbiter releases each message once, from whichever line delivers it first, and uses the
  other line to cover losses.

  Background:
    Given an arbiter with a gap timeout of 5 ms

  Scenario: The fast line wins when both lines are healthy
    When line A delivers messages 1 to 200 at 4.0 ms
    And line B delivers messages 1 to 200 at 6.5 ms
    Then the output contains messages 1 to 200 with no gap
    And line A won messages 1 to 200
    And each message was released exactly once

  Scenario: The fiber line fills a hole left by the wireless line
    When line A delivers messages 1 to 99 at 4.0 ms
    And line A delivers messages 121 to 200 at 4.1 ms
    And line B delivers messages 1 to 99 at 6.4 ms
    And line B delivers messages 100 to 120 at 6.5 ms
    And line B delivers messages 121 to 200 at 6.6 ms
    Then the output contains messages 1 to 200 with no gap
    And line A won messages 1 to 99
    And line B won messages 100 to 120
    And line A won messages 121 to 200
    And message 121 was released at 6.5 ms

  Scenario: A loss on both lines is reported as a gap after the timeout
    When line A delivers messages 1 to 50 at 4.0 ms
    And line A delivers messages 61 to 100 at 4.1 ms
    And line B delivers messages 1 to 50 at 6.5 ms
    And line B delivers messages 61 to 100 at 6.6 ms
    And time advances to 20 ms
    Then a gap is reported for messages 51 to 60 at 9.1 ms
    And the output contains 90 messages
    And each message was released exactly once

  Scenario: A gap timeout shorter than the path difference defeats the protection
    Given an arbiter with a gap timeout of 1 ms
    When line A delivers messages 1 to 99 at 4.0 ms
    And line A delivers messages 121 to 200 at 4.1 ms
    And line B delivers messages 1 to 200 at 6.5 ms
    Then a gap is reported for messages 100 to 120 at 5.1 ms
    And the output contains 179 messages
