package com.flashticket.payment_service.dto.common;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class ContractViolationsTest {
  private static final jakarta.validation.ValidatorFactory FACTORY =
      Validation.buildDefaultValidatorFactory();

  record Input(@NotBlank String zeta, @NotBlank String alpha) {}

  @AfterAll
  static void closeFactory() {
    FACTORY.close();
  }

  @Test
  void returnsEmptyWhenContractHasNoViolations() {
    var violations = FACTORY.getValidator().validate(new Input("valid", "valid"));

    assertThat(ContractViolations.firstPath(violations)).isEmpty();
  }

  @Test
  void returnsTheOnlyInvalidField() {
    var violations = FACTORY.getValidator().validate(new Input("", "valid"));

    assertThat(ContractViolations.firstPath(violations)).contains("zeta");
  }

  @Test
  void choosesLexicographicPathRegardlessOfInputOrderWithoutMutatingViolations() {
    var original = new ArrayList<>(FACTORY.getValidator().validate(new Input("", "")));
    var forward = new LinkedHashSet<>(original);
    var reversed = new ArrayList<>(original);
    Collections.reverse(reversed);
    var backward = new LinkedHashSet<>(reversed);

    assertThat(ContractViolations.firstPath(forward)).contains("alpha");
    assertThat(ContractViolations.firstPath(backward)).contains("alpha");
    assertThat(forward).containsExactlyElementsOf(original);
    assertThat(backward).containsExactlyElementsOf(reversed);
  }
}
