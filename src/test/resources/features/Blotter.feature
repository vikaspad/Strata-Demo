Feature: Trade blotter

  Scenario: A trader exports the blotter and opens another one
    Given I am signed in as "jdoe"
    When I filter the blotter by counterparty "Halvorsen"
    And I export the trades
    Then the export dialog is shown
    When I open blotter "99001"
    Then I see the heading "Blotter 99001"
    When I allocate the trade
