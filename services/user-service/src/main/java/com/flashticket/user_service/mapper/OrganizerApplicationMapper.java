package com.flashticket.user_service.mapper;

import com.flashticket.user_service.document.OrganizerApplicationDocument;
import com.flashticket.user_service.dto.response.OrganizerApplicationView;
import org.mapstruct.*;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OrganizerApplicationMapper {
  @Mapping(target = "applicationId", source = "id")
  OrganizerApplicationView toView(OrganizerApplicationDocument application);
}
