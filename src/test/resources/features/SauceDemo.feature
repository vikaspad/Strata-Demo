@public
Feature: Sauce Demo

  Scenario: A user logs in and opens the About page
    Given I open the Sauce Demo site
    When I log in with username "standard_user" and password "secret_sauce"
    Then I see the products page
    When I click About in the left menu
    Then I am taken to the Sauce Labs website
