package com.flashticket.payment_service.dto.common;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.dto.message.*;
import com.flashticket.payment_service.dto.payment.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.entity.enums.*;
import jakarta.validation.constraints.*;
import java.util.*;

public record Violation(@NotBlank String field, @NotBlank String code) {}
