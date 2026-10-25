package com.flashticket.user_service.mapper;

import com.flashticket.user_service.document.OrganizerProfileDocument;
import com.flashticket.user_service.dto.response.OrganizerPublicView;
import org.mapstruct.*;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OrganizerProfileMapper {
  @Mapping(target = "organizerSubject", source = "profile.identitySubject")
  @Mapping(target = "followerCount", expression = "java(followerCount)")
  OrganizerPublicView toPublicView(OrganizerProfileDocument profile, @Context long followerCount);
}
