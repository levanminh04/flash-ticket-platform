package com.flashticket.event_service;

import static org.assertj.core.api.Assertions.*;

import com.flashticket.event_service.domain.ContractRules;
import com.flashticket.event_service.web.EventProblem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ContractRulesTests {
  @ParameterizedTest
  @ValueSource(strings = {"1.0", "1e3", "+1", "01", "-1", "NaN", "10000000000000000000", "0"})
  void priceMustBeAPositiveCanonicalVndString(String input) {
    assertThatThrownBy(() -> ContractRules.money(input, "price")).isInstanceOf(EventProblem.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"-1", "01", "1.0", "9223372036854775808"})
  void countersMustFitPostgresBigintWithoutCoercion(String input) {
    assertThatThrownBy(() -> ContractRules.unsigned(input, "quantity", true))
        .isInstanceOf(EventProblem.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"2027-01-01T07:00:00+07:00", "not-a-date"})
  void inputInstantsMustUseUtcZ(String input) {
    assertThatThrownBy(() -> ContractRules.instant(input, "startsAt"))
        .isInstanceOf(EventProblem.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"1.01", "-0.1", "0.123456789", "NaN", "1e-2"})
  void feeRateIsAnExactFractionWithAtMostEightDecimals(String input) {
    assertThatThrownBy(() -> ContractRules.fee(input)).isInstanceOf(EventProblem.class);
  }

  @Test
  void validBoundariesAndPromotionNormalization() {
    assertThat(ContractRules.money("9999999999999999999", "price").toPlainString())
        .isEqualTo("9999999999999999999");
    assertThat(ContractRules.unsigned("0", "quantity", true)).isZero();
    assertThat(ContractRules.unsigned("9223372036854775807", "quantity", false))
        .isEqualTo(Long.MAX_VALUE);
    assertThat(ContractRules.code("  summer10  ")).isEqualTo("SUMMER10");
    assertThat(ContractRules.fee("0.125")).isEqualByComparingTo("0.125");
  }
}
