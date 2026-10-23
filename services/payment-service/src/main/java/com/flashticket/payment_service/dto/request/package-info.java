/**
 * HTTP request DTOs belong here when an endpoint needs a structured input. Current Payment reads
 * use orderId path and optional requestId query parameters. StartPayment is an inter-service
 * command in dto.message, not a public POST body.
 */
package com.flashticket.payment_service.dto.request;
