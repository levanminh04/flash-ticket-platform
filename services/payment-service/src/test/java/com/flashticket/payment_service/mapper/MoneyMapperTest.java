package com.flashticket.payment_service.mapper;
import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyMapperTest {
  private final MoneyMapper mapper=new MoneyMapper();
  @Test void convertsExactlyWithoutScientificNotation(){
    assertThat(mapper.toDecimal("9999999999999999999")).isEqualByComparingTo("9999999999999999999");
    assertThat(mapper.toWire(new BigDecimal("100.00"))).isEqualTo("100");
    assertThat(mapper.toWire(new BigDecimal("1E+3"))).isEqualTo("1000");
    assertThat(mapper.toWire(BigDecimal.ZERO)).isEqualTo("0");
  }
  @ParameterizedTest @ValueSource(strings={"-1","1.1","1e2","01","+1","10000000000000000000",""})
  void rejectsNonCanonicalInput(String value){assertThatThrownBy(()->mapper.toDecimal(value)).isInstanceOf(IllegalArgumentException.class);}
  @Test void refusesRoundingAndOverflow(){
    for(var value:new BigDecimal[]{new BigDecimal("1.1"),BigDecimal.valueOf(-1),new BigDecimal("10000000000000000000")})assertThatThrownBy(()->mapper.toWire(value)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(()->mapper.toDecimal(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(()->mapper.toWire(null)).isInstanceOf(IllegalArgumentException.class);
  }
}
