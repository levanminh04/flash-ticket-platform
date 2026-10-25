package com.flashticket.user_service.mapper;

import com.flashticket.user_service.document.UserDocument;
import com.flashticket.user_service.dto.response.MeView;
import org.mapstruct.*;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserMapper {

  MeView toView(UserDocument user);
}
