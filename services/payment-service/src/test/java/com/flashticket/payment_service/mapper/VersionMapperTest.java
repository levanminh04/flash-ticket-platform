package com.flashticket.payment_service.mapper;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class VersionMapperTest {
  @Test
  void keepsVersionInPostgresBigintDomain() {
    var mapper = new VersionMapper();
    assertThat(mapper.toLong("9223372036854775807")).isEqualTo(Long.MAX_VALUE);
    assertThat(mapper.toWire(1L)).isEqualTo("1");
    for (String bad : new String[] {"0", "-1", "01", "1.5", "9223372036854775808", null})
      assertThatThrownBy(() -> mapper.toLong(bad)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> mapper.toWire(0L)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> mapper.toWire(null)).isInstanceOf(IllegalArgumentException.class);
  }
}
