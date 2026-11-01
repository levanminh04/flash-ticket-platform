package com.flashticket.ticket_service.entity;

import static com.flashticket.ticket_service.support.TicketFixtures.*;
import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EntityBehaviorTests {
  @Test
  void addTicketLinksBothSidesWithoutAssigningBusinessValues() {
    var issuance = new Issuance();
    issuance.setEventId(EVENT);
    var ticket = new Ticket();
    issuance.addTicket(ticket);
    assertThat(issuance.getTickets()).containsExactly(ticket);
    assertThat(ticket.getIssuance()).isSameAs(issuance);
    assertThat(ticket.getEventId()).isEqualTo(EVENT);
    assertThat(ticket.getState()).isNull();
    assertThat(ticket.getQrTokenCiphertext()).isNull();
    assertThat(ticket.getId()).isNull();
    assertThatThrownBy(() -> issuance.addTicket(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void inboxKeyIncludesConsumerName() {
    var key = new InboxMessageId("ticket-issue", MESSAGE);
    assertThat(key).isEqualTo(new InboxMessageId("ticket-issue", MESSAGE));
    assertThat(key.hashCode()).isEqualTo(new InboxMessageId("ticket-issue", MESSAGE).hashCode());
    assertThat(key).isNotEqualTo(new InboxMessageId("ticket-cancel", MESSAGE));
  }
}
