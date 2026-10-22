package com.flashticket.payment_service.dto;

import static org.assertj.core.api.Assertions.*;
import com.flashticket.payment_service.dto.common.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.dto.message.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.node.ObjectNode;

class ContractValidationTest {
  @ParameterizedTest
  @ValueSource(strings={"-1","1.5","1e2","+1","01","10000000000000000000"," 1",""})
  void rejectsNonCanonicalMoney(String money) throws Exception {
    var tree=(ObjectNode)ContractCodecTest.CODEC.toTree(ContractCodecTest.CODEC.read(ContractCodecTest.purchaseJson(),PurchaseSnapshot.class));
    tree.put("total",money);
    assertThatThrownBy(()->ContractCodecTest.CODEC.fromTree(tree,PurchaseSnapshot.class)).isInstanceOf(ContractValidationException.class);
  }
  @Test void rejectsWrongTokensMissingNullableAndNestedUnknownFields() throws Exception {
    String json=ContractCodecTest.purchaseJson();
    for(String bad: new String[]{json.replace("\"total\":\"9999999999999999999\"","\"total\":100"),json.replace("\"venueName\":null,",""),json.replace("\"quantity\":1","\"quantity\":1.5"),json.replace("\"seats\":[]","\"extra\":1,\"seats\":[]"),json.replace("9223372036854775807","9223372036854775808"),json.replace("2030-01-01T01:00:00Z","2030-01-01T08:00:00+07:00"),json.replace("\"buyerSubject\":\"buyer-1\"","\"buyerSubject\":null")}) {
      assertThatThrownBy(()->ContractCodecTest.CODEC.read(bad,PurchaseSnapshot.class)).isInstanceOf(ContractValidationException.class);
    }
  }
  @Test void rejectsInconsistentStartResults() {
    String bad="""
    {"requestId":"10000000-0000-0000-0000-000000000005","orderId":"10000000-0000-0000-0000-000000000001","state":"CREATED","attemptId":null,"failureCode":null}
    """;
    assertThatThrownBy(()->ContractCodecTest.CODEC.read(bad,StartPaymentResult.class)).isInstanceOf(ContractValidationException.class);
    assertThatThrownBy(()->ContractCodecTest.CODEC.read(bad.replace("CREATED","REJECTED"),StartPaymentResult.class)).isInstanceOf(ContractValidationException.class);
  }
  @Test void boundsConfigurationCountersToPostgresBigintWhileAllowingZero() {
    String json="""
    {"sectorId":"10000000-0000-0000-0000-000000000003","kind":"STANDING","configuredCapacity":"0"}
    """;
    assertThat(ContractCodecTest.CODEC.read(json,SectorConfig.class).configuredCapacity()).isEqualTo("0");
    assertThatThrownBy(()->ContractCodecTest.CODEC.read(json.replace("\"0\"","\"9223372036854775808\""),SectorConfig.class)).isInstanceOf(ContractValidationException.class);
  }
}
